# Phase 3 — Nhắc việc

Trạng thái: **code xong Task 1–6, chờ thử trên Fold 8** · Loi duyệt 2026-10-20 (cả 4 quyết định theo đề xuất)

Mục tiêu: việc có giờ nhắc thì **đúng giờ đó** Fold 8 hiện notification (có tiếng, hiện nổi trên màn hình), kể cả khi app đã đóng, máy đang khóa hoặc vừa khởi động lại. Notification có 3 nút **Xong · Báo lại 10 phút · Mở**, đúng mockup v3 (màn "Reminders": notification shade và Cài đặt → Quyền).

Checklist SPEC tương ứng:

- [ ] Báo thức chính xác + notification có nút Xong, Báo lại sau 10 phút → Task 1–6
- [ ] Khởi động lại máy → báo thức vẫn kêu đúng giờ → Task 5, Task 8

**Không thuộc Phase 3:**
- Việc lặp lại, nhắc nhiều lần cho 1 việc, chọn thời gian báo lại khác 10 phút (ngoài MVP / đã chốt cố định 10 phút).
- Push từ server (SPEC: báo thức chạy hoàn toàn trên máy).
- APK đã ký: Phase 4.

**Backend:** không cần sửa. Xong = `PATCH completed=true`, Báo lại = `PATCH remind_at`, đều là endpoint đã có.

---

## Quyết định cần Loi chốt

1. **Quyền báo thức chính xác.** SPEC ghi `SCHEDULE_EXACT_ALARM`. Từ Android 14, quyền này **mặc định bị tắt** với app mới cài: anh phải vào Settings → Apps → Note Cá Mập → "Alarms & reminders" bật tay, và có thể bị tắt lại. Quyền `USE_EXACT_ALARM` thì **tự có khi cài**, không cần bật; Google chỉ giới hạn nó với app đưa lên Play Store, mà app này không lên Store. Đề xuất: **dùng `USE_EXACT_ALARM`** (sửa dòng Quyền trong SPEC). Cài đặt vẫn hiện dòng "Báo thức chính xác: Bật/Tắt" như mockup để kiểm tra.
2. **Nhắc bị lỡ khi máy tắt.** Ví dụ nhắc 9:00 mà 8:50–9:30 máy hết pin. SPEC chỉ nói đặt lại báo thức cho giờ *trong tương lai*, nên 9:00 sẽ bị bỏ qua luôn. Đề xuất: **khi máy bật lại, nhắc ngay những việc bị lỡ trong 24 giờ qua** (mỗi việc 1 lần, chưa xong). Lỡ quá 24 giờ thì thôi, việc vẫn hiện đỏ "quá hạn" trong danh sách.
3. **Kiểu báo.** Đề xuất: notification **ưu tiên cao**: có tiếng thông báo mặc định + rung, hiện nổi (heads-up), hiện trên màn hình khóa. **Không** reo liên tục kiểu đồng hồ báo thức toàn màn hình. Anh đổi được tiếng trong Settings của Android (kênh "Nhắc việc").
4. **Chạm notification / nút Mở** → mở app và mở luôn chi tiết việc đó (bottom sheet khi gập, cột phải khi mở máy).

## Nguyên tắc kỹ thuật

- `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP)` (SPEC). Mỗi việc 1 báo thức, `requestCode` theo id việc, `PendingIntent` mang `taskId` + `remindAt` để bỏ qua báo thức cũ (đã đổi giờ).
- **Một chỗ duy nhất tính báo thức:** `ReminderPlanner` (logic thuần, test JVM). Đầu vào: danh sách việc trong cache + các thao tác Xong/Báo lại chưa lên server + giờ hiện tại + danh sách đã báo. Đầu ra: báo thức cần đặt, cần hủy, việc bị lỡ cần báo ngay. Mọi nơi (tải danh sách, sửa việc, khởi động máy…) chỉ gọi `ReminderScheduler.sync()`.
- **Không bao giờ để báo thức của việc đã xong hoặc đã xóa** (CLAUDE.md): sau mỗi thay đổi `remind_at`/`completed_at`, xóa, Hoàn tác và sau khi tải danh sách đều `sync()`. `sync()` hủy cả những báo thức không còn trong kết quả (lưu danh sách id đã đặt).
- **Xong / Báo lại khi mất mạng:** làm ngay trên máy (hủy/đặt lại báo thức, cập nhật cache), ghi thao tác vào bảng Room `pending_task_patches`, `TaskPatchWorker` (WorkManager, cần mạng, thử lại) gửi `PATCH`. Lúc tải danh sách, cache từ server được **áp thêm** các thao tác đang chờ, để báo lại không bị giờ cũ trên server ghi đè.
- **Room 1 → 2:** thêm bảng `pending_task_patches` và `fired_reminders` bằng migration (không xóa DB, vì hàng đợi ghi âm nằm trong đó), có test migration.
- Đặt lại báo thức sau: `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED` (cài bản mới), `TIME_SET`, `TIMEZONE_CHANGED`.
- **Không log** tiêu đề việc. Log chỉ có id việc, giờ báo, kết quả.

