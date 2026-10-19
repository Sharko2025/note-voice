# CLAUDE.md — note-voice (Note Cá Mập)

App việc cần làm kiểu Microsoft To Do, thêm việc bằng giọng nói: mở app → nhấn micro → nói → việc hiện trong danh sách. Một người dùng (Loi), một thiết bị (Samsung Galaxy Z Fold 8). Không phát hành Play Store. Song ngữ Việt/English, mặc định tiếng Việt.

**Nguồn sự thật duy nhất: `SPEC.md`.** Đọc SPEC.md trước mọi việc. Code mâu thuẫn với SPEC → dừng lại và hỏi, không tự sửa SPEC.

**Tái dùng từ `../expense-voice`:** xem `docs/reuse-from-expense-voice.md` trước khi viết file mới. File có trong danh sách "Copy as-is" thì copy rồi đổi tên, không viết lại.

## Quy trình làm việc

1. Thiết kế đã chốt trong SPEC.md — không mở lại các quyết định trong mục "Quyết định đã chốt" trừ khi Loi yêu cầu.
2. UI Android theo đúng mockup đã duyệt (link trong SPEC mục Android app). Muốn khác mockup → hỏi trước.
3. Mỗi phase bắt đầu bằng kế hoạch trong `docs/plans/phase-N.md`, chờ Loi duyệt rồi mới code.
4. Code theo TDD: test trước, code sau.
5. Kiểm chứng trên dữ liệu thật (audio thật ghi từ điện thoại), không chỉ fixture.
6. Làm **đúng từng phase** theo mục "Thứ tự triển khai". Phase hiện tại xong hết checklist mới sang phase sau.
7. Gặp quyết định chưa có trong SPEC → ghi 1 dòng vào `docs/pending-decisions.md` (ngày · vấn đề · đề xuất), tiếp tục với phương án an toàn nhất, báo Loi cuối phiên.

**Ngoài phạm vi MVP — không làm dù thấy hữu ích:** Quick Settings tile, widget, việc lặp lại, nhiều danh sách, bước con, thêm/sửa việc khi offline, đồng bộ Microsoft To Do/Google Tasks/lịch, chia sẻ, nhiều người dùng, ghi âm từ màn hình khóa, phím bên.

## Cấu trúc repo

```
backend/    FastAPI, SQLAlchemy, Alembic, tests/
android/    Kotlin + Jetpack Compose (mở bằng Android Studio trên Windows)
deploy/     docker-compose.yml, nginx/notecamap.conf, backup.sh
docs/       plans/, pending-decisions.md, reuse-from-expense-voice.md
samples/    audio thật để test (không commit)
SPEC.md     spec MVP
```

## Lệnh thường dùng

```bash
# Backend (local) — API ở 127.0.0.1:8001
cd deploy && docker compose up -d --build
docker compose exec api alembic upgrade head
docker compose exec api pytest -m "not openai"        # test nhanh, không gọi OpenAI
docker compose exec api pytest -m openai              # test parse thật, tốn tiền — chỉ chạy khi đổi prompt

# Thử endpoint
curl -H "Authorization: Bearer $DEVICE_TOKEN" \
  -F file=@samples/goi-anh-tuan.m4a -F entry_id=$(uuidgen) -F recorded_at=$(date -Iseconds) \
  https://notecamap.s-itpro.com/api/v1/voice

# Android
./gradlew installDebug        # cài qua wireless debugging
./gradlew assembleRelease     # APK đã ký
```

## Quy ước bắt buộc

- **Thời gian:** `remind_at`, `completed_at`, `deleted_at` lưu `TIMESTAMPTZ` (UTC). `due_date`, `my_day_date` là `DATE` theo `Asia/Ho_Chi_Minh`. Mọi phép tính "hôm nay", "mai" theo giờ VN.
- **Thời gian tương đối khi parse:** luôn tính từ `recorded_at` của lần nói, không bao giờ từ giờ server.
- **Giờ nhắc:** người dùng không nói giờ và không nói "nhắc" → `remind_at = NULL`. Không để AI hay code đoán.
- **Idempotency:** `POST /voice` theo `entry_id`, `POST /tasks` theo `id` do app sinh — gửi lại không tạo bản ghi trùng.
- **Xóa:** luôn xóa mềm (`deleted_at`) để hỗ trợ Hoàn tác.
- **Đo thời gian:** mỗi `voice_entry` ghi `stt_ms`, `parse_ms`, `total_ms`.
- **Model OpenAI:** đọc từ biến môi trường (`OPENAI_STT_MODEL`, `OPENAI_PARSE_MODEL`, `OPENAI_STT_LANGUAGE`), không hardcode.
- **Ngôn ngữ:** mọi chữ hiển thị có cả VI và EN (test `StringsCompletenessTest`); lỗi trả từ API bằng tiếng Việt. Code, tên biến, commit message bằng tiếng Anh.
- **Báo thức:** sau mọi thay đổi `remind_at`/`completed_at` và sau khi tải danh sách, đặt lại báo thức trên máy; không bao giờ để báo thức của việc đã xong hoặc đã xóa.

## Bảo mật

- Không commit `.env`, keystore (`*.jks`), `keystore.properties`, `samples/`. Kiểm tra `.gitignore` trước commit đầu tiên.
- OpenAI key chỉ nằm trên server, không bao giờ xuất hiện trong app Android.
- `DEVICE_TOKEN` riêng, khác expense-voice.
- Không log nội dung audio, token, raw request body, `raw_text` hay tiêu đề việc. Log chỉ gồm `entry_id`/id, status, thời gian xử lý.
- So sánh token bằng `hmac.compare_digest`.
- File audio xóa ngay sau khi transcribe xong.

## Triển khai

- Server: EC2 Singapore `54.251.52.235`, domain `notecamap.s-itpro.com`. **Chung máy với expense-voice** — không đụng container, DB, nginx conf hay cron của expense-voice.
- App tự chứa trong Docker Compose (project name `note-voice`); Postgres riêng, không publish port 5432; API chỉ bind `127.0.0.1:8001`.
- Thao tác chạy lâu qua SSH (build image, restore DB) → chạy trong `tmux`.
- Không chạy lệnh thay đổi nginx/certbot/cron/DNS trên server khi chưa được Loi xác nhận. Không push GitHub khi chưa được xác nhận.

## Định nghĩa "xong" cho một task

- Test liên quan đều qua.
- Đã chạy thử với dữ liệu thật (curl với audio thật, hoặc trên Fold 8).
- Checklist tương ứng trong SPEC.md được đánh dấu.
- Không có TODO bỏ lửng; việc chưa xong ghi vào `docs/pending-decisions.md`.
