import json
import logging

from app import logs


def test_json_format_is_one_object_per_line(capsys):
    logs.configure("json")
    logging.getLogger("hygiene").info("analysed %s frames", 12)
    try:
        raise ValueError("boom")
    except ValueError:
        logging.getLogger("hygiene").exception("failed")
    lines = [json.loads(line) for line in capsys.readouterr().err.strip().splitlines()]
    assert lines[0]["message"] == "analysed 12 frames"
    assert lines[0]["log.level"] == "INFO" and lines[0]["service.name"] == "virtual-kitchen-ai"
    assert "ValueError: boom" in lines[1]["error.stack_trace"]
    logs.configure("text")
