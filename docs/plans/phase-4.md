# Phase 4 — Dùng thật

Trạng thái: **phần không cần Fold 8 đã xong; chờ Loi: cài APK, xác nhận cron, dùng 1 tuần** · Loi cho làm luôn không chờ duyệt plan (2026-10-20: "làm xong tới phase cuối của MVP, non stop… sẽ review sau"). Chỗ nào chưa có trong SPEC → chọn phương án an toàn nhất, ghi vào `docs/pending-decisions.md`.

Checklist SPEC tương ứng:

- [ ] Build APK đã ký, cài lên Fold 8 → Task 1–2 (cài: cần Fold 8)
- [ ] Backup hằng đêm chạy, thử khôi phục 1 lần → Task 3–4 (cron: cần Loi xác nhận)
- [ ] Dùng 1 tuần, ghi lại các câu parse sai để chỉnh prompt → Task 5 (Loi dùng, em xem lại)

---

## Task 1 — APK đã ký

- Keystore riêng `notevoice.jks` (alias `notevoice`, RSA 2048, 10000 ngày) tạo **1 lần** trên laptop, nằm ở `android/notevoice.jks`, mật khẩu trong `android/keystore.properties`. Cả hai đã có trong `.gitignore`, **không commit**. Loi cất 2 file này vào password manager: mất keystore thì không cài đè bản mới được (phải gỡ app, mất hàng đợi trên máy).
- `signingConfigs.release` đọc `keystore.properties` (hoặc biến môi trường); không có file thì `assembleRelease` báo lỗi rõ, không ký bằng key debug.
- Bản release: không có `app/src/debug/` (HTTP thường, `DebugVoiceReceiver`), URL mặc định HTTPS production. Không bật R8/minify (1 máy, tránh lỗi reflection của Retrofit/kotlinx.serialization; APK nhỏ không quan trọng).
- `versionCode` 2 / `versionName` 1.0.0.
- Kiểm: `apksigner verify --print-certs` đúng chứng chỉ `notevoice`, manifest bản release không có `networkSecurityConfig` và receiver debug.

## Task 2 — Cài lên Fold 8 (cần Loi, cắm USB)

Bản debug ký bằng key khác → Android không cho cài đè. Thứ tự:
1. Mở app debug, kiểm **Cài đặt → Hàng đợi = 0** (không còn bản ghi chờ gửi).
2. `adb uninstall com.sitpro.notevoice` → `adb install app-release.apk`.
3. Mở app, cho quyền micro + thông báo, nhập lại token (em nhập qua adb, không in ra), **Pin → Unrestricted** lại.
4. Thử nhanh: nói 1 câu, đặt 1 nhắc 2 phút nữa.

Hướng dẫn ghi vào `docs/fold8-setup.md` mục 3.

## Task 3 — Backup hằng đêm

Hiện trạng server (2026-10-20): không có `aws` CLI, `BACKUP_S3_URI` vẫn là giá trị mẫu, chưa có cron nào cho note-voice.

- `backup.sh`: `pg_dump` → `~/backups/note-voice/notevoice-YYYYMMDD-HHMMSS.sql.gz`, giữ **30 bản mới nhất** trên server. Nếu `BACKUP_S3_URI` đã đặt (khác `change-me`) **và** có `aws` → đẩy thêm lên S3, giữ 30 bản ở đó (như SPEC). Chạy được từ bất kỳ thư mục nào (`APP_DIR`, mặc định thư mục cha của script).
- Cron 02:00 hằng đêm (SPEC): **chưa cài**. CLAUDE.md: thay đổi cron trên server phải được Loi xác nhận. Lệnh cài sẵn trong `deploy/README.md`.
- Backup chỉ trên cùng máy EC2 không cứu được khi mất cả máy → ghi `pending-decisions.md`: cần bucket S3 + IAM (hoặc chỗ khác) để có bản ngoài máy.

## Task 4 — Thử khôi phục 1 lần

`restore-test.sh`: lấy bản backup mới nhất, khôi phục vào **1 container Postgres tạm** (`note-voice-restore-test`, không đụng DB thật, không publish port), so số dòng `tasks`, `notes`, `voice_entries` với DB thật, rồi xóa container tạm. Lệnh khôi phục thật (khi cần) ghi trong `deploy/README.md`.

## Task 5 — Dùng 1 tuần

- `deploy/review.sh [số ngày]`: in các lần nói trong N ngày (giờ, câu đã nghe, việc/ghi chú ra được, thời gian xử lý, đã Hoàn tác chưa) để Loi và em dò câu parse sai. Chỉ in ra terminal của người chạy, không ghi log.
- Tiêu chí MVP (SPEC) kiểm sau 1 tuần: dưới 3 s; ≥ 9/10 câu đúng; không mất lần nói; không trùng; không lỡ báo thức.
- Câu sai → thêm vào bảng test OpenAI → chỉnh prompt (quy trình đã có).

## Những thứ cần Loi

1. Xác nhận cài cron backup 02:00 (lệnh ở `deploy/README.md`).
2. Quyết bản backup ngoài máy (S3 bucket + IAM role/khóa, hay chỗ khác).
3. Cắm Fold 8 cho Task 2 và các bước thử còn lại của Phase 3.
4. Cất `notevoice.jks` + `keystore.properties` vào password manager.
5. Push GitHub các commit Phase 3–4.

## Nhật ký (2026-10-20)

- **APK:** `android/NoteCaMap-1.0.0.apk` (versionCode 2, 1.0.0, ~50 MB). `apksigner verify`: chứng chỉ `CN=Note Ca Map, O=S-ITPRO, C=VN`, SHA-256 `6100e49cfd1b50663364a5df53c2d9e02010b0e55e14068fe16442b62fe3d22b`. Manifest release: không `networkSecurityConfig`, không `DebugVoiceReceiver`, không debuggable; có `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `BootReceiver`. Keystore tạo bằng keytool, mật khẩu ngẫu nhiên 32 ký tự trong `keystore.properties` (không in ra, không commit). **Chưa cài lên Fold 8** (máy đã rút).
- **Cron (Loi xác nhận):** server pull `66a666a`, cài `0 2 * * * …/deploy/backup.sh >> ~/backups/note-voice/backup.log` (server giờ +07 → 02:00 giờ VN). Chạy thử bằng môi trường giống cron (`env -i`, PATH tối thiểu) → `backup ok notevoice-20261020-114816.sql.gz`. Đánh dấu checklist SPEC sau lần chạy 02:00 đầu tiên.
- **Backup:** chạy tay `backup.sh` trên server → `~/backups/note-voice/notevoice-20261020-110831.sql.gz` (3,7 KB, quyền 600). **Cron chưa cài** (chờ Loi xác nhận). Chưa có S3 (không có aws CLI, `BACKUP_S3_URI` là giá trị mẫu).
- **Khôi phục thử:** `restore-test.sh` trên server, bản trên → container tạm: `21|2|13|0002` (tasks|notes|voice_entries|alembic) **khớp** DB thật; container tạm đã xóa. Trên máy local cũng khớp (`25|8|12|0002`).
- **review.sh** chạy được trên server. Số liệu 13 lần nói đến giờ: trung bình `total_ms` 2854, p90 3374, **5/13 trên 3 s** (đa số là câu dài hoặc lần đầu sau lúc nghỉ). Theo dõi trong tuần dùng thật; nếu vẫn hay quá 3 s thì xem giảm thời gian parse (Phase 4, sau khi Loi duyệt).
