import functools
import http.server
import threading
from pathlib import Path

import cv2
import numpy as np
import pytest


def write_video(path: Path, seconds: float, fps: int = 10, size=(64, 48)) -> Path:
    writer = cv2.VideoWriter(str(path), cv2.VideoWriter_fourcc(*"mp4v"), fps, size)
    for i in range(int(seconds * fps)):
        frame = np.full((size[1], size[0], 3), (i * 5) % 255, dtype=np.uint8)
        writer.write(frame)
    writer.release()
    return path


@pytest.fixture
def video_dir(tmp_path: Path) -> Path:
    write_video(tmp_path / "clip.mp4", seconds=5.0)
    (tmp_path / "not-a-video.mp4").write_bytes(b"this is not a video at all")
    return tmp_path


@pytest.fixture
def file_server(video_dir: Path):
    """Serves video_dir at http://127.0.0.1:<port>/ like the S3 endpoint would."""
    handler = functools.partial(http.server.SimpleHTTPRequestHandler, directory=str(video_dir))
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    yield f"127.0.0.1:{server.server_address[1]}"
    server.shutdown()
