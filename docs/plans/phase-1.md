# Phase 1 — Backend + AI

Trạng thái: **hoàn thành** · duyệt 2026-10-19 · xong 2026-10-19

Mục tiêu: backend chạy trên `https://notecamap.s-itpro.com`, `curl` gửi audio thật → nhận đúng danh sách việc (tiêu đề, ngày hạn, giờ nhắc, sao); đủ mọi endpoint trong SPEC mục "API backend".

Checklist SPEC tương ứng:

- [x] Khởi tạo repo từ expense-voice, Docker Compose, Alembic migration → Task 1–3
- [x] `POST /voice`: transcribe + parse + lưu, idempotent theo `entry_id` → Task 4–7
- [x] Bộ test parse ở mục Pipeline AI đều qua → Task 6
- [x] Các endpoint tasks (4 view), health → Task 8
- [x] Deploy lên EC2, nginx + SSL, `curl` từ ngoài gửi audio ra kết quả đúng → Task 9–10

---

## Nguyên tắc kỹ thuật

Giữ nguyên như expense-voice (đã chạy ổn trên production):

- **Mọi thứ chạy trong Docker**, image `python:3.12-slim`.
- **SQLAlchemy 2.0 sync + psycopg 3**, endpoint dạng `def`.
- **Test chạy trên Postgres thật** (database `notevoice_test` trong cùng container `db`), mỗi test 1 transaction rồi rollback.
- **OpenAI tách sau 2 interface** `Transcriber` và `TaskParser`. Test thường dùng fake qua `app.dependency_overrides`; test `-m openai` gọi API thật.
- Client OpenAI: `timeout=20`, `max_retries=0` (app Android tự retry).
- Logging: 1 middleware ghi `method path status ms` (+ `entry_id` ở `/voice`). Không log body, header, token, `raw_text`, tiêu đề việc.
- **Đồng hồ giả trong test:** mọi chỗ cần "hôm nay" đi qua `timeutil.today_vn(now)` với `now` truyền vào được, để test cố định Thứ Hai 19/10/2026 14:30.

## Cấu trúc backend

```
backend/
  Dockerfile  pyproject.toml  requirements.txt  alembic.ini  .dockerignore     # copy
  alembic/env.py                                                                 # copy
  alembic/versions/0001_initial.py                                               # voice_entries (copy DDL) + tasks
  app/
    main.py                 # create_app(), middleware log, router /api/v1 — đổi tên
    config.py  db.py  auth.py                                                    # copy
    timeutil.py             # copy (VN_TZ, today_vn, day_range) + format_now_for_prompt()
    models.py               # VoiceEntry (copy) + Task
    schemas.py              # Task, TaskCreate, TaskPatch, VoiceResult
    services/
      errors.py             # copy
      stt.py                # copy, đổi STT_PROMPT
      parser.py             # copy OpenAIParser.parse; SYSTEM_PROMPT + JSON_SCHEMA mới
      normalize.py          # MỚI: hậu kiểm kết quả parse → danh sách việc hợp lệ
      voice.py              # copy process_voice/_claim/_replay/_transcribe; đổi bước lưu
    routers/
      health.py             # copy
      voice.py              # copy contract, thêm field my_day
      tasks.py              # từ transactions.py: CRUD, view, restore, undo
  tests/
    conftest.py             # copy fixtures + FakeParser, thêm fixture đồng hồ cố định
    test_health.py  test_auth.py  test_stt.py                                    # copy
    test_migration.py  test_normalize.py  test_voice.py  test_tasks.py
    test_parse_openai.py    # @pytest.mark.openai — 13 câu trong SPEC
    test_voice_openai.py    # @pytest.mark.openai — audio thật end-to-end
```

---

## Task 1 — Khởi tạo repo

1. Repo đã `git init` trên `main`. Copy `.gitignore` từ expense-voice (đã có `.env`, `*.jks`, `samples/`, build Android).
2. `.env.example` đúng SPEC mục `.env` (giá trị giả) + `TEST_DATABASE_URL`.
3. Commit đầu tiên: `SPEC.md`, `CLAUDE.md`, `docs/`, `.gitignore`, `.env.example`. Kiểm tra `git status` không có file bí mật.
4. **Chưa tạo / push** `github.com/Sharko2025/note-voice` cho tới khi Loi xác nhận.

