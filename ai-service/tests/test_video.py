from pathlib import Path

import pytest

from app.video import VideoError, check_url, download, sample_frames
from tests.conftest import write_video

ALLOWED = frozenset({"minio:9000", "localhost:9000"})


def test_one_frame_per_second(video_dir: Path):
    sampled = sample_frames(video_dir / "clip.mp4", fps=1.0, max_frames=100)
    assert sampled.timestamps == [0.0, 1.0, 2.0, 3.0, 4.0]
    assert len(sampled.frames) == 5
    assert sampled.duration_s == 5.0


def test_sampling_rate_and_cap(tmp_path: Path):
    video = write_video(tmp_path / "long.mp4", seconds=3.0, fps=25)
    assert sample_frames(video, fps=2.0, max_frames=100).timestamps == [0.0, 0.52, 1.0, 1.52, 2.0, 2.52]
    assert len(sample_frames(video, fps=1.0, max_frames=2).frames) == 2


def test_unreadable_files_are_422(video_dir: Path):
    with pytest.raises(VideoError) as e:
        sample_frames(video_dir / "not-a-video.mp4", fps=1.0, max_frames=10)
    assert e.value.status == 422


@pytest.mark.parametrize("url", [
    "http://evil.example/clip.mp4",
    "http://minio:9001/clip.mp4",
    "file:///etc/passwd",
    "ftp://minio:9000/x",
    "http://169.254.169.254/latest/meta-data",
])
def test_only_configured_storage_hosts_are_fetched(url: str):
    with pytest.raises(VideoError) as e:
        check_url(url, ALLOWED)
    assert e.value.status == 400


def test_download_respects_the_size_limit(file_server: str, tmp_path: Path):
    allowed = frozenset({file_server})
    ok = download(f"http://{file_server}/clip.mp4", allowed, 10 << 20, 10, tmp_path)
    assert ok.stat().st_size > 0
    with pytest.raises(VideoError) as e:
        download(f"http://{file_server}/clip.mp4", allowed, 100, 10, tmp_path)
    assert e.value.status == 413
    with pytest.raises(VideoError) as e:
        download(f"http://{file_server}/missing.mp4", allowed, 10 << 20, 10, tmp_path)
    assert e.value.status == 502
