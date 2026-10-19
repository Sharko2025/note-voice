"""initial schema

Revision ID: 0001
Revises:
Create Date: 2026-10-19
"""
from alembic import op

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    # DDL mirrors SPEC.md "Schema DB".
    op.execute("""
        CREATE TABLE voice_entries (
          id           UUID PRIMARY KEY,
          recorded_at  TIMESTAMPTZ NOT NULL,
          received_at  TIMESTAMPTZ DEFAULT now(),
          raw_text     TEXT,
          status       TEXT NOT NULL,
          error        TEXT,
          stt_ms       INT,
          parse_ms     INT,
          total_ms     INT
        )
    """)
    op.execute("""
        CREATE TABLE tasks (
          id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
          voice_entry_id  UUID REFERENCES voice_entries(id) ON DELETE CASCADE,
          title           TEXT NOT NULL CONSTRAINT tasks_title_check CHECK (title <> ''),
          note            TEXT,
          starred         BOOLEAN NOT NULL DEFAULT FALSE,
          due_date        DATE,
          remind_at       TIMESTAMPTZ,
          my_day_date     DATE,
          completed_at    TIMESTAMPTZ,
          deleted_at      TIMESTAMPTZ,
          created_at      TIMESTAMPTZ DEFAULT now(),
          updated_at      TIMESTAMPTZ DEFAULT now()
        )
    """)
    op.execute("CREATE INDEX tasks_due_date_idx ON tasks (due_date) WHERE deleted_at IS NULL")


def downgrade() -> None:
    op.execute("DROP TABLE tasks")
    op.execute("DROP TABLE voice_entries")
