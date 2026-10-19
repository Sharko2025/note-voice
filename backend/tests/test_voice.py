import uuid
from datetime import date, datetime, timedelta, timezone

import pytest
from sqlalchemy import func, select, text

from app.models import Task, VoiceEntry
from app.services.errors import AIServiceError

RECORDED_AT = "2026-10-19T14:30:00+07:00"


def post_voice(client, auth, entry_id=None, recorded_at=RECORDED_AT, audio=b"fake-m4a-bytes", **extra):
    entry_id = entry_id or str(uuid.uuid4())
    data = {"entry_id": entry_id, "recorded_at": recorded_at, **extra}
    resp = client.post(
        "/api/v1/voice", headers=auth, data=data, files={"file": ("rec.m4a", audio, "audio/mp4")}
    )
    return entry_id, resp


def count(db, model):
    return db.scalar(select(func.count()).select_from(model))


def test_creates_tasks_in_spoken_order(client, auth, db, stt, parser):
    parser.tasks = [
        {"title": "Gọi cho anh Tuấn", "due_date": "2026-10-20", "remind_at": "2026-10-20T09:00", "starred": False},
        {"title": "mua sữa cho con", "due_date": None, "remind_at": None, "starred": True},
    ]
    entry_id, resp = post_voice(client, auth)

    assert resp.status_code == 200
    body = resp.json()
    assert body["entry_id"] == entry_id
    assert body["raw_text"] == stt.text
    first, second = body["tasks"]
    assert (first["title"], first["due_date"], first["starred"]) == ("Gọi cho anh Tuấn", "2026-10-20", False)
    assert datetime.fromisoformat(first["remind_at"]) == datetime(2026, 10, 20, 2, 0, tzinfo=timezone.utc)
    assert (second["title"], second["due_date"], second["remind_at"], second["starred"]) == (
        "Mua sữa cho con", None, None, True,
    )
    assert all(t["voice_entry_id"] == entry_id and not t["completed"] for t in body["tasks"])
    entry = db.get(VoiceEntry, uuid.UUID(entry_id))
    assert entry.status == "parsed" and entry.raw_text == stt.text
    assert count(db, Task) == 2


def test_parser_gets_recorded_at_as_now(client, auth, stt, parser):
    post_voice(client, auth)
    text_arg, now = parser.calls[0]
    assert text_arg == stt.text
    assert now == datetime.fromisoformat(RECORDED_AT)


def test_my_day_sets_my_day_date_to_recorded_day(client, auth, db, stt, parser):
    # 23:30 VN on the 18th is 16:30 UTC; the task belongs to My Day of the 18th.
    entry_id, resp = post_voice(client, auth, recorded_at="2026-10-18T16:30:00+00:00", my_day="true")
    assert resp.status_code == 200
    task = db.scalar(select(Task).where(Task.voice_entry_id == uuid.UUID(entry_id)))
    assert task.my_day_date == date(2026, 10, 18)


def test_without_my_day_flag_not_in_my_day(client, auth, db, stt, parser):
    post_voice(client, auth)
    assert db.scalar(select(Task.my_day_date)) is None


def test_timings_recorded(client, auth, db, stt, parser):
    entry_id, _ = post_voice(client, auth)
    entry = db.get(VoiceEntry, uuid.UUID(entry_id))
    assert entry.stt_ms is not None and entry.parse_ms is not None and entry.total_ms is not None
    assert entry.total_ms >= entry.stt_ms


def test_resend_same_entry_id_is_idempotent(client, auth, db, stt, parser):
    entry_id, first = post_voice(client, auth)
    _, second = post_voice(client, auth, entry_id=entry_id)

    assert second.status_code == 200
    assert second.json() == first.json()
    assert len(stt.calls) == 1 and len(parser.calls) == 1
    assert count(db, Task) == 1
    assert count(db, VoiceEntry) == 1


def test_resend_after_undo_returns_remaining_tasks(client, auth, db, stt, parser):
    entry_id, _ = post_voice(client, auth)
    db.execute(text("UPDATE tasks SET deleted_at = now()"))
    _, resp = post_voice(client, auth, entry_id=entry_id)
    assert resp.status_code == 200
    assert resp.json()["tasks"] == []
    assert len(stt.calls) == 1


def test_no_tasks_returns_422_with_raw_text(client, auth, db, stt, parser):
    stt.text, parser.tasks = "hôm nay trời đẹp quá", []
    entry_id, resp = post_voice(client, auth)

    assert resp.status_code == 422
    assert resp.json() == {"detail": "Không hiểu, nói lại nhé", "raw_text": "hôm nay trời đẹp quá"}
    assert db.get(VoiceEntry, uuid.UUID(entry_id)).status == "parsed"
    assert count(db, Task) == 0


