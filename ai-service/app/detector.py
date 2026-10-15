"""Object detection behind a small interface so the model can be swapped (or faked in tests)."""

from __future__ import annotations

import hashlib
import logging
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Protocol, Sequence

import numpy as np
import yaml

log = logging.getLogger(__name__)

# Our labels. A model's own class names are mapped onto these by models/class_map.yaml.
LABELS = frozenset({"person", "gloves", "no_gloves", "head_cover", "no_head_cover", "mask", "no_mask"})


@dataclass(frozen=True)
class Detection:
    label: str
    confidence: float
    # x1, y1, x2, y2 as fractions of the frame size (0..1).
    box: tuple[float, float, float, float]


class Detector(Protocol):
    version: str

    def detect(self, frames: Sequence[np.ndarray]) -> list[list[Detection]]: ...


def load_class_map(path: str | Path) -> dict[str, str]:
    """Reads {model class name: our label}; unknown target labels are an error."""
    data = yaml.safe_load(Path(path).read_text()) or {}
    mapping = {str(k).strip().lower(): str(v).strip() for k, v in (data.get("classes") or {}).items()}
    bad = sorted({v for v in mapping.values() if v not in LABELS})
    if bad:
        raise ValueError(f"{path}: unknown target labels {bad}; use one of {sorted(LABELS)}")
    return mapping


def weights_version(path: str | Path) -> str:
    digest = hashlib.sha256(Path(path).read_bytes()).hexdigest()[:12]
    return f"{Path(path).name}@{digest}"


class YoloDetector:
    """Ultralytics YOLO. Classes the class map doesn't mention are ignored."""

    def __init__(self, weights: str | Path, class_map: dict[str, str], confidence: float, version: str | None = None,
                 model: Any = None):
        self.confidence = confidence
        self.class_map = class_map
        self.version = version or weights_version(weights)
        if model is None:
            from ultralytics import YOLO  # heavy import; only when a real model is used

            model = YOLO(str(weights))
        self.model = model
        names = getattr(model, "names", {}) or {}
        unmapped = sorted(n for n in names.values() if str(n).lower() not in class_map)
        if unmapped:
            log.info("Model classes ignored (not in the class map): %s", unmapped)
        missing = sorted(LABELS - set(class_map.values()))
        if missing:
            log.warning("The class map has no model class for %s; those items will be reported as not evaluated", missing)

    def detect(self, frames: Sequence[np.ndarray]) -> list[list[Detection]]:
        if not frames:
            return []
        results = self.model.predict(list(frames), conf=self.confidence, verbose=False)
        out: list[list[Detection]] = []
        for result in results:
            names = result.names
            found: list[Detection] = []
            boxes = result.boxes
            for cls, conf, xyxyn in zip(boxes.cls.tolist(), boxes.conf.tolist(), boxes.xyxyn.tolist()):
                label = self.class_map.get(str(names[int(cls)]).lower())
                if label:
                    found.append(Detection(label, round(float(conf), 3), tuple(round(float(c), 4) for c in xyxyn)))
            out.append(found)
        return out
