"""Precision / recall of a detector against labelled boxes (used by scripts/eval.py)."""

from __future__ import annotations

from dataclasses import dataclass

from .detector import Detection


@dataclass
class Counts:
    tp: int = 0
    fp: int = 0
    fn: int = 0

    @property
    def precision(self) -> float | None:
        return self.tp / (self.tp + self.fp) if self.tp + self.fp else None

    @property
    def recall(self) -> float | None:
        return self.tp / (self.tp + self.fn) if self.tp + self.fn else None


def iou(a: tuple[float, float, float, float], b: tuple[float, float, float, float]) -> float:
    ix = max(0.0, min(a[2], b[2]) - max(a[0], b[0]))
    iy = max(0.0, min(a[3], b[3]) - max(a[1], b[1]))
    inter = ix * iy
    union = (a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - inter
    return inter / union if union > 0 else 0.0


def match_image(predicted: list[Detection], truth: list[tuple[str, tuple[float, float, float, float]]], counts: dict[str, Counts],
                iou_threshold: float = 0.5) -> None:
    """Greedy matching per label, most confident prediction first; one truth box matches at most one prediction."""
    labels = {d.label for d in predicted} | {label for label, _ in truth}
    for label in labels:
        c = counts.setdefault(label, Counts())
        gt = [box for l, box in truth if l == label]
        used = [False] * len(gt)
        for det in sorted((d for d in predicted if d.label == label), key=lambda d: -d.confidence):
            best, best_iou = -1, iou_threshold
            for i, box in enumerate(gt):
                if not used[i]:
                    overlap = iou(det.box, box)
                    if overlap >= best_iou:
                        best, best_iou = i, overlap
            if best >= 0:
                used[best] = True
                c.tp += 1
            else:
                c.fp += 1
        c.fn += used.count(False)
