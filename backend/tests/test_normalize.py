from datetime import date, datetime, timezone

import pytest

from app.services.normalize import normalize, normalize_notes, tasks_as_notes
from app.services.parser import build_system_prompt
from app.timeutil import VN_TZ, format_now_for_prompt

# Monday 19/10/2026 14:30 in Vietnam, as in the SPEC test table.
NOW = datetime(2026, 10, 19, 14, 30, tzinfo=VN_TZ)


def item(title="Mua sữa", due_date=None, remind_at=None, starred=False):
    return {"title": title, "due_date": due_date, "remind_at": remind_at, "starred": starred}


def test_plain_task():
    [d] = normalize([item()], NOW)
    assert (d.title, d.due_date, d.remind_at, d.starred) == ("Mua sữa", None, None, False)


def test_title_trimmed_and_capitalised():
    [d] = normalize([item("  gọi   cho anh Tuấn ")], NOW)
    assert d.title == "Gọi cho anh Tuấn"


@pytest.mark.parametrize("title", ["", "   ", None, 42])
def test_empty_title_dropped(title):
    assert normalize([item(title), item("Đi chợ")], NOW)[0].title == "Đi chợ"
    assert len(normalize([item(title)], NOW)) == 0


def test_non_dict_items_skipped():
    assert normalize(["Mua sữa", None, item()], NOW)[0].title == "Mua sữa"


def test_reminder_is_vietnam_time():
    [d] = normalize([item(due_date="2026-10-20", remind_at="2026-10-20T09:00")], NOW)
    assert d.due_date == date(2026, 10, 20)
    assert d.remind_at == datetime(2026, 10, 20, 2, 0, tzinfo=timezone.utc)


def test_reminder_without_due_date_fills_due_date():
    [d] = normalize([item(remind_at="2026-10-19T23:30")], NOW)
    assert d.due_date == date(2026, 10, 19)  # 23:30 VN is still the 19th even though it is 16:30 UTC


@pytest.mark.parametrize("bad", ["2026-02-30", "20/10/2026", "2026-10-20T09:00", "tomorrow", 20261020])
def test_bad_due_date_becomes_none(bad):
    [d] = normalize([item(due_date=bad)], NOW)
    assert d.due_date is None


@pytest.mark.parametrize("bad", ["2026-10-20 09:00", "2026-10-20T25:00", "9am", "2026-10-20"])
def test_bad_remind_at_becomes_none(bad):
    [d] = normalize([item(remind_at=bad)], NOW)
    assert d.remind_at is None and d.due_date is None


@pytest.mark.parametrize("due", [None, "2026-10-19"])
def test_time_already_passed_today_moves_to_tomorrow(due):
    [d] = normalize([item(due_date=due, remind_at="2026-10-19T09:00")], NOW)
    assert d.remind_at == datetime(2026, 10, 20, 9, 0, tzinfo=VN_TZ)
    assert d.due_date == date(2026, 10, 20)


def test_due_today_with_reminder_tomorrow_follows_reminder():
    [d] = normalize([item(due_date="2026-10-19", remind_at="2026-10-20T10:00")], NOW)
    assert d.due_date == date(2026, 10, 20)
    assert d.remind_at == datetime(2026, 10, 20, 10, 0, tzinfo=VN_TZ)


def test_later_due_date_with_earlier_future_reminder_kept():
    [d] = normalize([item(due_date="2026-10-23", remind_at="2026-10-22T20:00")], NOW)
    assert d.due_date == date(2026, 10, 23)  # "nhắc tối thứ 5, hạn thứ 6" is fine as said


def test_time_later_today_stays_today():
    [d] = normalize([item(remind_at="2026-10-19T16:00")], NOW)
    assert d.remind_at == datetime(2026, 10, 19, 16, 0, tzinfo=VN_TZ)
    assert d.due_date == date(2026, 10, 19)


def test_reminder_on_an_earlier_day_dropped_but_due_date_kept():
    [d] = normalize([item(due_date="2026-10-17", remind_at="2026-10-17T09:00")], NOW)
    assert d.remind_at is None
    assert d.due_date == date(2026, 10, 17)


def test_passed_time_with_a_later_due_date_moves_to_that_day():
    [d] = normalize([item(due_date="2026-10-23", remind_at="2026-10-19T09:00")], NOW)
    assert d.remind_at == datetime(2026, 10, 23, 9, 0, tzinfo=VN_TZ)
    assert d.due_date == date(2026, 10, 23)


