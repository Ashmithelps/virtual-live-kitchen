from app.analysis import spread, summarize
from app.detector import Detection

BOX = (0.1, 0.1, 0.5, 0.5)


def d(label: str) -> Detection:
    return Detection(label, 0.9, BOX)


def test_compliance_is_measured_over_person_frames_where_the_item_could_be_judged():
    timestamps = [0.0, 1.0, 2.0, 3.0, 4.0, 5.0]
    detections = [
        [],                                              # empty kitchen: not a person-frame
        [d("person"), d("gloves"), d("head_cover")],
        [d("person"), d("no_gloves"), d("head_cover")],
        [d("person"), d("gloves"), d("no_head_cover")],
        [d("person")],                                   # hands not visible: nothing judged
        [d("gloves"), d("mask")],                        # PPE seen, person box missed: still a person-frame
    ]
    s = summarize(timestamps, detections)
    assert s.sampled_frames == 6
    assert s.person_frames == 5
    gloves = s.items["gloves"]
    assert (gloves.compliant_frames, gloves.violation_frames, gloves.not_evaluated_frames) == (3, 1, 1)
    assert gloves.compliance_pct == 75.0
    assert gloves.violation_examples == [2.0]
    assert s.items["headCover"].compliance_pct == 66.7
    assert s.items["headCover"].violation_examples == [3.0]
    assert s.items["mask"].compliance_pct == 100.0


def test_a_violation_wins_over_a_compliant_detection_in_the_same_frame():
    s = summarize([0.0], [[d("person"), d("gloves"), d("no_gloves")]])
    assert s.items["gloves"].violation_frames == 1
    assert s.items["gloves"].compliance_pct == 0.0


def test_items_never_seen_are_not_scored():
    s = summarize([0.0, 1.0], [[d("person")], [d("person")]])
    assert all(item.compliance_pct is None for item in s.items.values())
    assert summarize([], []).person_frames == 0


def test_three_example_timestamps_spread_across_the_violations():
    assert spread([1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0]) == [1.0, 4.0, 7.0]
    assert spread([1.0, 2.0]) == [1.0, 2.0]
    s = summarize([float(t) for t in range(10)], [[d("person"), d("no_mask")]] * 10)
    assert s.items["mask"].violation_examples == [0.0, 4.0, 9.0]
