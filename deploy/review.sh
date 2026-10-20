#!/usr/bin/env bash
# Phase 4 "dùng 1 tuần": what was said in the last N days (default 7) and what it became, to spot parse mistakes.
# Prints to the terminal of whoever runs it; nothing is logged. Usage: review.sh [days]
set -euo pipefail

APP_DIR="${APP_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
DAYS="${1:-7}"
[[ "$DAYS" =~ ^[0-9]+$ ]] || { echo "usage: review.sh [days]" >&2; exit 1; }

cd "$APP_DIR/deploy"
set -a; source ../.env; set +a

docker compose -f docker-compose.yml exec -T db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "
select to_char(v.recorded_at at time zone 'Asia/Ho_Chi_Minh', 'DD/MM HH24:MI') as said,
       v.status, v.total_ms as ms, v.raw_text as heard,
       (select string_agg(t.title
                || coalesce(' · ' || to_char(t.due_date, 'DD/MM'), '')
                || coalesce(' · 🔔' || to_char(t.remind_at at time zone 'Asia/Ho_Chi_Minh', 'DD/MM HH24:MI'), '')
                || case when t.starred then ' · ★' else '' end
                || case when t.deleted_at is not null then ' (xóa)' else '' end, E'\n' order by t.created_at, t.id)
          from tasks t where t.voice_entry_id = v.id) as tasks,
       (select string_agg(n.label || ': ' || n.text
                || case when n.deleted_at is not null then ' (xóa)' else '' end, E'\n' order by n.created_at, n.id)
          from notes n where n.voice_entry_id = v.id) as notes
  from voice_entries v
 where v.recorded_at > now() - make_interval(days => $DAYS)
 order by v.recorded_at"

docker compose -f docker-compose.yml exec -T db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "
select count(*) as entries,
       count(*) filter (where status = 'parsed') as parsed,
       round(avg(total_ms)) as avg_ms,
       percentile_disc(0.9) within group (order by total_ms) as p90_ms,
       count(*) filter (where total_ms > 3000) as over_3s
  from voice_entries
 where recorded_at > now() - make_interval(days => $DAYS)"