def test_passed_time_with_an_earlier_due_date_dropped():
    [d] = normalize([item(due_date="2026-10-17", remind_at="2026-10-19T09:00")], NOW)
    assert d.remind_at is None
    assert d.due_date == date(2026, 10, 17)


def test_starred_only_when_true():
    assert normalize([item(starred=True)], NOW)[0].starred is True
    assert normalize([item(starred="true")], NOW)[0].starred is False


def test_never_invents_a_reminder():
    [d] = normalize([item(due_date="2026-10-23")], NOW)
    assert d.remind_at is None


def test_order_kept():
    titles = [d.title for d in normalize([item("Một"), item("Hai"), item("Ba")], NOW)]
    assert titles == ["Một", "Hai", "Ba"]


def test_prompt_now_format():
    assert format_now_for_prompt(NOW) == "Thứ Hai (Monday), 19/10/2026 14:30"


def test_prompt_now_uses_vietnam_day_near_midnight():
    late = datetime(2026, 10, 18, 16, 30, tzinfo=timezone.utc)  # 23:30 Sunday in Vietnam
    assert format_now_for_prompt(late) == "Chủ Nhật (Sunday), 18/10/2026 23:30"


def test_system_prompt_as_note_line():
    assert "màn hình Ghi chú" not in build_system_prompt(NOW)
    assert build_system_prompt(NOW, as_note=True).endswith(
        "\nNgười nói đang ở màn hình Ghi chú: mọi nội dung là ghi chú, tasks luôn rỗng."
    )


def test_system_prompt_contains_now():
    prompt = build_system_prompt(NOW)
    assert "Bây giờ là Thứ Hai (Monday), 19/10/2026 14:30 (giờ Việt Nam)." in prompt
    assert "{now}" not in prompt


# ---------- notes ----------

def note(text="ý tưởng hay", label="idea", author=None, source="ý tưởng hay"):
    return {"source": source, "text": text, "label": label, "author": author}


def test_note_text_cleaned_but_not_rewritten():
    [n] = normalize_notes([note("  làm app   nhắc con\nuống nước. ")])
    assert (n.text, n.label, n.author, n.starred) == ("Làm app nhắc con uống nước.", "idea", None, False)


def test_note_long_text_kept_whole():
    long = "câu " * 300
    [n] = normalize_notes([note(long)])
    assert n.text == long.strip().capitalize()


@pytest.mark.parametrize("text", ["", "  ", None, 7])
def test_empty_note_dropped(text):
    assert normalize_notes([note(text), note("Hai")])[0].text == "Hai"
    assert normalize_notes([note(text)]) == []


@pytest.mark.parametrize("label", ["task", None, "IDEA", 3])
def test_unknown_label_becomes_note(label):
    assert normalize_notes([note(label=label)])[0].label == "note"


@pytest.mark.parametrize("author,expected", [(" Steve Jobs ", "Steve Jobs"), ("", None), ("  ", None), (None, None), (5, None)])
def test_note_author(author, expected):
    assert normalize_notes([note(label="quote", author=author)])[0].author == expected


def test_non_dict_notes_skipped():
    assert [n.text for n in normalize_notes(["x", None, note("Một")])] == ["Một"]


def test_tasks_as_notes_use_spoken_source():
    items = [
        {"source": "gọi cho anh Tuấn hỏi về hợp đồng", "title": "Gọi cho anh Tuấn", "due_date": None,
         "remind_at": None, "starred": True},
        {"source": "", "title": "mua sữa", "due_date": None, "remind_at": None, "starred": False},
        "junk",
    ]
    out = tasks_as_notes(items)
    assert [(n["text"], n["label"], n["author"]) for n in out] == [
        ("gọi cho anh Tuấn hỏi về hợp đồng", "note", None),
        ("mua sữa", "note", None),  # no source: fall back to the title
    ]
    assert [n.starred for n in normalize_notes(out)] == [True, False]


def test_tasks_as_notes_drops_duplicates():
    task = {"source": "gọi cho anh Tuấn hỏi về hợp đồng", "title": "Gọi cho anh Tuấn", "due_date": None,
            "remind_at": None, "starred": False}
    model_note = {"source": "x", "text": "Gọi cho anh Tuấn hỏi về hợp đồng.", "label": "idea", "author": None}
    assert len(tasks_as_notes([task, dict(task)])) == 1  # the model sometimes repeats the same span
    assert tasks_as_notes([task], [model_note]) == []  # already returned as a note
