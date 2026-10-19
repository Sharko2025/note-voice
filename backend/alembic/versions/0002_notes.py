"""notes: ideas, quotes and other non-task speech

Revision ID: 0002
Revises: 0001
Create Date: 2026-10-19
"""
from alembic import op

revision = "0002"
down_revision = "0001"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # DDL mirrors SPEC.md "Schema DB".
    op.execute("""
        CREATE TABLE notes (
          id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
          voice_entry_id  UUID REFERENCES voice_entries(id) ON DELETE CASCADE,
          text            TEXT NOT NULL CONSTRAINT notes_text_check CHECK (text <> ''),
          label           TEXT NOT NULL DEFAULT 'note'
                          CONSTRAINT notes_label_check CHECK (label IN ('idea', 'quote', 'note')),
          author          TEXT,
          starred         BOOLEAN NOT NULL DEFAULT FALSE,
          deleted_at      TIMESTAMPTZ,
          created_at      TIMESTAMPTZ DEFAULT now(),
          updated_at      TIMESTAMPTZ DEFAULT now()
        )
    """)


def downgrade() -> None:
    op.execute("DROP TABLE notes")
