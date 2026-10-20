#!/usr/bin/env bash
# Nightly Postgres backup (SPEC: 02:00, keep 30). Cron, once Loi confirms (deploy/README.md):
#   0 2 * * * /home/ubuntu/note-voice/deploy/backup.sh >> /home/ubuntu/backups/note-voice/backup.log 2>&1
# Always keeps the newest 30 dumps on the server in $BACKUP_DIR. Also uploads to $BACKUP_S3_URI (keeping 30 there)
# when it is set to a real bucket and the aws CLI is installed.
set -euo pipefail

APP_DIR="${APP_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
BACKUP_DIR="${BACKUP_DIR:-$HOME/backups/note-voice}"
KEEP=30

cd "$APP_DIR/deploy"
set -a; source ../.env; set +a
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

STAMP=$(date +%Y%m%d-%H%M%S)
NAME="notevoice-${STAMP}.sql.gz"
TMP="$BACKUP_DIR/.${NAME}.part"
trap 'rm -f "$TMP"' EXIT

# Written to a .part file first, so a failed dump never looks like a good backup.
docker compose -f docker-compose.yml exec -T db \
  pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --clean --if-exists | gzip -9 > "$TMP"
gzip -t "$TMP"
chmod 600 "$TMP"
mv "$TMP" "$BACKUP_DIR/$NAME"

ls -1 "$BACKUP_DIR" | grep '^notevoice-.*\.sql\.gz$' | sort -r | tail -n +$((KEEP + 1)) |
  while read -r old; do rm -f "$BACKUP_DIR/$old"; done

where="local"
if [[ -n "${BACKUP_S3_URI:-}" && "$BACKUP_S3_URI" != *change-me* ]] && command -v aws >/dev/null; then
  aws s3 cp --only-show-errors "$BACKUP_DIR/$NAME" "${BACKUP_S3_URI%/}/$NAME"
  aws s3 ls "${BACKUP_S3_URI%/}/" | awk '{print $4}' | grep '^notevoice-.*\.sql\.gz$' | sort -r | tail -n +$((KEEP + 1)) |
    while read -r old; do aws s3 rm --only-show-errors "${BACKUP_S3_URI%/}/${old}"; done
  where="local+s3"
fi

echo "$(date -Iseconds) backup ok $NAME $(du -h "$BACKUP_DIR/$NAME" | cut -f1) $where"
