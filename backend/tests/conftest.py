import os
import uuid
from datetime import datetime, timezone

import pytest
from alembic import command
from alembic.config import Config
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import Session

from app.config import get_settings
from app.db import get_db
from app.main import create_app
from app.models import Note, Task, VoiceEntry

TEST_DATABASE_URL = os.environ["TEST_DATABASE_URL"]


def alembic_config() -> Config:
    cfg = Config(os.path.join(os.path.dirname(__file__), "..", "alembic.ini"))
    cfg.set_main_option("sqlalchemy.url", TEST_DATABASE_URL)
    return cfg


@pytest.fixture(scope="session")
def engine():
    cfg = alembic_config()
    command.downgrade(cfg, "base")
    command.upgrade(cfg, "head")
    eng = create_engine(TEST_DATABASE_URL)
    yield eng
    eng.dispose()


@pytest.fixture
def db(engine):
    """Session inside an outer transaction that is rolled back after each test."""
    conn = engine.connect()
    trans = conn.begin()
    session = Session(bind=conn, join_transaction_mode="create_savepoint")
    yield session
    session.close()
    trans.rollback()
    conn.close()


@pytest.fixture
def app(db):
    application = create_app()
    application.dependency_overrides[get_db] = lambda: db
    return application


@pytest.fixture
def client(app):
    return TestClient(app)


@pytest.fixture
def auth():
    return {"Authorization": f"Bearer {get_settings().device_token}"}


class FakeTranscriber:
    def __init__(self, text="mua sữa cho con", error=None):
        self.text, self.error = text, error
        self.calls = []  # (path, existed_at_call_time)

    def transcribe(self, path):
        self.calls.append((path, path.exists()))
        if self.error:
            raise self.error
        return self.text


class FakeParser:
    def __init__(self, tasks=None, notes=None, error=None):
        self.tasks = tasks if tasks is not None else [
            {"title": "Mua sữa cho con", "due_date": None, "remind_at": None, "starred": False}
        ]
        self.notes = notes if notes is not None else []
        self.error = error
        self.calls = []  # (text, now, as_note)

    def parse(self, text, now, as_note=False):
        self.calls.append((text, now, as_note))
        if self.error:
            raise self.error
        return {"tasks": self.tasks, "notes": self.notes}


@pytest.fixture
def stt(app):
    from app.services.stt import get_transcriber

    fake = FakeTranscriber()
    app.dependency_overrides[get_transcriber] = lambda: fake
    return fake


@pytest.fixture
def parser(app):
    from app.services.parser import get_parser

    fake = FakeParser()
    app.dependency_overrides[get_parser] = lambda: fake
    return fake


@pytest.fixture
def make_task(db):
    def _make(title="Mua sữa", entry=None, deleted=False, **fields):
        task = Task(
            title=title,
            voice_entry_id=entry.id if entry else None,
            deleted_at=datetime.now(timezone.utc) if deleted else None,
            **fields,
        )
        db.add(task)
        db.flush()
        return task

    return _make


@pytest.fixture
def make_note(db):
    def _make(text="Làm app nhắc con uống nước", entry=None, deleted=False, **fields):
        note = Note(
            text=text,
            voice_entry_id=entry.id if entry else None,
            deleted_at=datetime.now(timezone.utc) if deleted else None,
            **fields,
        )
        db.add(note)
        db.flush()
        return note

    return _make


@pytest.fixture
def make_entry(db):
    def _make(status="parsed", **fields):
        entry = VoiceEntry(id=uuid.uuid4(), recorded_at=datetime.now(timezone.utc), status=status, **fields)
        db.add(entry)
        db.flush()
        return entry

    return _make
