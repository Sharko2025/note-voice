import uuid
from datetime import datetime, timezone

import pytest
from alembic import command
from sqlalchemy import inspect, text
from sqlalchemy.exc import IntegrityError

from tests.conftest import alembic_config

TASK_COLUMNS = {
    "id", "voice_entry_id", "title", "note", "starred", "due_date", "remind_at",
    "my_day_date", "completed_at", "deleted_at", "created_at", "updated_at",
}


def test_tables_exist(engine):
    names = set(inspect(engine).get_table_names())
    assert {"voice_entries", "tasks", "notes"} <= names
    assert {c["name"] for c in inspect(engine).get_columns("tasks")} == TASK_COLUMNS


def test_partial_index_on_due_date(engine):
    idx = [i for i in inspect(engine).get_indexes("tasks") if i["column_names"] == ["due_date"]]
    assert idx, "missing index on tasks(due_date)"
    assert "deleted_at IS NULL" in idx[0]["dialect_options"]["postgresql_where"]


def _insert_entry(db) -> uuid.UUID:
    entry_id = uuid.uuid4()
    db.execute(
        text("INSERT INTO voice_entries (id, recorded_at, status) VALUES (:id, :at, 'parsed')"),
        {"id": entry_id, "at": datetime.now(timezone.utc)},
    )
    return entry_id


def _insert_task(db, entry_id=None, title="Mua sữa"):
    db.execute(text("INSERT INTO tasks (voice_entry_id, title) VALUES (:e, :t)"), {"e": entry_id, "t": title})


def test_task_defaults(db):
    _insert_task(db)
    row = db.execute(text("SELECT * FROM tasks")).one()
    assert row.id is not None and row.created_at is not None and row.updated_at is not None
    assert row.starred is False
    assert row.due_date is None and row.remind_at is None and row.completed_at is None and row.deleted_at is None


def test_empty_title_rejected(db):
    with pytest.raises(IntegrityError):
        with db.begin_nested():
            _insert_task(db, title="")


def test_deleting_entry_cascades_to_tasks(db):
    entry_id = _insert_entry(db)
    _insert_task(db, entry_id)
    db.execute(text("DELETE FROM voice_entries WHERE id = :id"), {"id": entry_id})
    assert db.execute(text("SELECT count(*) FROM tasks")).scalar() == 0


def test_downgrade_then_upgrade_is_clean(engine):
    cfg = alembic_config()
    command.downgrade(cfg, "base")
    assert not {"tasks", "notes"} & set(inspect(engine).get_table_names())
    command.upgrade(cfg, "head")
    assert {"tasks", "notes"} <= set(inspect(engine).get_table_names())


NOTE_COLUMNS = {"id", "voice_entry_id", "text", "label", "author", "starred", "deleted_at", "created_at", "updated_at"}


def _insert_note(db, entry_id=None, text_="Ý tưởng", label=None):
    cols, vals = "voice_entry_id, text", ":e, :t"
    if label is not None:
        cols, vals = cols + ", label", vals + ", :l"
    db.execute(text(f"INSERT INTO notes ({cols}) VALUES ({vals})"), {"e": entry_id, "t": text_, "l": label})


def test_notes_table(engine):
    assert {c["name"] for c in inspect(engine).get_columns("notes")} == NOTE_COLUMNS


def test_note_defaults(db):
    _insert_note(db)
    row = db.execute(text("SELECT * FROM notes")).one()
    assert row.id is not None and row.created_at is not None and row.updated_at is not None
    assert (row.label, row.author, row.starred, row.deleted_at) == ("note", None, False, None)


@pytest.mark.parametrize("bad", [{"text_": ""}, {"label": "task"}])
def test_note_constraints(db, bad):
    with pytest.raises(IntegrityError):
        with db.begin_nested():
            _insert_note(db, **bad)


def test_deleting_entry_cascades_to_notes(db):
    entry_id = _insert_entry(db)
    _insert_note(db, entry_id)
    db.execute(text("DELETE FROM voice_entries WHERE id = :id"), {"id": entry_id})
    assert db.execute(text("SELECT count(*) FROM notes")).scalar() == 0
