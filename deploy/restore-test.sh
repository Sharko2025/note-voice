#!/usr/bin/env bash
# Restore drill (SPEC Phase 4: "thử khôi phục 1 lần"). Restores a backup into a throwaway Postgres container,
# never the live database, and compares row counts with the live one. Usage: restore-test.sh [backup.sql.gz]
set -euo pipefail

APP_DIR="${APP_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
BACKUP_DIR="${BACKUP_DIR:-$HOME/backups/note-voice}"
FILE="${1:-$(ls -1 "$BACKUP_DIR"/notevoice-*.sql.gz | sort | tail -n 1)}"
NAME=note-voice-restore-test

cd "$APP_DIR/deploy"
set -a; source ../.env; set +a

cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT
cleanup

# No published port, no volume: gone when the drill ends.
docker run -d --name "$NAME" -e POSTGRES_USER=restore -e POSTGRES_PASSWORD=restore -e POSTGRES_DB=restore postgres:16 >/dev/null
until docker exec "$NAME" pg_isready -U restore -d restore >/dev/null 2>&1; do sleep 1; done

gunzip -c "$FILE" | docker exec -i "$NAME" psql -q -v ON_ERROR_STOP=1 -U restore -d restore >/dev/null

COUNTS="select (select count(*) from tasks), (select count(*) from notes), (select count(*) from voice_entries),
               (select version_num from alembic_version)"
restored=$(docker exec "$NAME" psql -At -U restore -d restore -c "$COUNTS")
live=$(docker compose -f docker-compose.yml exec -T db psql -At -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "$COUNTS")

echo "backup:   $(basename "$FILE")"
echo "restored: $restored  (tasks|notes|voice_entries|alembic)"
echo "live:     $live"
if [[ "$restored" == "$live" ]]; then
  echo "restore ok"
else
  echo "restore differs (fine only if data changed after the backup was taken)"
fi
