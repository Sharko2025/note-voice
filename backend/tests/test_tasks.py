import uuid
from datetime import date, datetime, time, timedelta, timezone

import pytest

from app.models import Task
from app.timeutil import VN_TZ, today_vn

TODAY = today_vn()
YESTERDAY = TODAY - timedelta(days=1)


def at_vn(day: date, hh: int, mm: int = 0) -> datetime:
    return datetime.combine(day, time(hh, mm), VN_TZ)


def titles(client, auth, view):
    resp = client.get(f"/api/v1/tasks?view={view}", headers=auth)
    assert resp.status_code == 200
    return [t["title"] for t in resp.json()]


@pytest.fixture
def mockup(make_task):
    """The sample data from the approved UI mockup, relative to the real today."""
    start_of_today = at_vn(TODAY, 0)
    rows = {
        "overdue": make_task("Đóng tiền điện", due_date=TODAY - timedelta(days=2)),
        "today": make_task("Gọi cho chủ nhà", due_date=TODAY, remind_at=at_vn(TODAY, 23, 0), my_day_date=TODAY),
        "starred_my_day": make_task("Mua sữa cho con", starred=True, my_day_date=TODAY),
        "friday": make_task("Nộp báo cáo quý", due_date=TODAY + timedelta(days=4), starred=True),
        "tomorrow": make_task("Gửi email cho chị Lan", due_date=TODAY + timedelta(days=1),
                              remind_at=at_vn(TODAY + timedelta(days=1), 15)),
        "later": make_task("Gia hạn hộ chiếu", due_date=TODAY + timedelta(days=22)),
        "no_date": make_task("Đặt lịch khám răng"),
        "done_today": make_task("Uống thuốc", my_day_date=TODAY, completed_at=start_of_today + timedelta(minutes=1)),
        "done_yesterday": make_task("Chuyển tiền nhà", my_day_date=YESTERDAY,
                                    completed_at=start_of_today - timedelta(minutes=1)),
        "old_my_day": make_task("Việc hôm qua", my_day_date=YESTERDAY),
        "deleted": make_task("Đã xóa", my_day_date=TODAY, starred=True, due_date=TODAY, deleted=True),
    }
    return rows


def test_my_day_view(client, auth, mockup):
    got = titles(client, auth, "my_day")
    assert sorted(got) == sorted(["Đóng tiền điện", "Gọi cho chủ nhà", "Mua sữa cho con", "Uống thuốc"])


def test_my_day_flag_only_for_today(client, auth, mockup):
    by_title = {t["title"]: t for t in client.get("/api/v1/tasks", headers=auth).json()}
    assert by_title["Mua sữa cho con"]["my_day"] is True
    assert by_title["Việc hôm qua"]["my_day"] is False


def test_important_view(client, auth, mockup):
    assert sorted(titles(client, auth, "important")) == sorted(["Mua sữa cho con", "Nộp báo cáo quý"])


def test_planned_view_sorted_by_due_date(client, auth, mockup):
    assert titles(client, auth, "planned") == [
        "Đóng tiền điện", "Gọi cho chủ nhà", "Gửi email cho chị Lan", "Nộp báo cáo quý", "Gia hạn hộ chiếu",
    ]


def test_all_view_has_everything_not_deleted(client, auth, mockup):
    got = titles(client, auth, "all")
    assert len(got) == 10 and "Đã xóa" not in got


def test_default_view_is_all(client, auth, mockup):
    assert len(client.get("/api/v1/tasks", headers=auth).json()) == 10


def test_unknown_view_rejected(client, auth):
    assert client.get("/api/v1/tasks?view=someday", headers=auth).status_code == 422


def test_newest_first_but_one_recording_keeps_spoken_order(client, auth, db, make_task, make_entry):
    make_task("Cũ", created_at=datetime.now(timezone.utc) - timedelta(hours=1))
    entry = make_entry(received_at=datetime.now(timezone.utc))
    make_task("Thứ nhất", entry=entry, created_at=datetime.now(timezone.utc) + timedelta(seconds=1))
    make_task("Thứ hai", entry=entry, created_at=datetime.now(timezone.utc) + timedelta(seconds=2))
    assert titles(client, auth, "all") == ["Thứ nhất", "Thứ hai", "Cũ"]


# ---------- POST /tasks ----------

def test_create_typed_task(client, auth, db):
    task_id = str(uuid.uuid4())
    resp = client.post("/api/v1/tasks", headers=auth, json={"id": task_id, "title": "  Đặt bàn sinh nhật mẹ ", "my_day": True})
    assert resp.status_code == 200
    body = resp.json()
    assert (body["id"], body["title"], body["my_day"], body["voice_entry_id"], body["starred"]) == (
        task_id, "Đặt bàn sinh nhật mẹ", True, None, False,
    )
    assert db.get(Task, uuid.UUID(task_id)).my_day_date == TODAY


def test_create_is_idempotent(client, auth, db):
    task_id = str(uuid.uuid4())
    client.post("/api/v1/tasks", headers=auth, json={"id": task_id, "title": "Một"})
    resp = client.post("/api/v1/tasks", headers=auth, json={"id": task_id, "title": "Khác"})
    assert resp.status_code == 200 and resp.json()["title"] == "Một"
    assert db.query(Task).count() == 1


def test_create_with_reminder_fills_due_date(client, auth):
    resp = client.post(
        "/api/v1/tasks", headers=auth,
        json={"id": str(uuid.uuid4()), "title": "Uống thuốc", "remind_at": "2026-10-19T16:30:00+00:00"},
    )
    assert resp.json()["due_date"] == "2026-10-19"  # 23:30 VN


