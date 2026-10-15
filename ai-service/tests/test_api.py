from fastapi.testclient import TestClient

from app.config import Settings
from app.detector import Detection
from app.main import create_app


class FakeDetector:
    """Frame 0: no one. Then a chef with gloves, then without, alternating; never a mask decision."""

    version = "fake@1"

    def detect(self, frames):
        out = []
        for i, _ in enumerate(frames):
            if i == 0:
                out.append([])
            else:
                hands = "gloves" if i % 2 else "no_gloves"
                out.append([Detection("person", 0.9, (0.1, 0.1, 0.9, 0.9)), Detection(hands, 0.8, (0.2, 0.6, 0.3, 0.7)),
                            Detection("head_cover", 0.7, (0.4, 0.1, 0.6, 0.2))])
        return out


def client(file_server: str, key: str = "secret", detector=FakeDetector()) -> TestClient:
    settings = Settings(_env_file=None, ai_service_api_key=key, allowed_video_hosts=file_server)
    return TestClient(create_app(settings, detector))


def test_analyze_returns_frames_and_summary(file_server: str):
    with client(file_server) as c:
        r = c.post("/analyze", json={"videoUrl": f"http://{file_server}/clip.mp4"}, headers={"X-Api-Key": "secret"})
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["modelVersion"] == "fake@1"
    assert body["durationSeconds"] == 5.0
    assert [f["t"] for f in body["frames"]] == [0.0, 1.0, 2.0, 3.0, 4.0]
    assert body["frames"][0]["detections"] == []
    s = body["summary"]
    assert (s["sampledFrames"], s["personFrames"]) == (5, 4)
    assert s["items"]["gloves"] == {"compliancePct": 50.0, "compliantFrames": 2, "violationFrames": 2, "notEvaluatedFrames": 0,
                                    "violationExamples": [2.0, 4.0]}
    assert s["items"]["headCover"]["compliancePct"] == 100.0
    assert s["items"]["mask"]["compliancePct"] is None


def test_api_key_is_required_when_configured(file_server: str):
    with client(file_server) as c:
        url = {"videoUrl": f"http://{file_server}/clip.mp4"}
        assert c.post("/analyze", json=url).status_code == 401
        assert c.post("/analyze", json=url, headers={"X-Api-Key": "wrong"}).status_code == 401


def test_errors_are_clear_status_codes(file_server: str):
    with client(file_server) as c:
        h = {"X-Api-Key": "secret"}
        assert c.post("/analyze", json={"videoUrl": "http://elsewhere:9000/clip.mp4"}, headers=h).status_code == 400
        assert c.post("/analyze", json={"videoUrl": f"http://{file_server}/not-a-video.mp4"}, headers=h).status_code == 422
        assert c.post("/analyze", json={"videoUrl": f"http://{file_server}/missing.mp4"}, headers=h).status_code == 502
        assert c.post("/analyze", json={}, headers=h).status_code == 422


def test_without_a_model_analysis_is_unavailable(file_server: str, tmp_path, monkeypatch):
    settings = Settings(_env_file=None, ai_service_api_key="", allowed_video_hosts=file_server, hygiene_model_path=str(tmp_path / "none.pt"))
    with TestClient(create_app(settings)) as c:
        assert c.get("/health").json() == {"status": "no_model", "modelVersion": None}
        assert c.post("/analyze", json={"videoUrl": f"http://{file_server}/clip.mp4"}).status_code == 503
