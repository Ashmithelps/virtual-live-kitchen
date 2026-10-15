from pathlib import Path

import numpy as np
import pytest

from app.detector import YoloDetector, load_class_map


class Boxes:
    def __init__(self, rows):
        self.cls = _T([r[0] for r in rows])
        self.conf = _T([r[1] for r in rows])
        self.xyxyn = _T([r[2] for r in rows])


class _T(list):
    def tolist(self):
        return list(self)


class Result:
    def __init__(self, names, rows):
        self.names = names
        self.boxes = Boxes(rows)


class FakeYolo:
    names = {0: "Person", 1: "Gloves", 2: "No-Gloves", 3: "knife"}

    def predict(self, frames, conf, verbose):
        return [Result(self.names, [(0, 0.91, [0.1, 0.1, 0.9, 0.9]), (2, 0.66, [0.2, 0.5, 0.3, 0.6]), (3, 0.5, [0, 0, 1, 1])])
                for _ in frames]


def test_model_classes_are_mapped_and_unknown_ones_dropped():
    detector = YoloDetector("unused.pt", {"person": "person", "gloves": "gloves", "no-gloves": "no_gloves"}, 0.3, "fake@1", model=FakeYolo())
    [found] = detector.detect([np.zeros((10, 10, 3), dtype=np.uint8)])
    assert [(d.label, d.confidence) for d in found] == [("person", 0.91), ("no_gloves", 0.66)]
    assert found[1].box == (0.2, 0.5, 0.3, 0.6)


def test_shipped_class_map_is_valid():
    mapping = load_class_map(Path(__file__).parent.parent / "models" / "class_map.yaml")
    assert mapping["person"] == "person" and mapping["no-gloves"] == "no_gloves"


def test_bad_class_map_target_is_rejected(tmp_path: Path):
    bad = tmp_path / "map.yaml"
    bad.write_text("classes:\n  gloves: hand_protection\n")
    with pytest.raises(ValueError):
        load_class_map(bad)