## Task 2 — Copy skeleton + Docker Compose + health

- Copy các file "Copy as-is" của backend và deploy theo `docs/reuse-from-expense-voice.md`.
- Đổi tên: logger `note_voice`, FastAPI title `Note Cá Mập API`, compose `name: note-voice`, port `127.0.0.1:8001:8000`, `POSTGRES_DB=notevoice`, test DB `notevoice_test` (`initdb/create-test-db.sh`).
- `docker-compose.dev.yml`: mount `../backend` + `../samples`, `--reload`.
- Test trước (`test_health` copy): `GET /api/v1/health` → 200 `ok`, không cần token.
- Xong khi: `docker compose up -d --build` lên, `curl 127.0.0.1:8001/api/v1/health` → `ok`, và **expense-voice local vẫn chạy song song** ở 8000.

## Task 3 — Model + migration

- Test trước (`test_migration.py`): sau `alembic upgrade head` có 2 bảng `voice_entries`, `tasks` đúng cột như SPEC; `title = ''` bị CHECK chặn; xóa `voice_entries` cascade xóa `tasks`; `downgrade base` rồi `upgrade head` chạy sạch.
- Code: `models.py` khớp 100% DDL trong SPEC (kể cả partial index `WHERE deleted_at IS NULL`). `updated_at` cập nhật trong ORM (`onupdate=func.now()`).

## Task 4 — Auth

- Copy `auth.py` + `test_auth.py`: thiếu header / sai token → 401 `"Token không hợp lệ"`; đúng → qua. Gắn cho mọi router trừ health.

## Task 5 — Hậu kiểm kết quả parse (`normalize.py`)

Hàm thuần `normalize(items, recorded_at) -> list[TaskDraft]`, không gọi mạng — test kỹ bằng unit test:

- `title`: trim, viết hoa chữ đầu; rỗng → bỏ việc đó.
- `due_date`: không đúng `YYYY-MM-DD` hoặc ngày không tồn tại (`2026-02-30`) → `None`, giữ việc.
- `remind_at`: không đúng `YYYY-MM-DDTHH:MM` → `None`. Đúng → hiểu là giờ VN, đổi sang `TIMESTAMPTZ`.
- `remind_at` sớm hơn `recorded_at` → bỏ `remind_at`, giữ `due_date` (xem pending-decisions).
- Có `remind_at` mà `due_date` null → `due_date` = ngày VN của `remind_at` (SPEC mục Schema DB).
- **Không bao giờ tự thêm `remind_at`** khi LLM trả null.

## Task 6 — Pipeline AI (STT + parse)

- `stt.py`: giữ `OpenAITranscriber`, `language` theo `OPENAI_STT_LANGUAGE` (rỗng = tự nhận); `STT_PROMPT` đúng SPEC.
- `parser.py`: `SYSTEM_PROMPT` và `JSON_SCHEMA` **chép nguyên văn từ SPEC**; `{now}` thay bằng `timeutil.format_now_for_prompt(recorded_at)` → `Thứ Hai (Monday), 19/10/2026 14:30`. Giữ `OpenAIParser.parse` (strict json_schema, `temperature=0`).
- Test thường: `format_now_for_prompt` đúng thứ ở ranh giới ngày (23:30 giờ VN = 16:30 UTC vẫn là ngày VN đó).
- Test `-m openai` (`test_parse_openai.py`): 13 câu trong bảng SPEC với `now` cố định, kiểm số việc, `title` (chứa từ khóa), `due_date`, `remind_at`, `starred`. Chạy mỗi câu 3 lần để bắt kết quả không ổn định.
- Test `-m openai` (`test_voice_openai.py`): audio thật trong `samples/` qua `POST /voice` với `recorded_at` cố định.
- **Cần Loi:** ghi âm bằng Fold 8 theo `docs/recording-samples.md`, đặt vào `samples/`:

  | File | Câu nói |
  | --- | --- |
  | `goi-anh-tuan.m4a` | mai 9 giờ sáng gọi cho anh Tuấn |
  | `uong-thuoc.m4a` | nhắc tôi 4 giờ chiều uống thuốc |
  | `bao-cao-thu-6.m4a` | nộp báo cáo thứ 6, quan trọng |
  | `hai-viec.m4a` | mua sữa cho con, gửi email cho chị Lan chiều mai nhắc tôi |
  | `sieu-thi-chu-nhat.m4a` | chủ nhật đi siêu thị 9 rưỡi sáng |
  | `troi-dep.m4a` | hôm nay trời đẹp quá (→ 1 ghi chú) |
  | `y-tuong.m4a` | ý tưởng: làm app nhắc con uống nước (→ 1 ghi chú Ý tưởng) |
  | `cau-hay.m4a` | câu hay của Steve Jobs: stay hungry, stay foolish (→ 1 ghi chú Câu hay) |
  | `call-bank.m4a` | remind me to call the bank tomorrow at 3pm |
  | `milk-rent.m4a` | buy milk and pay rent on Friday, it's urgent |

