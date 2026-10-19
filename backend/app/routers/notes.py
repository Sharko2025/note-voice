import uuid

from fastapi import APIRouter, Depends, HTTPException, Query, Response
from sqlalchemy import func, select
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.orm import Session

from app.db import get_db
from app.models import Note, VoiceEntry
from app.schemas import Label, NoteCreate, NoteOut, NotePatch
from app.services.normalize import clean_author

router = APIRouter()


def _get_note(db: Session, note_id: uuid.UUID, include_deleted: bool = False) -> Note:
    note = db.get(Note, note_id)
    if note is None or (note.deleted_at is not None and not include_deleted):
        raise HTTPException(404, "Không tìm thấy ghi chú")
    return note


@router.get("/notes", response_model=list[NoteOut])
def list_notes(label: Label | None = Query(None), db: Session = Depends(get_db)):
    query = (
        select(Note)
        .outerjoin(VoiceEntry, Note.voice_entry_id == VoiceEntry.id)
        .where(Note.deleted_at.is_(None))
        # Newest first, but notes from one recording keep their spoken order (same as tasks).
        .order_by(func.coalesce(VoiceEntry.received_at, Note.created_at).desc(), Note.created_at, Note.id)
    )
    if label is not None:
        query = query.where(Note.label == label)
    return [NoteOut.from_model(n) for n in db.scalars(query)]


@router.post("/notes", response_model=NoteOut)
def create_note(body: NoteCreate, db: Session = Depends(get_db)):
    db.execute(
        pg_insert(Note)
        .values(
            id=body.id,
            text=body.text,
            label=body.label,
            author=clean_author(body.author),
            starred=body.starred,
        )
        .on_conflict_do_nothing(index_elements=[Note.id])  # resend of the same id returns the stored note
    )
    db.commit()
    return NoteOut.from_model(db.get(Note, body.id, populate_existing=True))


@router.patch("/notes/{note_id}", response_model=NoteOut)
def patch_note(note_id: uuid.UUID, patch: NotePatch, db: Session = Depends(get_db)):
    note = _get_note(db, note_id)
    changes = patch.model_dump(exclude_unset=True)
    if "author" in changes:
        changes["author"] = clean_author(changes["author"])
    for key, value in changes.items():
        setattr(note, key, value)
    note.updated_at = func.now()
    db.commit()
    db.refresh(note)
    return NoteOut.from_model(note)


@router.delete("/notes/{note_id}", status_code=204)
def delete_note(note_id: uuid.UUID, db: Session = Depends(get_db)):
    note = _get_note(db, note_id, include_deleted=True)
    if note.deleted_at is None:
        note.deleted_at = func.now()
        db.commit()
    return Response(status_code=204)


@router.post("/notes/{note_id}/restore", response_model=NoteOut)
def restore_note(note_id: uuid.UUID, db: Session = Depends(get_db)):
    note = _get_note(db, note_id, include_deleted=True)
    note.deleted_at = None
    db.commit()
    db.refresh(note)
    return NoteOut.from_model(note)
