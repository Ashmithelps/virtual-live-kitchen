"""Turns per-frame detections into compliance numbers. Pure functions (no model, no I/O)."""

from __future__ import annotations

from dataclasses import dataclass, field

from .detector import Detection

# item → (label that shows compliance, label that shows a violation)
ITEMS: dict[str, tuple[str, str]] = {
    "gloves": ("gloves", "no_gloves"),
    "headCover": ("head_cover", "no_head_cover"),
    "mask": ("mask", "no_mask"),
}
PPE_LABELS = frozenset(label for pair in ITEMS.values() for label in pair)
EXAMPLES = 3


@dataclass
class ItemSummary:
    compliant_frames: int = 0
    violation_frames: int = 0
    # Person-frames where neither label was seen (hands/face out of view, or the model can't tell).
    not_evaluated_frames: int = 0
    compliance_pct: float | None = None
    violation_examples: list[float] = field(default_factory=list)


@dataclass
class Summary:
    sampled_frames: int
    person_frames: int
    items: dict[str, ItemSummary]


def is_person_frame(detections: list[Detection]) -> bool:
    # A glove or a cap means someone is there even if the person box was missed.
    return any(d.label == "person" or d.label in PPE_LABELS for d in detections)


def spread(values: list[float], k: int = EXAMPLES) -> list[float]:
    """Up to k values spread across the list (first, middle, last…), in order."""
    if len(values) <= k:
        return list(values)
    if k == 1:
        return [values[0]]
    return [values[round(i * (len(values) - 1) / (k - 1))] for i in range(k)]


def summarize(timestamps: list[float], detections: list[list[Detection]]) -> Summary:
    """
    Per item, among frames with a person: a frame is a violation if the "no_" label was detected, compliant if
    only the positive label was, and not evaluated otherwise. Compliance % = compliant / (compliant + violation),
    i.e. of the person-frames where the item could be judged; None when none could.
    """
    items = {name: ItemSummary() for name in ITEMS}
    violations: dict[str, list[float]] = {name: [] for name in ITEMS}
    person_frames = 0
    for t, found in zip(timestamps, detections):
        if not is_person_frame(found):
            continue
        person_frames += 1
        labels = {d.label for d in found}
        for name, (positive, negative) in ITEMS.items():
            if negative in labels:
                items[name].violation_frames += 1
                violations[name].append(t)
            elif positive in labels:
                items[name].compliant_frames += 1
            else:
                items[name].not_evaluated_frames += 1
    for name, item in items.items():
        judged = item.compliant_frames + item.violation_frames
        item.compliance_pct = round(100.0 * item.compliant_frames / judged, 1) if judged else None
        item.violation_examples = spread(violations[name])
    return Summary(sampled_frames=len(timestamps), person_frames=person_frames, items=items)