def test_resend_after_422_does_not_call_openai_again(client, auth, stt, parser):
    stt.text, parser.tasks = "hôm nay trời đẹp quá", []
    entry_id, _ = post_voice(client, auth)
    _, resp = post_voice(client, auth, entry_id=entry_id)
    assert resp.status_code == 422
    assert resp.json()["raw_text"] == "hôm nay trời đẹp quá"
    assert len(stt.calls) == 1 and len(parser.calls) == 1


def test_invalid_rows_from_llm_are_dropped(client, auth, stt, parser):
    parser.tasks = [
        {"title": "  ", "due_date": None, "remind_at": None, "starred": False},
        {"title": "Đóng tiền điện", "due_date": "2026-02-30", "remind_at": "nonsense", "starred": False},
    ]
    _, resp = post_voice(client, auth)
    [task] = resp.json()["tasks"]
    assert (task["title"], task["due_date"], task["remind_at"]) == ("Đóng tiền điện", None, None)


def test_only_invalid_rows_returns_422(client, auth, stt, parser):
    parser.tasks = [{"title": "", "due_date": None, "remind_at": None, "starred": False}]
    _, resp = post_voice(client, auth)
    assert resp.status_code == 422


@pytest.mark.parametrize("stage", ["stt", "parse"])
def test_openai_failure_returns_502_and_marks_failed(client, auth, db, stt, parser, stage):
    fake = stt if stage == "stt" else parser
    fake.error = AIServiceError("APITimeoutError")
    entry_id, resp = post_voice(client, auth)

    assert resp.status_code == 502
    assert resp.json()["detail"] == "Lỗi xử lý giọng nói, sẽ thử lại"
    entry = db.get(VoiceEntry, uuid.UUID(entry_id))
    assert entry.status == "failed"
    assert stage in entry.error and "APITimeoutError" in entry.error
    assert count(db, Task) == 0


def test_resend_after_failure_reprocesses(client, auth, db, stt, parser):
    stt.error = AIServiceError("APIConnectionError")
    entry_id, _ = post_voice(client, auth)
    stt.error = None
    _, resp = post_voice(client, auth, entry_id=entry_id)

    assert resp.status_code == 200
    assert len(resp.json()["tasks"]) == 1
    entry = db.get(VoiceEntry, uuid.UUID(entry_id))
    assert entry.status == "parsed" and entry.error is None
    assert count(db, Task) == 1


def test_in_progress_entry_returns_409(client, auth, db, stt, parser):
    entry_id = uuid.uuid4()
    db.add(VoiceEntry(id=entry_id, recorded_at=datetime.now(timezone.utc), status="received"))
    db.flush()
    _, resp = post_voice(client, auth, entry_id=str(entry_id))
    assert resp.status_code == 409
    assert resp.json()["detail"] == "Đang xử lý, thử lại sau"
    assert stt.calls == []


def test_stale_in_progress_entry_is_reprocessed(client, auth, db, stt, parser):
    entry_id = uuid.uuid4()
    db.add(
        VoiceEntry(
            id=entry_id,
            recorded_at=datetime.now(timezone.utc),
            received_at=datetime.now(timezone.utc) - timedelta(minutes=10),
            status="transcribed",
        )
    )
    db.flush()
    _, resp = post_voice(client, auth, entry_id=str(entry_id))
    assert resp.status_code == 200
    assert len(stt.calls) == 1


@pytest.mark.parametrize("error", [None, AIServiceError("boom")])
def test_audio_file_deleted_after_transcribe(client, auth, stt, parser, error):
    stt.error = error
    post_voice(client, auth)
    [(path, existed)] = stt.calls
    assert existed, "audio must exist while transcribing"
    assert not path.exists(), "audio must be deleted right after transcribing"


def test_recorded_at_without_timezone_rejected(client, auth, stt, parser):
    _, resp = post_voice(client, auth, recorded_at="2026-10-19T14:30:00")
    assert resp.status_code == 422
    assert stt.calls == []


def test_invalid_entry_id_rejected(client, auth, stt, parser):
    _, resp = post_voice(client, auth, entry_id="not-a-uuid")
    assert resp.status_code == 422


def test_empty_audio_rejected(client, auth, db, stt, parser):
    _, resp = post_voice(client, auth, audio=b"")
    assert resp.status_code == 422
    assert resp.json()["detail"] == "File ghi âm rỗng"
    assert count(db, VoiceEntry) == 0


def test_oversized_audio_rejected(client, auth, db, stt, parser, monkeypatch):
    from app.config import get_settings

    monkeypatch.setattr(get_settings(), "max_upload_bytes", 10)
    _, resp = post_voice(client, auth, audio=b"x" * 11)
    assert resp.status_code == 422
    assert resp.json()["detail"] == "File ghi âm quá lớn"
    assert count(db, VoiceEntry) == 0