## Test

- **JVM (TDD):** `ReminderPlanner`:
  - việc tương lai → đặt; đã xong / đã xóa / không giờ → không đặt, hủy cái cũ;
  - đổi giờ → hủy giờ cũ, đặt giờ mới;
  - Báo lại đang chờ → dùng giờ báo lại, không dùng giờ trên server;
  - Xong đang chờ → không đặt;
  - lỡ trong 24 giờ, chưa báo → báo ngay 1 lần; đã báo hoặc lỡ quá 24 giờ → không.
- **JVM:** chữ notification ("Nhắc lúc 16:00 · Hôm nay" / "Reminder at 16:00 · Today", có ngày khi không phải hôm nay) VI/EN.
- **Robolectric:** `ReminderScheduler` với `ShadowAlarmManager` (đặt đúng giờ, hủy đúng cái, `requestCode` theo id); `ReminderReceiver` hiện notification có 3 nút, bỏ qua báo thức cũ; nút Xong / Báo lại ghi `pending_task_patches` + đặt báo thức +10 phút; `TaskPatchWorker` + MockWebServer gửi đúng `PATCH`; migration Room 1 → 2.
- **Fold 8:** Task 8.

## Cấu trúc (mới / sửa)

```
android/app/src/main/java/com/sitpro/notevoice/
  reminder/  ReminderPlanner (thuần), ReminderScheduler, ReminderReceiver,
             ReminderActionReceiver (Xong / Báo lại), BootReceiver          # mới
  sync/      Workers: + TaskPatchWorker                                      # sửa
  data/      AppDatabase v2: + PendingTaskPatch, FiredReminder; TaskRepository áp patch đang chờ
  notify/    ResultNotifier: + kênh "Nhắc việc", ReminderNotifier
  ui/        SettingsScreen: nhóm "Quyền"; MainActivity: mở việc từ notification
```

---

## Task 1 — Manifest, kênh notification