@pytest.mark.parametrize("body", [
    {"title": "Thiếu id"},
    {"id": "x", "title": "Sai id"},
    {"id": str(uuid.uuid4()), "title": "   "},
    {"id": str(uuid.uuid4()), "title": "Giờ không múi giờ", "remind_at": "2026-10-19T09:00:00"},
    {"id": str(uuid.uuid4()), "title": "Trường lạ", "category": "x"},
])
def test_create_validation(client, auth, body):
    assert client.post("/api/v1/tasks", headers=auth, json=body).status_code == 422


# ---------- PATCH /tasks/{id} ----------

def patch(client, auth, task, **body):
    return client.patch(f"/api/v1/tasks/{task.id}", headers=auth, json=body)


def test_patch_fields(client, auth, make_task):
    task = make_task("Gọi cho chủ nhà")
    resp = patch(client, auth, task, title="Gọi chủ nhà", note="  hỏi hợp đồng ", starred=True, due_date="2026-10-23")
    body = resp.json()
    assert resp.status_code == 200
    assert (body["title"], body["note"], body["starred"], body["due_date"]) == (
        "Gọi chủ nhà", "hỏi hợp đồng", True, "2026-10-23",
    )


def test_patch_my_day_toggle(client, auth, db, make_task):
    task = make_task()
    assert patch(client, auth, task, my_day=True).json()["my_day"] is True
    assert db.get(Task, task.id).my_day_date == TODAY
    assert patch(client, auth, task, my_day=False).json()["my_day"] is False
    assert db.get(Task, task.id).my_day_date is None


def test_patch_complete_and_reopen(client, auth, make_task):
    task = make_task()
    done = patch(client, auth, task, completed=True).json()
    assert done["completed"] is True and done["completed_at"] is not None
    again = patch(client, auth, task, completed=True).json()
    assert again["completed_at"] == done["completed_at"]  # completing twice keeps the first time
    reopened = patch(client, auth, task, completed=False).json()
    assert reopened["completed"] is False and reopened["completed_at"] is None


def test_patch_completed_today_shows_in_my_day(client, auth, make_task):
    task = make_task("Uống thuốc", my_day_date=TODAY)
    patch(client, auth, task, completed=True)
    assert titles(client, auth, "my_day") == ["Uống thuốc"]


def test_patch_snooze_reminder(client, auth, make_task):
    task = make_task(due_date=TODAY, remind_at=at_vn(TODAY, 16))
    resp = patch(client, auth, task, remind_at="2026-10-19T09:10:00+00:00")
    assert datetime.fromisoformat(resp.json()["remind_at"]) == datetime(2026, 10, 19, 9, 10, tzinfo=timezone.utc)


def test_patch_reminder_without_due_date_fills_it(client, auth, make_task):
    task = make_task()
    resp = patch(client, auth, task, remind_at="2026-10-20T02:00:00+00:00")
    assert resp.json()["due_date"] == "2026-10-20"


def test_patch_clear_reminder_and_due_date(client, auth, make_task):
    task = make_task(due_date=TODAY, remind_at=at_vn(TODAY, 16), note="x")
    body = patch(client, auth, task, remind_at=None, due_date=None, note=None).json()
    assert (body["remind_at"], body["due_date"], body["note"]) == (None, None, None)


@pytest.mark.parametrize("body", [
    {"title": None}, {"title": "  "}, {"starred": None}, {"my_day": None}, {"completed": None},
    {"remind_at": "2026-10-19T09:00:00"}, {"voice_entry_id": None},
])
def test_patch_validation(client, auth, make_task, body):
    assert patch(client, auth, make_task(), **body).status_code == 422


def test_patch_deleted_or_missing_is_404(client, auth, make_task):
    assert patch(client, auth, make_task(deleted=True), title="x").status_code == 404
    resp = client.patch(f"/api/v1/tasks/{uuid.uuid4()}", headers=auth, json={"title": "x"})
    assert resp.status_code == 404 and resp.json()["detail"] == "Không tìm thấy việc"


# ---------- delete / restore / undo ----------

def test_delete_restore(client, auth, db, make_task):
    task = make_task()
    assert client.delete(f"/api/v1/tasks/{task.id}", headers=auth).status_code == 204
    assert client.delete(f"/api/v1/tasks/{task.id}", headers=auth).status_code == 204
    assert db.get(Task, task.id).deleted_at is not None
    assert titles(client, auth, "all") == []
    resp = client.post(f"/api/v1/tasks/{task.id}/restore", headers=auth)
    assert resp.status_code == 200 and resp.json()["title"] == "Mua sữa"
    assert titles(client, auth, "all") == ["Mua sữa"]


def test_delete_missing_is_404(client, auth):
    assert client.delete(f"/api/v1/tasks/{uuid.uuid4()}", headers=auth).status_code == 404


def test_undo_voice_entry(client, auth, make_task, make_entry):
    entry = make_entry()
    make_task("Một", entry=entry)
    make_task("Hai", entry=entry)
    make_task("Gõ tay")
    assert client.post(f"/api/v1/voice/{entry.id}/undo", headers=auth).status_code == 204
    assert titles(client, auth, "all") == ["Gõ tay"]


def test_undo_unknown_entry_is_404(client, auth):
    resp = client.post(f"/api/v1/voice/{uuid.uuid4()}/undo", headers=auth)
    assert resp.status_code == 404 and resp.json()["detail"] == "Không tìm thấy lần ghi âm"
