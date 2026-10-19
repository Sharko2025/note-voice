import uuid
from datetime import date, datetime

from sqlalchemy import Boolean, CheckConstraint, Date, DateTime, ForeignKey, Index, Integer, Text, func, text
from sqlalchemy import text as sql_text  # Note has a column named `text`
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship


class Base(DeclarativeBase):
    pass


class VoiceEntry(Base):
    __tablename__ = "voice_entries"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True)
    recorded_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    received_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), server_default=func.now())
    raw_text: Mapped[str | None] = mapped_column(Text)
    status: Mapped[str] = mapped_column(Text, nullable=False)  # received | transcribed | parsed | failed
    error: Mapped[str | None] = mapped_column(Text)
    stt_ms: Mapped[int | None] = mapped_column(Integer)
    parse_ms: Mapped[int | None] = mapped_column(Integer)
    total_ms: Mapped[int | None] = mapped_column(Integer)

    tasks: Mapped[list["Task"]] = relationship(back_populates="voice_entry")
    notes: Mapped[list["Note"]] = relationship(back_populates="voice_entry")


class Task(Base):
    __tablename__ = "tasks"
    __table_args__ = (
        CheckConstraint("title <> ''", name="tasks_title_check"),
        Index("tasks_due_date_idx", "due_date", postgresql_where=text("deleted_at IS NULL")),
    )

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, server_default=text("gen_random_uuid()")
    )
    voice_entry_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("voice_entries.id", ondelete="CASCADE")
    )  # NULL = typed
    title: Mapped[str] = mapped_column(Text, nullable=False)
    note: Mapped[str | None] = mapped_column(Text)
    starred: Mapped[bool] = mapped_column(Boolean, nullable=False, server_default=text("false"))
    due_date: Mapped[date | None] = mapped_column(Date)  # Vietnam calendar day
    remind_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    my_day_date: Mapped[date | None] = mapped_column(Date)  # day it was added to My Day
    completed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    created_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), server_default=func.now())
    updated_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now()
    )

    voice_entry: Mapped[VoiceEntry | None] = relationship(back_populates="tasks")


NOTE_LABELS = ("idea", "quote", "note")  # Ý tưởng | Câu hay | Ghi chú


class Note(Base):
    __tablename__ = "notes"
    __table_args__ = (
        CheckConstraint("text <> ''", name="notes_text_check"),
        CheckConstraint("label IN ('idea', 'quote', 'note')", name="notes_label_check"),
    )

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, server_default=sql_text("gen_random_uuid()")
    )
    voice_entry_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("voice_entries.id", ondelete="CASCADE")
    )  # NULL = typed
    text: Mapped[str] = mapped_column(Text, nullable=False)  # spoken words as said
    label: Mapped[str] = mapped_column(Text, nullable=False, server_default=sql_text("'note'"))
    author: Mapped[str | None] = mapped_column(Text)  # who said the quote
    starred: Mapped[bool] = mapped_column(Boolean, nullable=False, server_default=sql_text("false"))
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    created_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), server_default=func.now())
    updated_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now()
    )

    voice_entry: Mapped[VoiceEntry | None] = relationship(back_populates="notes")
