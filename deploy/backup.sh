#!/usr/bin/env bash
# Nightly Postgres backup -> S3, keep the newest 30. Cron (Phase 4):
#   0 2 * * * /path/to/note-voice/deploy/backup.sh >> /var/log/note-voice-backup.log 2>&1
# Requires: aws CLI with write access to $BACKUP_S3_URI (e.g. s3://my-bucket/note-voice).
set -euo pipefail

cd "$(dirname "$0")"
set -a; source ../.env; set +a
: "${BACKUP_S3_URI:?BACKUP_S3_URI must be set in .env}"
KEEP=30

STAMP=$(date +%Y%m%d-%H%M%S)
FILE="/tmp/notevoice-${STAMP}.sql.gz"
trap 'rm -f "$FILE"' EXIT

docker compose -f docker-compose.yml exec -T db \
  pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --clean --if-exists | gzip -9 > "$FILE"
aws s3 cp --only-show-errors "$FILE" "${BACKUP_S3_URI}/notevoice-${STAMP}.sql.gz"

aws s3 ls "${BACKUP_S3_URI}/" | awk '{print $4}' | grep '^notevoice-.*\.sql\.gz$' | sort -r | tail -n +$((KEEP + 1)) |
  while read -r old; do aws s3 rm --only-show-errors "${BACKUP_S3_URI}/${old}"; done

echo "$(date -Iseconds) backup ok notevoice-${STAMP}.sql.gz"
