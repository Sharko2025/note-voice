import uuid
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException, Query, Response
from sqlalchemy import and_, func, or_, select, true, update
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.orm import Session

from app import timeutil
from app.db import get_db
from app.models import Task, VoiceEntry
from app.schemas import TaskCreate, TaskOut, TaskPatch
from app.services.normalize import fill_due_date

router = APIRouter()

View = Literal["my_day", "important", "planned", "all"]


def _get_task(db: Session, task_id: uuid.UUID, include_deleted: bool = False) -> Task:
    task = db.get(Task, task_id)
    if task is None or (task.deleted_at is not None and not include_deleted):
        raise HTTPException(404, "Không tìm thấy việc")
    return task


def _view_filter(view: View, today):
    """SPEC "Định nghĩa các màn hình". Completed tasks are included; the app folds them into a group."""
    if view == "my_day":
        start, end = timeutil.day_range(today, today)
        open_ = and_(Task.completed_at.is_(None), or_(Task.my_day_date == today, Task.due_date <= today))
        done_today = and_(
            Task.completed_at >= start,
            Task.completed_at < end,
            or_(Task.my_day_date == today, Task.due_date <= today),
        )
        return or_(open_, done_today)
    if view == "important":
        return Task.starred.is_(True)
    if view == "planned":
        return Task.due_date.is_not(None)
    return true()


@router.get("/tasks", response_model=list[TaskOut])
def list_tasks(view: View = Query("all"), db: Session = Depends(get_db)):
    today = timeutil.today_vn()
    query = (
        select(Task)
        .outerjoin(VoiceEntry, Task.voice_entry_id == VoiceEntry.id)
        .where(Task.deleted_at.is_(None), _view_filter(view, today))
    )
    if view == "planned":
        query = query.order_by(Task.due_date, Task.remind_at.asc().nulls_last(), Task.created_at, Task.id)
    else:
        # Newest first, but tasks from one recording keep their spoken order.
        query = query.order_by(
            func.coalesce(VoiceEntry.received_at, Task.created_at).desc(), Task.created_at, Task.id
        )
    return [TaskOut.from_model(t, today) for t in db.scalars(query)]


@router.post("/tasks", response_model=TaskOut)
def create_task(body: TaskCreate, db: Session = Depends(get_db)):
    today = timeutil.today_vn()
    db.execute(
        pg_insert(Task)
        .values(
            id=body.id,
            title=body.title,
            note=(body.note or "").strip() or None,
            starred=body.starred,
            due_date=fill_due_date(body.due_date, body.remind_at),
            remind_at=body.remind_at,
            my_day_date=today if body.my_day else None,
        )
        .on_conflict_do_nothing(index_elements=[Task.id])  # resend of the same id returns the stored task
    )
    db.commit()
    return TaskOut.from_model(db.get(Task, body.id, populate_existing=True), today)


@router.patch("/tasks/{task_id}", response_model=TaskOut)
def patch_task(task_id: uuid.UUID, patch: TaskPatch, db: Session = Depends(get_db)):
    task = _get_task(db, task_id)
    today = timeutil.today_vn()
    changes = patch.model_dump(exclude_unset=True)
    if "my_day" in changes:
        task.my_day_date = today if changes.pop("my_day") else None
    if "completed" in changes:
        if not changes.pop("completed"):
            task.completed_at = None
        elif task.completed_at is None:
            task.completed_at = func.now()
    if "note" in changes:
        changes["note"] = (changes["note"] or "").strip() or None
    for key, value in changes.items():
        setattr(task, key, value)
    task.due_date = fill_due_date(task.due_date, task.remind_at)
    task.updated_at = func.now()
    db.commit()
    db.refresh(task)
    return TaskOut.from_model(task, today)


@router.delete("/tasks/{task_id}", status_code=204)
def delete_task(task_id: uuid.UUID, db: Session = Depends(get_db)):
    task = _get_task(db, task_id, include_deleted=True)
    if task.deleted_at is None:
        task.deleted_at = func.now()
        db.commit()
    return Response(status_code=204)


@router.post("/tasks/{task_id}/restore", response_model=TaskOut)
def restore_task(task_id: uuid.UUID, db: Session = Depends(get_db)):
    task = _get_task(db, task_id, include_deleted=True)
    task.deleted_at = None
    db.commit()
    db.refresh(task)
    return TaskOut.from_model(task, timeutil.today_vn())


@router.post("/voice/{entry_id}/undo", status_code=204)
def undo_voice_entry(entry_id: uuid.UUID, db: Session = Depends(get_db)):
    if db.get(VoiceEntry, entry_id) is None:
        raise HTTPException(404, "Không tìm thấy lần ghi âm")
    db.execute(
        update(Task)
        .where(Task.voice_entry_id == entry_id, Task.deleted_at.is_(None))
        .values(deleted_at=func.now())
    )
    db.commit()
    return Response(status_code=204)
