import uuid
from datetime import datetime, timedelta, timezone

import pytest

from app.models import Note


def texts(client, auth, label=None):
    url = "/api/v1/notes" + (f"?label={label}" if label else "")
    resp = client.get(url, headers=auth)
    assert resp.status_code == 200
    return [n["text"] for n in resp.json()]


# ---------- list ----------

def test_list_newest_first_without_deleted(client, auth, make_note, make_entry):
    now = datetime.now(timezone.utc)
    make_note("Cũ nhất", created_at=now - timedelta(hours=2))
    entry = make_entry(received_at=now - timedelta(hours=1))
    make_note("Một", entry=entry, label="idea", created_at=now - timedelta(minutes=59))
    make_note("Hai", entry=entry, label="quote", author="Steve Jobs", created_at=now - timedelta(minutes=58))
    make_note("Đã xóa", deleted=True)
    make_note("Mới nhất", created_at=now)
    # Newer recordings/notes first, notes of one recording keep their spoken order.
    assert texts(client, auth) == ["Mới nhất", "Một", "Hai", "Cũ nhất"]


def test_list_filter_by_label(client, auth, make_note):
    make_note("Ý tưởng", label="idea")
    make_note("Câu hay", label="quote")
    make_note("Ghi chú")
    assert texts(client, auth, "idea") == ["Ý tưởng"]
    assert texts(client, auth, "quote") == ["Câu hay"]
    assert texts(client, auth, "note") == ["Ghi chú"]


def test_list_unknown_label_rejected(client, auth):
    assert client.get("/api/v1/notes?label=task", headers=auth).status_code == 422


def test_notes_not_in_task_views(client, auth, make_note):
    make_note()
    assert client.get("/api/v1/tasks?view=all", headers=auth).json() == []


def test_requires_token(client):
    assert client.get("/api/v1/notes").status_code == 401


# ---------- create ----------

def test_create_typed_note(client, auth, db):
    note_id = str(uuid.uuid4())
    resp = client.post("/api/v1/notes", headers=auth, json={"id": note_id, "text": "  Có công mài sắt  "})
    assert resp.status_code == 200
    body = resp.json()
    assert body["id"] == note_id
    assert (body["text"], body["label"], body["author"], body["starred"], body["voice_entry_id"]) == (
        "Có công mài sắt", "note", None, False, None,
    )
    assert body["created_at"] is not None


def test_create_quote_with_author(client, auth):
    body = {"id": str(uuid.uuid4()), "text": "Stay hungry", "label": "quote", "author": " Steve Jobs ", "starred": True}
    resp = client.post("/api/v1/notes", headers=auth, json=body).json()
    assert (resp["label"], resp["author"], resp["starred"]) == ("quote", "Steve Jobs", True)


def test_create_is_idempotent(client, auth, db):
    body = {"id": str(uuid.uuid4()), "text": "Một lần"}
    first = client.post("/api/v1/notes", headers=auth, json=body).json()
    second = client.post("/api/v1/notes", headers=auth, json={**body, "text": "Đổi rồi"}).json()
    assert second == first
    assert db.query(Note).count() == 1


@pytest.mark.parametrize("body", [
    {"text": "Thiếu id"},
    {"id": "not-a-uuid", "text": "x"},
    {"id": "00000000-0000-0000-0000-000000000001", "text": "   "},
    {"id": "00000000-0000-0000-0000-000000000001", "text": "x", "label": "task"},
    {"id": "00000000-0000-0000-0000-000000000001", "text": "x", "due_date": "2026-10-20"},
])
def test_create_validation(client, auth, body):
    assert client.post("/api/v1/notes", headers=auth, json=body).status_code == 422


# ---------- patch ----------

def test_patch_fields(client, auth, make_note):
    note = make_note()
    resp = client.patch(f"/api/v1/notes/{note.id}", headers=auth,
                        json={"text": " Câu mới ", "label": "quote", "author": "Bác Hồ", "starred": True})
    assert resp.status_code == 200
    body = resp.json()
    assert (body["text"], body["label"], body["author"], body["starred"]) == ("Câu mới", "quote", "Bác Hồ", True)


def test_patch_clear_author_and_keep_others(client, auth, make_note):
    note = make_note("Giữ nguyên", label="quote", author="Ai đó")
    body = client.patch(f"/api/v1/notes/{note.id}", headers=auth, json={"author": None}).json()
    assert (body["text"], body["label"], body["author"]) == ("Giữ nguyên", "quote", None)


@pytest.mark.parametrize("body", [{"text": ""}, {"text": None}, {"label": None}, {"label": "x"}, {"starred": None}, {"title": "x"}])
def test_patch_validation(client, auth, make_note, body):
    note = make_note()
    assert client.patch(f"/api/v1/notes/{note.id}", headers=auth, json=body).status_code == 422


def test_patch_deleted_or_missing_is_404(client, auth, make_note):
    note = make_note(deleted=True)
    for note_id in (note.id, uuid.uuid4()):
        resp = client.patch(f"/api/v1/notes/{note_id}", headers=auth, json={"text": "x"})
        assert resp.status_code == 404 and resp.json()["detail"] == "Không tìm thấy ghi chú"


# ---------- delete / restore / undo ----------

def test_delete_restore(client, auth, db, make_note):
    note = make_note("Xóa thử")
    assert client.delete(f"/api/v1/notes/{note.id}", headers=auth).status_code == 204
    assert client.delete(f"/api/v1/notes/{note.id}", headers=auth).status_code == 204
    assert db.get(Note, note.id).deleted_at is not None
    assert texts(client, auth) == []
    resp = client.post(f"/api/v1/notes/{note.id}/restore", headers=auth)
    assert resp.status_code == 200 and resp.json()["text"] == "Xóa thử"
    assert texts(client, auth) == ["Xóa thử"]


def test_delete_missing_is_404(client, auth):
    assert client.delete(f"/api/v1/notes/{uuid.uuid4()}", headers=auth).status_code == 404


def test_undo_voice_entry_removes_notes_too(client, auth, make_task, make_note, make_entry):
    entry = make_entry()
    make_task("Việc", entry=entry)
    make_note("Ghi chú của lần nói", entry=entry)
    make_note("Gõ tay")
    assert client.post(f"/api/v1/voice/{entry.id}/undo", headers=auth).status_code == 204
    assert texts(client, auth) == ["Gõ tay"]
    assert client.get("/api/v1/tasks", headers=auth).json() == []
