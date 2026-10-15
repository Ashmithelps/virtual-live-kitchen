"""Precision / recall of the configured detector on a labelled folder (YOLO format).

    python -m scripts.eval --images /data/eval/images --labels /data/eval/labels --names /data/eval/data.yaml

The folder's class ids are named by --names (a dataset YAML with ``names``) and mapped through the same
class map as the model, so both sides speak our labels (gloves, no_gloves, ...). A prediction counts as a
true positive when its box overlaps an unmatched labelled box of the same label with IoU ≥ --iou.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import cv2
import yaml

from app.config import Settings
from app.detector import YoloDetector, load_class_map
from app.evaluation import Counts, match_image

IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}


def read_names(path: Path) -> dict[int, str]:
    names = yaml.safe_load(path.read_text())["names"]
    return {i: n for i, n in enumerate(names)} if isinstance(names, list) else {int(k): v for k, v in names.items()}


def read_truth(label_file: Path, names: dict[int, str], class_map: dict[str, str]):
    truth = []
    if label_file.exists():
        for line in label_file.read_text().splitlines():
            parts = line.split()
            if len(parts) < 5:
                continue
            label = class_map.get(names.get(int(parts[0]), "").lower())
            if not label:
                continue
            cx, cy, w, h = map(float, parts[1:5])
            truth.append((label, (cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)))
    return truth


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--images", type=Path, required=True)
    parser.add_argument("--labels", type=Path, required=True)
    parser.add_argument("--names", type=Path, required=True, help="dataset YAML with the class names of the label files")
    parser.add_argument("--weights", default=None, help="defaults to HYGIENE_MODEL_PATH")
    parser.add_argument("--iou", type=float, default=0.5)
    parser.add_argument("--json", type=Path, default=None, help="also write the report here")
    args = parser.parse_args()

    settings = Settings()
    class_map = load_class_map(settings.hygiene_class_map_path)
    detector = YoloDetector(args.weights or settings.hygiene_model_path, class_map, settings.confidence)
    names = read_names(args.names)
    counts: dict[str, Counts] = {}
    images = sorted(p for p in args.images.iterdir() if p.suffix.lower() in IMAGE_SUFFIXES)
    for image in images:
        frame = cv2.imread(str(image))
        if frame is None:
            continue
        predicted = detector.detect([frame])[0]
        match_image(predicted, read_truth(args.labels / f"{image.stem}.txt", names, class_map), counts, args.iou)

    report = {label: {"tp": c.tp, "fp": c.fp, "fn": c.fn, "precision": c.precision, "recall": c.recall} for label, c in sorted(counts.items())}
    print(f"Model {detector.version} on {len(images)} images (IoU ≥ {args.iou})\n")
    print(f"{'label':<15}{'precision':>10}{'recall':>10}{'tp':>6}{'fp':>6}{'fn':>6}")
    for label, r in report.items():
        fmt = lambda v: f"{v:.3f}" if v is not None else "  n/a"
        print(f"{label:<15}{fmt(r['precision']):>10}{fmt(r['recall']):>10}{r['tp']:>6}{r['fp']:>6}{r['fn']:>6}")
    if args.json:
        args.json.write_text(json.dumps({"model": detector.version, "images": len(images), "labels": report}, indent=2))


if __name__ == "__main__":
    main()