- Quyền `USE_EXACT_ALARM` (hoặc `SCHEDULE_EXACT_ALARM` nếu Loi chọn giữ SPEC), `RECEIVE_BOOT_COMPLETED`.
- Receiver: `ReminderReceiver`, `ReminderActionReceiver`, `BootReceiver` (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`), không export trừ bộ lọc hệ thống.
- Kênh "Nhắc việc" / "Reminders": ưu tiên cao, tiếng + rung, hiện trên màn hình khóa.

## Task 2 — `ReminderPlanner` (TDD)

Hàm thuần `plan(tasks, pendingPatches, fired, scheduledIds, now) → Plan(schedule, cancel, fireNow)`. Viết toàn bộ test ở mục Test trước.

## Task 3 — Room v2, `pending_task_patches`, `fired_reminders`

- Migration 1 → 2 (AutoMigration), test bằng `MigrationTestHelper`.
- `TaskRepository.refresh()` / `observe()` áp các patch đang chờ lên cache (UI cũng thấy ngay việc đã Xong / giờ báo lại).

## Task 4 — `ReminderScheduler` + `ReminderReceiver` + notification

- `sync()`: đọc cache + patch + fired, gọi Planner, đặt/hủy báo thức, báo ngay các việc bị lỡ. Gọi trong coroutine nền (`goAsync` ở receiver).
- Receiver: kiểm tra việc vẫn còn, chưa xong, `remind_at` khớp → hiện notification: tiêu đề việc, "Nhắc lúc 16:00 · Hôm nay", 3 nút. Ghi `fired_reminders`.
- Notification id theo id việc (báo lại thì thay notification cũ, không chồng thêm).

## Task 5 — Xong / Báo lại / Mở + đồng bộ

- **Xong:** đóng notification, đánh dấu xong trong cache, hủy báo thức, ghi patch `completed=true`, xếp `TaskPatchWorker`.
- **Báo lại 10 phút:** đóng notification, đặt báo thức `bây giờ + 10 phút` **ngay trên máy**, ghi patch `remind_at`, xếp worker (SPEC).
- **Mở / chạm notification:** mở `MainActivity` với `taskId` → chọn việc đó.
- `TaskPatchWorker`: gửi lần lượt, xong thì xóa patch, lỗi mạng → thử lại; 404 (việc đã bị xóa) → bỏ patch.
- Gọi `sync()` sau: `refresh()`, kết quả ghi âm (`cacheNew`), sửa / xóa / Hoàn tác việc, Hoàn tác lần nói, `BootReceiver`.

## Task 6 — Cài đặt → Quyền

Theo mockup: nhóm **Quyền** với 2 dòng "Báo thức chính xác" và "Thông báo", mỗi dòng **Bật** (xanh) hoặc **Tắt — Mở cài đặt** (chạm để mở đúng màn Settings của Android). Thêm 1 dòng nhắc "Pin: Không hạn chế" nếu app đang bị tối ưu pin. Chữ VI + EN.

## Task 7 — Kiểm tra tổng

- Toàn bộ unit test Android qua (76 hiện có + test mới), backend không đổi.
- Cài lên Fold 8 (USB), giữ token hiện có (không `pm clear`).

## Task 8 — Thử trên Fold 8 (Loi + em)

1. Nói *"nhắc tôi lúc HH:MM uống nước"* (HH:MM = 3 phút nữa) → đóng app, khóa máy → đúng giờ có notification nổi, có tiếng, trên màn hình khóa.
2. Bấm **Báo lại 10 phút** → 10 phút sau kêu lại; server có `remind_at` mới; trong app hiện giờ mới.
3. Bấm **Xong** → việc hiện đã xong trong app và trên server.
4. Bấm **Mở** → app mở đúng chi tiết việc.
5. Đặt nhắc 3 phút nữa → sửa giờ sang 5 phút nữa → chỉ kêu lúc 5 phút. Đặt nhắc rồi xóa việc → không kêu.
6. Chế độ máy bay → khi notification kêu bấm **Xong** → tắt máy bay → server cập nhật.
7. **Khởi động lại máy** với 1 nhắc 5 phút nữa → vẫn kêu đúng giờ.
8. (Nếu Loi duyệt quyết định 2) Đặt nhắc 3 phút nữa → tắt máy 5 phút → bật lại → báo ngay 1 lần.
9. Ghi kết quả vào cuối file này, đánh dấu checklist SPEC.

---

## Những thứ cần Loi

1. Duyệt plan, nhất là 4 quyết định ở trên.
2. Cắm USB Fold 8 khi tới Task 7–8.
3. Kiểm tra **Settings → Apps → Note Cá Mập → Battery → Unrestricted** (SPEC: để báo thức đúng giờ).

## Ghi chú khi code

- Thay cho bảng `fired_reminders`: bảng `scheduled_reminders` (id việc → giờ báo app đã đặt). Báo thức đã kêu thì xóa khỏi bảng; còn trong bảng mà giờ đã qua (máy tắt) → nhắc bị lỡ. Cài mới / xóa dữ liệu thì bảng trống → không nhắc dồn các giờ cũ.
- Khi 1 báo thức kêu, hiện luôn các việc đến giờ trong 1 phút tới (Doze có thể giữ các báo thức khác của app lại vài phút).
- Notification nhắc đang hiện mà việc được đánh dấu xong / xóa trong app → tự gỡ.
- Test: 113 unit test Android (thêm `ReminderPlannerTest` 12, `TaskPatchQueueTest` 10, `ReminderSchedulerTest` 9, `MigrationTest` 1, chữ nhắc VI/EN).

## Nhật ký thử trên Fold 8 (2026-10-20)

Bản debug, USB. Battery Unrestricted (Loi đặt; `dumpsys deviceidle whitelist` có app). `dumpsys alarm`: báo thức của app là `RTC_WAKEUP`, `exactAllowReason=policy_permission` (USE_EXACT_ALARM có hiệu lực).

| Bước | Kết quả |
| --- | --- |
| 1. "Nhắc tôi lúc mười một giờ uống nước" → thoát app, khóa máy | ✅ báo thức 11:00 kêu lúc 11:00:04, notification có tiếng, 3 nút (Loi: "perfect") |
| 2. Báo lại 10 phút | ✅ bấm 11:00:17 → server `remind_at` 11:10:17 (gửi < 1 s), báo thức mới 11:10:17 trên máy |
| 3. Xong · 4. Mở · 5. Sửa giờ / xóa · 6. Máy bay · 7. Khởi động lại · 8. Lỡ khi tắt máy | ⏳ chưa thử: Loi rút máy để em làm tiếp Phase 4 |

Checklist SPEC Phase 3 chưa đánh dấu, chờ bước 3–8 (làm cùng lúc cài APK release, Phase 4 Task 2).
