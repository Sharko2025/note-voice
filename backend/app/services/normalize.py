"""Post-check of the LLM output → valid task drafts (pure, no I/O)."""
import re
from dataclasses import dataclass
from datetime import date, datetime, timedelta

from app.models import NOTE_LABELS
from app.timeutil import VN_TZ, vn_date

_DATE = re.compile(r"\d{4}-\d{2}-\d{2}")
_DATETIME = re.compile(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}")


@dataclass
class TaskDraft:
    title: str
    due_date: date | None
    remind_at: datetime | None
    starred: bool


def normalize(items: list, recorded_at: datetime) -> list[TaskDraft]:
    drafts = []
    for item in items:
        if not isinstance(item, dict):
            continue
        title = clean_title(item.get("title"))
        if not title:
            continue
        due_date = _parse_date(item.get("due_date"))
        remind_at = _parse_local_datetime(item.get("remind_at"))
        due_date, remind_at = _roll_passed_time(due_date, remind_at, recorded_at)
        due_date = fill_due_date(due_date, remind_at)
        if remind_at is not None and remind_at < recorded_at:
            remind_at = None  # never schedule an alarm in the past; the due date stays
        drafts.append(TaskDraft(title, due_date, remind_at, item.get("starred") is True))
    return drafts


@dataclass
class NoteDraft:
    text: str
    label: str
    author: str | None
    starred: bool = False


def normalize_notes(items: list) -> list[NoteDraft]:
    """Notes keep the spoken words; only whitespace and the first letter are touched."""
    drafts = []
    for item in items:
        if not isinstance(item, dict):
            continue
        text = clean_title(item.get("text"))
        if not text:
            continue
        label = item.get("label") if item.get("label") in NOTE_LABELS else "note"
        drafts.append(NoteDraft(text, label, clean_author(item.get("author")), item.get("starred") is True))
    return drafts


def tasks_as_notes(items: list, existing_notes: list = ()) -> list[dict]:
    """as_note mode: a task the model returned anyway becomes a note of what was said (SPEC: Pipeline AI).

    The model sometimes repeats one span as two tasks, or returns it as a note too: keep each text once.
    """
    seen = {_note_key(n.get("text")) for n in existing_notes if isinstance(n, dict)}
    notes = []
    for item in items:
        if not isinstance(item, dict):
            continue
        source = item.get("source")
        text = source if isinstance(source, str) and source.strip() else item.get("title")
        key = _note_key(text)
        if not key or key in seen:
            continue
        seen.add(key)
        notes.append({"text": text, "label": "note", "author": None, "starred": item.get("starred") is True})
    return notes


def _note_key(text) -> str:
    return " ".join(text.lower().split()).rstrip(".!?…") if isinstance(text, str) else ""


def clean_author(value) -> str | None:
    if not isinstance(value, str):
        return None
    return " ".join(value.split()) or None


def clean_title(value) -> str:
    title = " ".join(value.split()) if isinstance(value, str) else ""
    return title[:1].upper() + title[1:]


def fill_due_date(due_date: date | None, remind_at: datetime | None) -> date | None:
    """A reminder without a due date is due on the reminder's Vietnam day (SPEC: Schema DB)."""
    if due_date is None and remind_at is not None:
        return vn_date(remind_at)
    return due_date


def _roll_passed_time(due_date, remind_at, recorded_at):
    """A time already passed on the day it was spoken means tomorrow ("10 giờ sáng" said at 14:30).

    The LLM gets this wrong about 1 time in 3, so the rule is applied here as well (SPEC: Pipeline AI).
    """
    today = vn_date(recorded_at)
    if remind_at is not None and due_date == today and vn_date(remind_at) > today:
        # Reminder already rolled to tomorrow but the due date left on today: follow the reminder.
        return vn_date(remind_at), remind_at
    if remind_at is None or remind_at >= recorded_at or vn_date(remind_at) != today:
        return due_date, remind_at
    if due_date is not None and due_date > today:
        # Right day, wrong reminder day ("thứ 6 lúc 9 giờ" with the reminder left on today).
        return due_date, remind_at + timedelta(days=(due_date - today).days)
    if due_date is not None and due_date < today:
        return due_date, None
    remind_at = remind_at + timedelta(days=1)
    return vn_date(remind_at), remind_at


def _parse_date(value) -> date | None:
    if not isinstance(value, str) or not _DATE.fullmatch(value):
        return None
    try:
        return date.fromisoformat(value)
    except ValueError:
        return None


def _parse_local_datetime(value) -> datetime | None:
    """'YYYY-MM-DDTHH:MM' in Vietnam time → aware datetime."""
    if not isinstance(value, str) or not _DATETIME.fullmatch(value):
        return None
    try:
        return datetime.strptime(value, "%Y-%m-%dT%H:%M").replace(tzinfo=VN_TZ)
    except ValueError:
        return None
