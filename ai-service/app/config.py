"""Settings, all from environment variables (see .env.example)."""

from functools import cached_property

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore", protected_namespaces=())

    # Detector weights (an Ultralytics YOLO .pt). Swap models by changing this path; see README.
    hygiene_model_path: str = "models/hygiene.pt"
    # Maps the model's class names onto ours (person, gloves, no_gloves, head_cover, ...).
    hygiene_class_map_path: str = "models/class_map.yaml"
    # Stored with every result; default "<file name>@<sha256 prefix>" of the weights.
    hygiene_model_version: str | None = None
    confidence: float = Field(0.35, ge=0.01, le=0.99)
    sample_fps: float = Field(1.0, gt=0, le=10)
    max_frames: int = Field(180, ge=1)
    max_video_mb: int = Field(200, ge=1)
    download_timeout_s: float = Field(60, gt=0)
    # Only videos from these hosts ("host:port") are fetched (no requests to arbitrary URLs).
    allowed_video_hosts: str = "localhost:9000,127.0.0.1:9000,minio:9000"
    # Shared secret the backend sends as X-Api-Key. Empty = no check (local development only).
    ai_service_api_key: str = ""
    # Analyses at a time (each holds a video and model activations in memory).
    max_concurrent: int = Field(1, ge=1)

    @cached_property
    def allowed_hosts(self) -> frozenset[str]:
        return frozenset(h.strip().lower() for h in self.allowed_video_hosts.split(",") if h.strip())
