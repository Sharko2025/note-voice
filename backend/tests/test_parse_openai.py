"""Real OpenAI parse tests (SPEC: Pipeline AI, mandatory suite). Run: pytest -m openai

Each sentence runs 3 times to catch unstable answers. `now` is fixed to Monday 19/10/2026 14:30 (Vietnam).
"""
from datetime import datetime

import pytest

from app.services.normalize import normalize
from app.services.parser import get_parser
from app.timeutil import VN_TZ

pytestmark = pytest.mark.openai

NOW = datetime(2026, 10, 19, 14, 30, tzinfo=VN_TZ)
RUNS = [1, 2, 3]

# sentence -> list of (keyword in title, due_date, remind_at, starred); ANY = not checked
ANY = object()
CASES = {
    "mai 9 giờ sáng gọi cho anh Tuấn, mua sữa cho con, quan trọng": [
        ("tuấn", "2026-10-20", "2026-10-20T09:00", False),
        ("sữa", None, None, True),
    ],
    "Mai chín giờ sáng gọi cho anh Tuấn mua sữa cho con, quan trọng.": [  # STT output without the comma
        ("tuấn", "2026-10-20", "2026-10-20T09:00", False),
        ("sữa", None, None, True),
    ],
    "đi siêu thị mua sữa và trứng": [("siêu thị", None, None, False)],
    "nhắc tôi 9 giờ sáng gọi mẹ": [("mẹ", "2026-10-20", "2026-10-20T09:00", False)],
    "mua sữa": [("sữa", None, None, False)],
    "mai 9 giờ sáng gọi cho anh Tuấn": [("tuấn", "2026-10-20", "2026-10-20T09:00", False)],
    "nộp báo cáo thứ 6, quan trọng": [("báo cáo", "2026-10-23", None, True)],
    "nhắc tôi 4 giờ chiều uống thuốc": [("thuốc", "2026-10-19", "2026-10-19T16:00", False)],
    "nhắc tôi 10 giờ sáng đi lấy đồ": [("lấy đồ", "2026-10-20", "2026-10-20T10:00", False)],
    "mua sữa cho con, gửi email cho chị Lan chiều mai nhắc tôi": [
        ("sữa", None, None, False),
        ("lan", "2026-10-20", "2026-10-20T15:00", False),
    ],
    "ngày kia đóng tiền điện": [("tiền điện", "2026-10-21", None, False)],
    "chủ nhật đi siêu thị 9 rưỡi sáng": [("siêu thị", "2026-10-25", "2026-10-25T09:30", False)],
    "hạn 25/10 nộp thuế, gấp": [("thuế", "2026-10-25", None, True)],
    "remind me to call the bank tomorrow at 3pm": [("bank", "2026-10-20", "2026-10-20T15:00", False)],
    "buy milk and pay rent on Friday, it's urgent": [
        ("milk", ANY, None, False),  # "on Friday" may cover both tasks; "it's urgent" only the rent
        ("rent", "2026-10-23", None, True),
    ],
}
NO_TASK = ["hôm nay trời đẹp quá", "the weather is nice today"]


def parse(text):
    """LLM output after the server's post-check, i.e. what gets stored."""
    return [
        {
            "title": d.title,
            "due_date": d.due_date.isoformat() if d.due_date else None,
            "remind_at": d.remind_at.astimezone(VN_TZ).strftime("%Y-%m-%dT%H:%M") if d.remind_at else None,
            "starred": d.starred,
        }
        for d in normalize(get_parser().parse(text, NOW), NOW)
    ]


@pytest.mark.parametrize("run", RUNS)
@pytest.mark.parametrize("sentence,expected", CASES.items())
def test_sentence(sentence, expected, run):
    got = parse(sentence)
    assert len(got) == len(expected), got
    for keyword, due, remind, starred in expected:
        [task] = [t for t in got if keyword in t["title"].lower()] or [None]
        assert task is not None, f"no title with {keyword!r}: {got}"
        assert task["title"][:1].isupper(), task
        if due is not ANY:
            assert task["due_date"] == due, task
        assert task["remind_at"] == remind, task
        if starred is not ANY:
            assert task["starred"] is starred, task


@pytest.mark.parametrize("run", RUNS)
@pytest.mark.parametrize("sentence", NO_TASK)
def test_no_task(sentence, run):
    assert parse(sentence) == []


def test_title_drops_time_words():
    [task] = parse("mai 9 giờ sáng gọi cho anh Tuấn")
    title = task["title"].lower()
    assert "mai" not in title.split() and "9 giờ" not in title
