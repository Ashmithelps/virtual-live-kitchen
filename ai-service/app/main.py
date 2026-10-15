"""Hygiene analysis service: POST /analyze {videoUrl} → per-frame detections and a compliance summary."""

from __future__ import annotations

import logging
import secrets
import shutil
import tempfile
import threading
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from . import logs
from .analysis import summarize
from .config import Settings
from .detector import Detector, YoloDetector, load_class_map
from .video import VideoError, download, sample_frames

log = logging.getLogger("hygiene")


class AnalyzeRequest(BaseModel):
    videoUrl: str = Field(min_length=8, max_length=4096)


class DetectionOut(BaseModel):
    label: str
    confidence: float
    box: list[float]


class FrameOut(BaseModel):
    t: float
    detections: list[DetectionOut]


class ItemOut(BaseModel):
    compliancePct: float | None
    compliantFrames: int
    violationFrames: int
    notEvaluatedFrames: int
    violationExamples: list[float]


class SummaryOut(BaseModel):
    sampledFrames: int
    personFrames: int
    items: dict[str, ItemOut]


class AnalyzeResponse(BaseModel):
    modelVersion: str
    durationSeconds: float
    frames: list[FrameOut]
    summary: SummaryOut


def load_detector(settings: Settings) -> Detector | None:
    weights = Path(settings.hygiene_model_path)
    if not weights.is_file():
        log.error("No model at %s: /analyze will answer 503 until one is installed (see README)", weights)
        return None
    return YoloDetector(weights, load_class_map(settings.hygiene_class_map_path), settings.confidence, settings.hygiene_model_version)


def create_app(settings: Settings | None = None, detector: Detector | None = None) -> FastAPI:
    settings = settings or Settings()
    slots = threading.BoundedSemaphore(settings.max_concurrent)

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        app.state.detector = detector if detector is not None else load_detector(settings)
        if not settings.ai_service_api_key:
            log.warning("AI_SERVICE_API_KEY is empty: requests are not authenticated (local development only)")
        yield

    app = FastAPI(title="Virtual Live Kitchen hygiene service", lifespan=lifespan)

    @app.exception_handler(VideoError)
    async def video_error(_: Request, e: VideoError):
        return JSONResponse(status_code=e.status, content={"status": e.status, "message": str(e)})

    def require_key(x_api_key: str | None = Header(default=None)) -> None:
        if settings.ai_service_api_key and not secrets.compare_digest(x_api_key or "", settings.ai_service_api_key):
            raise HTTPException(status_code=401, detail="Missing or wrong X-Api-Key")

    @app.get("/health")
    def health(request: Request):
        model = request.app.state.detector
        return {"status": "ok" if model else "no_model", "modelVersion": model.version if model else None}

    @app.post("/analyze", response_model=AnalyzeResponse, dependencies=[Depends(require_key)])
    def analyze(body: AnalyzeRequest, request: Request) -> AnalyzeResponse:
        model: Detector | None = request.app.state.detector
        if model is None:
            raise HTTPException(status_code=503, detail="No detection model is installed")
        work = Path(tempfile.mkdtemp(prefix="hygiene-"))
        try:
            with slots:  # sync endpoint: runs in FastAPI's thread pool; one analysis at a time by default
                video = download(body.videoUrl, settings.allowed_hosts, settings.max_video_mb << 20, settings.download_timeout_s, work)
                sampled = sample_frames(video, settings.sample_fps, settings.max_frames)
                detections = model.detect(sampled.frames)
            summary = summarize(sampled.timestamps, detections)
            return AnalyzeResponse(
                modelVersion=model.version,
                durationSeconds=sampled.duration_s,
                frames=[FrameOut(t=t, detections=[DetectionOut(label=d.label, confidence=d.confidence, box=list(d.box)) for d in found])
                        for t, found in zip(sampled.timestamps, detections)],
                summary=SummaryOut(
                    sampledFrames=summary.sampled_frames,
                    personFrames=summary.person_frames,
                    items={name: ItemOut(compliancePct=i.compliance_pct, compliantFrames=i.compliant_frames, violationFrames=i.violation_frames,
                                         notEvaluatedFrames=i.not_evaluated_frames, violationExamples=i.violation_examples)
                           for name, i in summary.items.items()},
                ),
            )
        finally:
            shutil.rmtree(work, ignore_errors=True)

    return app


logs.configure()
app = create_app()
