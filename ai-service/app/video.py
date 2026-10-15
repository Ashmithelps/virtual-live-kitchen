"""Fetching a clip (from allowed hosts only) and sampling frames from it."""

from __future__ import annotations

import tempfile
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlsplit

import cv2
import httpx
import numpy as np


class VideoError(Exception):
    """The request can't be served; ``status`` is the HTTP status to answer with."""

    def __init__(self, status: int, message: str):
        super().__init__(message)
        self.status = status


def check_url(url: str, allowed_hosts: frozenset[str]) -> None:
    parts = urlsplit(url)
    if parts.scheme not in ("http", "https") or not parts.hostname:
        raise VideoError(400, "videoUrl must be an http(s) URL")
    port = parts.port or (443 if parts.scheme == "https" else 80)
    if f"{parts.hostname.lower()}:{port}" not in allowed_hosts and parts.hostname.lower() not in allowed_hosts:
        raise VideoError(400, f"Videos are only fetched from configured storage hosts, not {parts.hostname}:{port}")


def download(url: str, allowed_hosts: frozenset[str], max_bytes: int, timeout_s: float, directory: Path) -> Path:
    """Streams the video to a temp file, refusing anything over ``max_bytes``. Redirects are not followed."""
    check_url(url, allowed_hosts)
    target = Path(tempfile.mkstemp(suffix=".mp4", dir=directory)[1])
    try:
        with httpx.stream("GET", url, timeout=timeout_s, follow_redirects=False) as response:
            if response.status_code != 200:
                raise VideoError(502, f"Fetching the video failed (HTTP {response.status_code})")
            size = 0
            with target.open("wb") as out:
                for chunk in response.iter_bytes(1 << 16):
                    size += len(chunk)
                    if size > max_bytes:
                        raise VideoError(413, f"The video is larger than {max_bytes // (1 << 20)} MB")
                    out.write(chunk)
    except httpx.HTTPError as e:
        raise VideoError(502, f"Fetching the video failed: {e.__class__.__name__}") from e
    return target


@dataclass(frozen=True)
class Sampled:
    duration_s: float
    timestamps: list[float]
    frames: list[np.ndarray]


def sample_frames(path: Path, fps: float, max_frames: int) -> Sampled:
    """One frame every 1/fps seconds (the first frame at or after each mark), up to ``max_frames``."""
    capture = cv2.VideoCapture(str(path))
    if not capture.isOpened():
        raise VideoError(422, "The file isn't a readable video")
    try:
        native_fps = capture.get(cv2.CAP_PROP_FPS) or 0.0
        count = capture.get(cv2.CAP_PROP_FRAME_COUNT) or 0.0
        timestamps: list[float] = []
        frames: list[np.ndarray] = []
        step = 1.0 / fps
        next_mark = 0.0
        index = 0
        last_t = 0.0
        while len(frames) < max_frames:
            ok = capture.grab()
            if not ok:
                break
            # Position from the frame index when the container reports a rate; it's more reliable than POS_MSEC.
            t = index / native_fps if native_fps > 0 else capture.get(cv2.CAP_PROP_POS_MSEC) / 1000.0
            index += 1
            last_t = t
            if t + 1e-6 >= next_mark:
                ok, frame = capture.retrieve()
                if ok:
                    timestamps.append(round(t, 2))
                    frames.append(frame)
                next_mark += step
                while next_mark <= t:
                    next_mark += step
        if index == 0:
            raise VideoError(422, "The video has no frames")
        duration = count / native_fps if native_fps > 0 and count > 0 else last_t
        return Sampled(round(duration, 2), timestamps, frames)
    finally:
        capture.release()
