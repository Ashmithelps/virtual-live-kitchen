"""Fine-tune a YOLO detector on a PPE / kitchen-hygiene dataset (YOLO format).

    python -m scripts.train --data /data/kitchen-ppe/data.yaml --base yolo11n.pt --epochs 80

Prints where the best weights were saved; copy them to models/ and point HYGIENE_MODEL_PATH at them.
"""

from __future__ import annotations

import argparse
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--data", required=True, help="dataset YAML (train/val image dirs + class names)")
    parser.add_argument("--base", default="yolo11n.pt", help="starting weights (a .pt file, or a model name Ultralytics downloads)")
    parser.add_argument("--epochs", type=int, default=80)
    parser.add_argument("--imgsz", type=int, default=640)
    parser.add_argument("--batch", type=int, default=16)
    parser.add_argument("--device", default=None, help="e.g. 0 for the first GPU, cpu, mps")
    parser.add_argument("--project", default="runs/hygiene")
    parser.add_argument("--name", default="train")
    args = parser.parse_args()

    from ultralytics import YOLO

    model = YOLO(args.base)
    model.train(data=args.data, epochs=args.epochs, imgsz=args.imgsz, batch=args.batch, device=args.device,
                project=args.project, name=args.name, exist_ok=False)
    best = Path(model.trainer.save_dir) / "weights" / "best.pt"
    print(f"\nBest weights: {best}")
    print("Class names in this model (map them in models/class_map.yaml):", list(model.names.values()))


if __name__ == "__main__":
    main()