## Task 7 — `POST /voice` + idempotency

Copy `services/voice.py` + `routers/voice.py` + `test_voice.py` từ expense-voice, đổi bước lưu:

- Request multipart: `file`, `entry_id`, `recorded_at` (bắt buộc có offset), `my_day` (bool, mặc định false).
- Luồng `_claim` / `_replay` / `_transcribe` / `STALE_AFTER` giữ nguyên: `parsed` → trả kết quả cũ; `parsed` 0 việc → 422 kèm `raw_text` cũ; `failed` → xử lý lại; đang xử lý → 409; kẹt quá 2 phút → xử lý lại. File tạm xóa trong `finally`.
- Bước lưu: `normalize()` → tạo `Task` theo thứ tự nói (`created_at = clock_timestamp()`), `voice_entry_id` = entry; `my_day=true` → `my_day_date` = ngày VN của `recorded_at`.
- Lỗi OpenAI → `status=failed`, 502. File rỗng hoặc > 10 MB → 422 trước khi gọi OpenAI.
- Response 200: `{"entry_id", "raw_text", "tasks": [Task]}`.
- Test trước (fake): như expense-voice (idempotency, 409, 502, stale, file tạm bị xóa, `stt_ms/parse_ms/total_ms`) + `my_day` đặt đúng `my_day_date` + `remind_at` lưu đúng UTC.

## Task 8 — Endpoint tasks

Schema `Task` trả về: `id, voice_entry_id, title, note, starred, due_date, remind_at, my_day, completed, completed_at, created_at, updated_at` (`my_day` = `my_day_date == hôm nay`).

- `GET /tasks?view=my_day|important|planned|all` — điều kiện đúng bảng SPEC mục "Định nghĩa các màn hình", bỏ xóa mềm, kèm việc đã xong (Ngày của tôi: chỉ việc xong hôm nay). Thiếu `view` → `all`; sai → 422.
- `POST /tasks` — body `id` (UUID app sinh), `title`, `due_date?`, `remind_at?`, `starred?`, `my_day?`, `note?`. Gửi lại cùng `id` → trả việc cũ, 200.
- `PATCH /tasks/{id}` — `title`, `note`, `starred`, `due_date`, `remind_at` (cho phép `null`), `my_day` (true → hôm nay, false → `NULL`), `completed` (true → `completed_at = now()`, false → `NULL`). Đặt `remind_at` không có `due_date` → điền `due_date` như normalize. Việc đã xóa → 404.
- `DELETE /tasks/{id}` → xóa mềm, 204; lần 2 vẫn 204. `POST /tasks/{id}/restore` → trả việc.
- `POST /voice/{entry_id}/undo` → xóa mềm mọi việc của lần nói, 204; không tồn tại → 404.
- Test trước cho từng view với dữ liệu mẫu giống mockup (quá hạn 17/10, hôm nay, mai, thứ 6, chủ nhật, 10/11, không hạn, đã xong hôm nay / hôm qua), ranh giới ngày VN (việc hạn hôm nay lúc 23:59 VN vẫn là hôm nay), và `my_day_date` hôm qua không còn trong Ngày của tôi.

## Task 9 — Chuẩn bị deploy (local)

