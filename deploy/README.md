# Deploy

Local dev and the server use the same `docker-compose.yml`. Locally, `deploy/.env` (gitignored) holds
`COMPOSE_FILE=docker-compose.yml;docker-compose.dev.yml`, which adds a code mount, `--reload`, the `samples/`
mount and the `notevoice_test` database. The server has no `deploy/.env`, so only `docker-compose.yml` is used.

The server already runs expense-voice (port 8000, its own compose project, nginx conf and certificate).
Nothing here touches it: compose project `note-voice`, API on `127.0.0.1:8001`, its own Postgres volume,
its own nginx `limit_req_zone` (`notecamap`) and log files.

## Local

```bash
cp .env.example .env                      # fill in real values
printf 'COMPOSE_FILE=docker-compose.yml;docker-compose.dev.yml\n' > deploy/.env   # use ':' on Linux/macOS
cd deploy && docker compose up -d --build
docker compose exec api alembic upgrade head
docker compose exec api pytest -m "not openai"
```

## Server (EC2 54.251.52.235) — first deploy

Run long steps inside `tmux new -s nv`. Steps marked ⚠ change shared server config: ask Loi first.

```bash
# 1. DNS
dig +short notecamap.s-itpro.com              # must print 54.251.52.235

# 2. Code + secrets (separate folder from ~/expense-voice)
git clone https://github.com/Sharko2025/note-voice.git ~/note-voice
cd ~/note-voice
cp .env.example .env && chmod 600 .env        # set DEVICE_TOKEN, OPENAI_*, POSTGRES_*, DATABASE_URL
#   DEVICE_TOKEN:      python3 -c "import secrets;print(secrets.token_urlsafe(32))"   (not the expense-voice one)
#   POSTGRES_PASSWORD: python3 -c "import secrets;print(secrets.token_urlsafe(24))"

# 3. Containers
cd deploy
docker compose up -d --build
docker compose exec api alembic upgrade head
curl -s 127.0.0.1:8001/api/v1/health          # ok
curl -s 127.0.0.1:8000/api/v1/health          # expense-voice still ok

# 4. Exposure check: API only on 127.0.0.1:8001, nothing on 5432
sudo ss -ltnp | grep -E ':800[01]|:5432'

# 5. ⚠ nginx
sudo cp nginx/notecamap.conf /etc/nginx/sites-available/notecamap.conf
sudo ln -s /etc/nginx/sites-available/notecamap.conf /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx

# 6. ⚠ SSL (adds the 443 block + 80 -> 443 redirect)
sudo certbot --nginx -d notecamap.s-itpro.com
```

## Update

```bash
# locally: git push
cd ~/note-voice && git pull
cd deploy && docker compose up -d --build && docker compose exec api alembic upgrade head
```

## Smoke test from outside

```bash
curl https://notecamap.s-itpro.com/api/v1/health
curl -H "Authorization: Bearer $DEVICE_TOKEN" \
  -F file=@samples/goi-anh-tuan.m4a -F entry_id=$(uuidgen) -F recorded_at=$(date -Iseconds) \
  https://notecamap.s-itpro.com/api/v1/voice
curl -H "Authorization: Bearer $DEVICE_TOKEN" "https://notecamap.s-itpro.com/api/v1/tasks?view=my_day"
```

## Backup / restore

`backup.sh` dumps Postgres, uploads to `$BACKUP_S3_URI`, keeps the newest 30. Cron is set up in Phase 4.

```bash
# restore
aws s3 cp "$BACKUP_S3_URI/notevoice-YYYYMMDD-HHMMSS.sql.gz" - | gunzip | \
  docker compose exec -T db psql -U notevoice -d notevoice
```
