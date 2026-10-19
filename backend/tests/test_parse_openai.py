"""Real OpenAI parse tests (SPEC: Pipeline AI, mandatory suite). Run: pytest -m openai

Each sentence runs 3 times to catch unstable answers. `now` is fixed to Monday 19/10/2026 14:30 (Vietnam).
"""
from datetime import datetime

import pytest

from app.services.normalize import normalize, normalize_notes, tasks_as_notes
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
# sentence -> (tasks as in CASES, notes as (keyword in text, label, author keyword or None))
NOTE_CASES = {
    "hôm nay trời đẹp quá": ([], [("trời đẹp", "note", None)]),
    "the weather is nice today": ([], [("weather", "note", None)]),
    "ý tưởng: làm app nhắc con uống nước": ([], [("uống nước", "idea", None)]),
    "câu hay của Steve Jobs: stay hungry, stay foolish": ([], [("stay hungry", "quote", "jobs")]),
    "có công mài sắt có ngày nên kim": ([], [("mài sắt", "quote", None)]),
    "idea: a podcast about sharks for kids": ([], [("podcast", "idea", None)]),
    "mai 9 giờ sáng gọi anh Tuấn, à ý tưởng là thêm chế độ tối cho app": (
        [("tuấn", "2026-10-20", "2026-10-20T09:00", False)],
        [("chế độ tối", "idea", None)],
    ),
}
# Lead-in words the note text must not keep (SPEC: text drops "ý tưởng là", "câu hay của …", "idea:").
LEAD_INS = ["ý tưởng", "câu hay", "idea:", "steve jobs"]


def parse(text, as_note=False):
    """LLM output after the server's post-check, i.e. what gets stored: (tasks, notes)."""
    raw = get_parser().parse(text, NOW, as_note)
    raw_tasks, raw_notes = raw["tasks"], raw["notes"]
    if as_note:
        raw_tasks, raw_notes = [], tasks_as_notes(raw_tasks, raw_notes) + raw_notes
    tasks = [
        {
            "title": d.title,
            "due_date": d.due_date.isoformat() if d.due_date else None,
            "remind_at": d.remind_at.astimezone(VN_TZ).strftime("%Y-%m-%dT%H:%M") if d.remind_at else None,
            "starred": d.starred,
        }
        for d in normalize(raw_tasks, NOW)
    ]
    notes = [{"text": n.text, "label": n.label, "author": n.author} for n in normalize_notes(raw_notes)]
    return tasks, notes


def check_tasks(got, expected):
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
@pytest.mark.parametrize("sentence,expected", CASES.items())
def test_sentence(sentence, expected, run):
    tasks, notes = parse(sentence)
    check_tasks(tasks, expected)
    assert notes == []


@pytest.mark.parametrize("run", RUNS)
@pytest.mark.parametrize("sentence,expected", NOTE_CASES.items())
def test_notes(sentence, expected, run):
    expected_tasks, expected_notes = expected
    tasks, notes = parse(sentence)
    check_tasks(tasks, expected_tasks)
    assert len(notes) == len(expected_notes), notes
    for keyword, label, author in expected_notes:
        [note] = [n for n in notes if keyword in n["text"].lower()] or [None]
        assert note is not None, f"no note with {keyword!r}: {notes}"
        assert note["label"] == label, note
        assert not any(w in note["text"].lower() for w in LEAD_INS), note
        if author is None:
            assert note["author"] is None, note
        else:
            assert author in (note["author"] or "").lower(), note


@pytest.mark.parametrize("run", RUNS)
def test_as_note_mode_makes_no_task(run):
    tasks, notes = parse("gọi cho anh Tuấn hỏi về hợp đồng", as_note=True)
    assert tasks == []
    assert len(notes) == 1 and "hợp đồng" in notes[0]["text"], notes


@pytest.mark.parametrize("run", RUNS)
def test_filler_makes_nothing(run):
    assert parse("ừm… à…") == ([], [])


def test_title_drops_time_words():
    [task], _ = parse("mai 9 giờ sáng gọi cho anh Tuấn")
    title = task["title"].lower()
    assert "mai" not in title.split() and "9 giờ" not in title