- `deploy/nginx/notecamap.conf`: từ `expense-voice.conf`, `server_name notecamap.s-itpro.com`, `limit_req_zone ... zone=notecamap` (tên riêng, không trùng zone của expense-voice), log `/var/log/nginx/notecamap.*.log`, `proxy_pass http://127.0.0.1:8001`.
- `deploy/backup.sh`: đổi prefix file `notevoice-`, đọc `BACKUP_S3_URI`.
- `deploy/README.md`: các bước deploy từng lệnh.
- Chạy `pytest -m "not openai"` và `pytest -m openai` local đều qua.

## Task 10 — Deploy lên EC2 (**từng bước cần Loi xác nhận**)

1. **Cần Loi:** tạo A record `notecamap.s-itpro.com` → `54.251.52.235`; kiểm tra bằng `nslookup`.
2. SSH, trong `tmux`: clone repo vào thư mục riêng (không trong thư mục expense-voice), tạo `.env` (Loi cung cấp OpenAI key, sinh `DEVICE_TOKEN` mới), `docker compose up -d --build`, `alembic upgrade head`.
3. Kiểm tra `ss -ltnp`: API chỉ ở `127.0.0.1:8001`, không có 5432; expense-voice vẫn chạy.
4. **Hỏi Loi trước:** copy nginx conf, `nginx -t`, reload, `certbot --nginx -d notecamap.s-itpro.com`.
5. Từ máy local: `curl` health + gửi 8 file audio thật → kết quả đúng; gửi lại cùng `entry_id` → không trùng; ghi lại `stt_ms/parse_ms/total_ms`. Kiểm tra `https://expense-voice.s-itpro.com/api/v1/health` vẫn `ok`.
6. Đánh dấu checklist Phase 1 trong SPEC.md.

---

## Những thứ cần Loi

1. Duyệt plan này (đặc biệt: 3 dòng trong `docs/pending-decisions.md`).
2. OpenAI key: dùng lại key của expense-voice hay tạo key riêng cho project (đề xuất: key riêng, giới hạn chi tiêu tháng).
3. 8 file audio ghi bằng Fold 8 (Task 6).
4. Tạo DNS `notecamap.s-itpro.com` (Task 10).
5. Xác nhận trước khi tạo/push repo GitHub, và trước mỗi thao tác nginx/certbot trên server.

## Kết quả (2026-10-19)

- Test: 115 test nhanh + 52 test OpenAI thật (16 câu × 3 lần + 4 test khác) đều qua, chạy 3 lần liền không lỗi. 8 test audio thật đang skip vì chưa có file trong `samples/`.
- Prompt đã chỉnh so với bản SPEC đầu (đã cập nhật SPEC, **Loi duyệt 2026-10-19**, xem `docs/pending-decisions.md`): trường `source` cho từng việc, luật tách việc khi STT mất dấu phẩy, luật "không phải việc", luật giờ đã qua viết rõ hơn. Server dời giờ nhắc đã qua trong ngày sang ngày mai (LLM sai luật này ~1/3 lần).
- End-to-end bằng audio TTS (chưa có audio ghi trên Fold 8): câu ví dụ đầu SPEC, câu tiếng Anh và câu "trời đẹp" ra đúng; gửi lại cùng `entry_id` trả đúng kết quả cũ; Hoàn tác 204.
- Server EC2: code ở `~/note-voice` (đẩy qua SSH vào bare repo `~/note-voice.git`, remote `server`), `.env` riêng (`DEVICE_TOKEN` và mật khẩu Postgres mới, chmod 600), container chạy, API chỉ ở `127.0.0.1:8001`, không publish 5432; expense-voice vẫn `ok`.
- Thời gian trên EC2 (3 lần nói TTS): `stt_ms` 742–1363, `parse_ms` 978–1619, `total_ms` 2078–3034.
- nginx + SSL: Loi chạy 2026-10-19. Chứng chỉ Let's Encrypt hết hạn 2027-01-17 (certbot tự gia hạn).
- Production `https://notecamap.s-itpro.com` (2026-10-19): health OK, HTTP → HTTPS 301, thiếu token → 401, expense-voice vẫn OK. Gửi audio TTS từ máy local qua HTTPS: 200 trong 3,1 s (2 việc đúng ngày/giờ/sao), gửi lại cùng `entry_id` 0,3 s cùng kết quả, Hoàn tác 204. DB production không còn việc nào (đã hoàn tác hết).
- Còn mở: repo GitHub (xem pending-decisions); test audio thật khi Loi có file mẫu.
