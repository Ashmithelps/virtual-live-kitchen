from app.detector import Detection
from app.evaluation import Counts, iou, match_image


def test_iou():
    assert iou((0, 0, 1, 1), (0, 0, 1, 1)) == 1.0
    assert iou((0, 0, 1, 1), (2, 2, 3, 3)) == 0.0
    assert round(iou((0, 0, 2, 2), (1, 1, 3, 3)), 3) == round(1 / 7, 3)


def test_precision_and_recall_with_greedy_matching():
    counts: dict[str, Counts] = {}
    predicted = [Detection("gloves", 0.9, (0.1, 0.1, 0.3, 0.3)),       # matches truth 1
                 Detection("gloves", 0.8, (0.11, 0.11, 0.31, 0.31)),   # duplicate of the same glove: FP
                 Detection("no_mask", 0.7, (0.5, 0.5, 0.6, 0.6))]      # nothing there: FP
    truth = [("gloves", (0.1, 0.1, 0.3, 0.3)), ("gloves", (0.6, 0.6, 0.8, 0.8))]  # second glove missed: FN
    match_image(predicted, truth, counts)
    assert (counts["gloves"].tp, counts["gloves"].fp, counts["gloves"].fn) == (1, 1, 1)
    assert counts["gloves"].precision == 0.5 and counts["gloves"].recall == 0.5
    assert counts["no_mask"].precision == 0.0 and counts["no_mask"].recall is None
