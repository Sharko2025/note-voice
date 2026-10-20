# Phase 2 — App Android cơ bản

Trạng thái: **xong** (thử trên Fold 8 2026-10-20) · Loi duyệt 2026-10-19 (cả 3 quyết định theo đề xuất)

Mục tiêu: app **Note Cá Mập** chạy trên Fold 8, đúng [mockup v3 đã duyệt](https://claude.ai/artifact/5wQsjDRmKu5ZYgYmVjN8Ch). Bốn danh sách việc và màn Ghi chú dùng được bằng tay và bằng giọng nói, ghi âm khi mất mạng không mất lần nào.

Checklist SPEC tương ứng:

- [x] 4 màn hình danh sách, thêm/sửa/xóa/hoàn thành/gắn sao bằng tay → Task 5–8
- [x] Màn Ghi chú: lọc nhãn, thêm/sửa/xóa/gắn sao bằng tay, ghi âm `as_note` → Task 9
- [x] Ghi âm trong app + hàng đợi Room + `UploadWorker` + kết quả kèm Hoàn tác → Task 10–12
- [x] Bật chế độ máy bay, nói 3 lần, tắt chế độ máy bay → cả 3 lên server, không trùng → Task 14

**Không thuộc Phase 2:**
- Báo thức nhắc việc và notification nhắc (Xong / Báo lại 10 phút) là Phase 3. Giờ nhắc vẫn đặt và sửa được trong app, chỉ chưa kêu.
- APK đã ký là Phase 4.

**Backend:** không cần sửa, dùng nguyên các endpoint hiện có (kể cả `/notes`).

---

## Task 0 — Chuẩn bị (Loi, khoảng 5 phút)

Máy đã ghép nối adb từ expense-voice (`docs/fold8-setup.md` bên đó; em sẽ copy sang repo này):

1. Tắt FortiClient VPN trên laptop. Bật **Wireless debugging** trên Fold 8.
2. Gửi em `IP:port` ở màn Wireless debugging chính. Em chạy `adb connect`.

## Nguyên tắc kỹ thuật (giống expense-voice)

- **Project:** package `com.sitpro.notevoice`, 1 module `app`, Gradle Kotlin DSL + version catalog. Dùng **cùng phiên bản thư viện** với expense-voice, bỏ Glance (không làm widget).
- **SDK:** `minSdk = targetSdk = compileSdk = 37` (Fold 8, Android 17). Chỉ 1 máy nên không viết code cho bản Android cũ.
- **Không dùng DI framework:** 1 `AppContainer` tạo Room, ApiService, các Repository và Scheduler.
- **Thư viện:** Compose Material 3, Room (KSP), WorkManager, Retrofit + OkHttp + kotlinx.serialization, `security-crypto`.
- **Giờ:** mọi ngày giờ tính và hiển thị theo `Asia/Ho_Chi_Minh`, ghi rõ zone. Giờ dạng 24h `09:00`. Ngày `T2, 20/10` (VI) / `Mon, Oct 20` (EN).
- **Màu cố định navy + cam**, sáng/tối, lấy token từ mockup (`--p-*`). Không dùng dynamic color.
- **Chữ hiển thị:** mọi chữ có VI và EN trong `Texts.kt` (test `StringsCompletenessTest`). Code tiếng Anh.
- **Không log** token, audio, `raw_text`, tiêu đề việc, nội dung ghi chú.

## Test

- **JVM unit test (TDD)** cho mọi logic thuần:
  - lọc và nhóm 4 màn hình;
  - format ngày giờ;
  - phát hiện im lặng;
  - phân loại kết quả upload;
  - chữ snackbar ("Đã thêm 1 việc, 1 ghi chú");
  - giới hạn chọn giờ nhắc (không cho quá khứ).
- **Robolectric + MockWebServer + Room in-memory:** `ApiService`, DAO, `UploadProcessor`, các Repository.
- **Thử trên Fold 8:** ghi âm, gập/mở máy, chế độ máy bay, thời gian phản hồi. Kết quả ghi vào cuối file này.

## Cấu trúc

```
android/app/src/main/java/com/sitpro/notevoice/
  NoteApp.kt, AppContainer.kt, MainActivity.kt
  audio/    Recorder, SilenceDetector                                  # copy
  data/     AppDatabase (PendingUpload, CachedTask, CachedNote, CacheMeta),
            TaskRepository, NoteRepository, SettingsStore              # SettingsStore copy
  sync/     UploadProcessor, Workers (UploadWorker, UndoWorker), ResultBus
  api/      ApiService, AuthInterceptor, Dto
  notify/   ResultNotifier, UndoReceiver                               # kênh nhắc việc: Phase 3
  ui/       AppTheme, Texts, AppLanguage, Format, TaskViews (lọc/nhóm),
            AppShell (drawer + top bar), TaskListScreen, TaskEditSheet, ReminderPicker,
            NoteListScreen, NoteEditSheet, RecordSheet, SettingsScreen, ViewModels
```

---

## Task 1 — Khởi tạo project

- Copy từ expense-voice: Gradle wrapper, `libs.versions.toml`, `build.gradle.kts` gốc, `gradle.properties`, `settings.gradle.kts`, `app/build.gradle.kts`. Đổi `namespace`/`applicationId` thành `com.sitpro.notevoice`, bỏ Glance.
- **Manifest:**
  - `MainActivity` là launcher **duy nhất**, nhãn luôn "Note Cá Mập".
  - `configChanges` cho gập/mở máy, để không mất bản ghi đang ghi.
  - Quyền `RECORD_AUDIO`, `POST_NOTIFICATIONS`, `INTERNET`, `ACCESS_NETWORK_STATE`, `VIBRATE`.
  - Có `UndoReceiver`.
  - Không có `RecordActivity`, tile, widget, `showWhenLocked`.
- **Icon:**
  - launcher dạng adaptive vector vẽ lại từ logo C: tờ note, vây cam, sóng xanh, nền navy, có bản monochrome;
  - icon notification: tờ note trắng, vây và dòng khoét rỗng (SPEC).
- **`AppTheme`** viết mới: 2 bảng màu cố định từ mockup, font hệ thống.
- **Xong khi:** `./gradlew testDebugUnitTest assembleDebug` qua, `installDebug` lên Fold 8 mở ra màn trống có đúng icon và tên.

## Task 2 — Chữ, ngôn ngữ, format

- Copy `AppLanguage.kt` và test. Mặc định tiếng Việt, đồng bộ với Settings → Apps → Language.
- **`Texts.kt`:** bảng VI + EN cho mọi chữ trong mockup v3, kèm `StringsCompletenessTest`.
- **`Format.kt` (TDD):**
  - ngày hạn `Hôm nay` / `Ngày mai` / `T6, 23/10` (EN `Today` / `Tomorrow` / `Fri, Oct 23`);
  - quá hạn → cờ tô đỏ;
  - giờ `09:00`;
  - thời điểm của ghi chú: `Hôm nay 14:30` / `Hôm qua 21:05` / `T7, 17/10`;
  - tiêu đề ngày của Ngày của tôi: `Thứ Hai, 19 tháng 10` / `Monday, October 19`.
- **Snackbar kết quả (TDD):** `Đã thêm 2 việc`, `Đã thêm 1 việc, 1 ghi chú`, `Đã lưu 1 ghi chú`, bản EN tương ứng, kèm câu đã nghe trong ngoặc kép.

## Task 3 — API client

- **`Dto.kt`:** `TaskDto`, `NoteDto`, `VoiceResultDto` (tasks + notes), `TaskCreate`/`TaskPatch`, `NoteCreate`/`NotePatch`. PATCH chỉ gửi trường có đổi, và gửi `null` khi xóa giá trị.
- **`ApiService`** đủ endpoint: `postVoice` (+ `my_day`, `as_note`), `undoVoice`, `/tasks` (GET/POST/PATCH/DELETE/restore), `/notes` (GET/POST/PATCH/DELETE/restore).
- Copy `AuthInterceptor` (Bearer) và `parseError` (422 có `raw_text`). Timeout OkHttp 30 s.
- **Test (MockWebServer):**
  - header đúng;
  - multipart có đủ `entry_id`, `recorded_at`, `my_day`, `as_note`;
  - parse 200, 422 có `raw_text`, 401;
  - PATCH xóa giờ nhắc gửi `"remind_at": null`.

## Task 4 — Room

- `PendingUpload(entryId PK, filePath, recordedAt, createdAt, myDay, asNote, attempts, lastError, blocked)`: thêm `myDay` và `asNote` so với expense-voice.
- `CachedTask`: mọi trường của `TaskDto`.
- `CachedNote`: mọi trường của `NoteDto`.
- `CacheMeta`: lưu "cập nhật lúc …".
- **Test DAO:** thêm/xóa pending; đếm pending dạng `Flow`; thay toàn bộ cache khi tải lại; xóa theo `voiceEntryId` khi Hoàn tác.

## Task 5 — Logic 4 màn hình (TDD, hàm thuần)

App tải **1 danh sách** `GET /tasks?view=all` vào Room rồi tự lọc ra 4 màn và số đếm trong menu, theo đúng bảng "Định nghĩa các màn hình" của SPEC:

| Màn hình | Điều kiện |
| --- | --- |
| Ngày của tôi | việc trong My Day hoặc có hạn ≤ hôm nay; việc đã xong chỉ hiện nếu xong hôm nay |
| Quan trọng | việc gắn sao |
| Đã lên kế hoạch | việc có hạn, nhóm Quá hạn / Hôm nay / Ngày mai / Tuần này / Sau này, xếp theo hạn rồi giờ nhắc |
| Tác vụ | tất cả |

- Việc chưa xong ở trên, việc đã xong vào nhóm "Đã hoàn thành" thu gọn.
- Thứ tự giống server: mới nhất trước, việc cùng một lần nói giữ thứ tự nói.
- Test dùng **đúng bộ dữ liệu mẫu** của mockup và của `test_tasks.py`, để app và server cho cùng kết quả.

Lý do không gọi server riêng cho từng màn: số đếm trong menu cần cả 4 màn cùng lúc, và lúc mất mạng vẫn xem được mọi màn.

## Task 6 — Repository + ViewModel

- **`TaskRepository`:**
  - `refresh()` tải lại toàn bộ;
  - `create`, `patch`, `delete`, `restore`: gọi API rồi cập nhật Room;
  - `removeVoiceEntry` khi Hoàn tác.
- **`NoteRepository`:** giống hệt, cho `/notes`.
- **Thêm/sửa bằng tay khi mất mạng** nằm ngoài MVP (SPEC). Lúc đó app báo "Không có mạng, thử lại" và giữ nguyên chữ anh vừa gõ, không mất.
- **Thêm việc gõ tay:** ở Ngày của tôi → `my_day = true`. Ở các màn khác → việc thường (xem "Quyết định cần Loi" số 2).
- **ViewModel (test bằng repository giả):**
  - màn đang mở;
  - danh sách đã lọc;
  - số đếm;
  - trạng thái tải / ngoại tuyến;
  - sự kiện snackbar (xóa → Hoàn tác).

## Task 7 — Khung app (menu bên + thanh trên)

- **Màn hình ngoài (cover):** `ModalNavigationDrawer` mở bằng ☰.
  - 4 mục việc, mỗi mục kèm số việc chưa xong;
  - vạch ngăn, rồi **Ghi chú** (số ghi chú);
  - vạch ngăn, rồi **Cài đặt**;
  - thẻ hàng đợi khi có bản ghi đang chờ.
- **Màn hình trong (mở máy, rộng ≥ 600 dp):**
  - menu cố định bên trái;
  - chi tiết việc mở ở **cột phải** thay vì bottom sheet (mockup "Unfolded").
- **Thanh trên:** tiêu đề màn, dòng ngày (riêng Ngày của tôi), nút **VI / EN**, menu ⋮. Dưới tiêu đề Ngày của tôi có đường sóng xanh.
- **Kéo xuống để tải lại.** Mất mạng → banner "Ngoại tuyến · cập nhật lúc 14:12".

## Task 8 — Danh sách việc + sửa việc

- **Mỗi dòng:** ô tròn hoàn thành · tiêu đề · dòng phụ (☀ Ngày của tôi khi ở màn khác, ngày hạn tô đỏ nếu quá hạn, 🔔 giờ nhắc, biểu tượng ghi chú) · ngôi sao. Chạm ô tròn hoặc sao → `PATCH` ngay.
- **Ô "Thêm việc"** ở dưới cùng. Khi đang gõ, nút micro đổi thành ✓.
- **`TaskEditSheet`** (bottom sheet; cột phải khi mở máy):
  - tiêu đề, Ngày của tôi, Nhắc tôi, Ngày hạn, ghi chú, xóa;
  - mỗi giá trị có × để xóa;
  - dòng "Tạo bằng giọng nói · 14:30" nếu việc đến từ giọng nói.
- **Nhắc tôi:**
  - chọn nhanh: Muộn hơn hôm nay / Ngày mai / Tuần sau;
  - hoặc **Chọn ngày & giờ**: lịch tháng, ngày quá khứ bị mờ, chip giờ 08:00 / 09:00 / 12:00 / 15:00 / 20:00, nút đồng hồ mở TimePicker đầy đủ;
  - không cho chọn thời điểm đã qua (TDD).
- **Xóa** → snackbar "Đã xóa · Hoàn tác" → `restore`.
- **Trống:** hình vây trên sóng, 1 dòng hướng dẫn, 2 câu nói mẫu theo ngôn ngữ.

## Task 9 — Màn Ghi chú

- **Chip lọc** Tất cả / Ý tưởng / Câu hay / Ghi chú (lọc trên máy).
- **Thẻ:**
  - nhãn màu: Ý tưởng cam 💡, Câu hay xanh ❝, Ghi chú xám;
  - thời điểm, sao;
  - nguyên văn tối đa 6 dòng; Câu hay chữ lớn hơn, có "— tác giả".
- **Ô "Thêm ghi chú"**, nhãn mặc định Ghi chú.
- **`NoteEditSheet`:** chọn nhãn, nội dung nhiều dòng, tác giả (khi nhãn Câu hay), sao, xóa (có Hoàn tác).
- **Micro ở màn này** gửi `as_note=true`.

## Task 10 — Ghi âm

- **Copy** `Recorder` và `SilenceDetector` cùng test: AAC/m4a 16 kHz mono, tự dừng sau 1,5 s im lặng, tối đa 30 s; 8 s đầu không có tiếng → hủy, báo "Không nghe thấy gì".
- **`RecordSheet`** (bottom sheet đè lên danh sách): chấm đỏ "Đang nghe…", đồng hồ `00:04 / 00:30`, thanh sóng âm theo biên độ thật, câu nói mẫu, nút **Hủy** / **Dừng**. Rung nhẹ khi bắt đầu.
- **Dừng:**
  1. lưu file `filesDir/pending/<entryId>.m4a`;
  2. tạo `PendingUpload` với `recordedAt` = lúc bắt đầu nói (offset +07:00), `myDay` = đang ở Ngày của tôi, `asNote` = đang ở Ghi chú;
  3. xếp `UploadWorker`;
  4. hiện dòng "⏳ Đang xử lý…" trên đầu danh sách.
- **Quyền micro:** chưa có quyền `RECORD_AUDIO` → xin ngay khi chạm micro lần đầu.

## Task 11 — Upload + kết quả

- **Copy `UploadProcessor` + `Workers`:** 1 unique work cho mỗi `entryId`, cần mạng, backoff từ 10 s, expedited. Cách xử lý mã HTTP giữ nguyên:
  - **200** → lưu việc và ghi chú vào Room, xóa file + pending.
  - **422 có `raw_text`** → xóa, báo "Không hiểu, nói lại nhé" kèm câu đã nghe, nút **Nói lại**.
  - **409 / 5xx / mất mạng** → thử lại, giữ file.
  - **401** → giữ, báo sai token, dừng tới khi bấm "Gửi lại hàng đợi".
- **`ResultBus`:**
  - app đang mở → snackbar trong app (việc mới tô cam nhạt, kèm câu đã nghe, **Hoàn tác**);
  - app đang đóng → notification kết quả kèm **Hoàn tác** (`ResultNotifier` + `UndoReceiver` copy từ expense-voice).
- **Hoàn tác** → `UndoWorker` gọi `POST /voice/{id}/undo` (thử lại khi mất mạng), xóa việc và ghi chú của lần nói đó khỏi Room.
- **Mở app / có mạng lại** → xếp lại work cho mọi `PendingUpload` còn đó.
- **Test (Robolectric + MockWebServer):** mỗi nhánh; file bị xóa đúng lúc; kết quả có cả việc lẫn ghi chú.

## Task 12 — Cài đặt

- URL máy chủ (mặc định `https://notecamap.s-itpro.com`), device token (ẩn ký tự), ngôn ngữ, số bản ghi đang chờ + **Gửi lại hàng đợi**, phiên bản.
- **Lưu** → gọi thử `GET /tasks?view=my_day` → "Kết nối OK" hoặc lỗi (sai token / không tới được máy chủ).
- Mục "Báo thức chính xác" trong mockup làm ở Phase 3, cùng báo thức.
- Lần đầu mở app chưa có token → mở thẳng Cài đặt.

## Task 13 — Cài lên Fold 8 lần đầu

- Copy `docs/fold8-setup.md` từ expense-voice, đổi tên app, domain, bỏ phần nút nguồn / tile / widget.
- `installDebug`, nhập token production qua adb (không in token ra màn hình), mở app.

## Task 14 — Kiểm chứng trên Fold 8

1. Mở app: icon, tên, màu sáng/tối, VI/EN, menu bên trên màn ngoài, 3 cột khi mở máy.
2. Gõ tay: thêm, sửa (tiêu đề, hạn, giờ nhắc, ghi chú), hoàn thành, gắn sao, xóa + Hoàn tác ở cả 4 màn. Thêm/sửa/xóa ghi chú.
3. Nói ở Ngày của tôi: "mai 9 giờ sáng gọi cho anh Tuấn, mua sữa cho con, quan trọng" → 2 việc vào đúng màn, snackbar có câu đã nghe, **Hoàn tác** xóa cả 2.
4. Nói một câu vừa có việc vừa có ý tưởng → việc vào danh sách, ý tưởng vào Ghi chú. Nói ở màn Ghi chú → luôn ra ghi chú.
5. Đo thời gian từ lúc ngừng nói tới khi việc hiện ra (mục tiêu dưới 3 s, Wi-Fi và 4G), so với `total_ms` trên server.
6. **Chế độ máy bay:** nói 3 lần → tắt chế độ máy bay → cả 3 lên server, kiểm DB không trùng.
7. Đóng app ngay sau khi nói → notification kết quả, Hoàn tác từ notification.
8. Gập/mở máy giữa lúc ghi âm → không mất bản ghi.
9. Ghi kết quả và các câu parse sai vào cuối file này, đánh dấu checklist SPEC.

---

## Quyết định (Loi chốt 2026-10-19)

1. **Lọc 4 màn trên máy** từ 1 danh sách tải về (Task 5), thay vì gọi server riêng cho từng màn. Có số đếm trong menu và xem được khi mất mạng. Đề xuất: **đồng ý**.
2. **Gõ việc ở Quan trọng / Đã lên kế hoạch:** Microsoft To Do tự gắn sao (Quan trọng) hoặc đặt hạn hôm nay (Đã lên kế hoạch). SPEC chỉ nói về Ngày của tôi. Đề xuất: **làm giống Microsoft To Do** cho việc gõ tay; việc nói thì chỉ theo `my_day` như SPEC.
3. **Kết quả ghi âm:** app đang mở → snackbar; app đã đóng → notification (SPEC mục Luồng hoạt động). Đề xuất: **đồng ý**.

## Những thứ cần Loi

1. Duyệt plan, nhất là 3 quyết định ở trên.
2. Task 0: bật Wireless debugging, gửi `IP:port`.
3. Thử trên máy ở Task 14 (em hướng dẫn từng bước).

## Nhật ký thử trên Fold 8 (2026-10-20)

SM-F971B, Android 17, cắm USB (`adb -s R5GL740VKFZ`), bản debug trỏ vào production `https://notecamap.s-itpro.com`. Token nhập qua adb, không in ra.

| Bước | Kết quả |
| --- | --- |
| 14.3 Nói "mai 9 giờ sáng gọi cho anh Tuấn, mua sữa cho con, quan trọng" × 3 | ✅ 3/3 ra 2 việc: Gọi cho anh Tuấn (mai 21/10, nhắc 09:00) + Mua sữa cho con ⭐, vào Ngày của tôi; rung, sóng âm, snackbar có câu đã nghe |
| 14.5 Thời gian (Wi-Fi) | ✅ `total_ms` 2155–3368 với câu ngắn (STT 1,0–1,5 s, parse 1,1–2,0 s); câu dài 3 việc 4497. Loi cảm nhận "tầm 3 giây" |
| 14.2 Gõ tay "Mua pin" | ✅ hiện ngay; nút micro ẩn khi gõ |
| 14.7 Đóng app ngay sau khi nói "chiều nay 5 giờ đón con" | ✅ notification "Đã thêm 1 việc" + Hoàn tác; Hoàn tác xóa việc (server: `deleted_at`) |
| 14.6 Máy bay: 1 lần nói "thứ 7 rửa xe, ý tưởng làm kệ sách cho con" | ✅ "1 bản ghi đã lưu, sẽ gửi khi có mạng"; tắt máy bay → tự gửi (ghi 09:11:46, server nhận 09:12:31), Rửa xe (T7 24/10) + ý tưởng vào Ghi chú, không trùng. (3 lần nói offline đã thử trên máy ảo) |
| 14.4 Câu vừa có việc vừa có ý tưởng | ✅ như trên |
| 14.8 Gập → mở máy giữa lúc ghi âm (câu dài) | ✅ không ngắt, ra 3 việc: Đi ngân hàng rút tiền (mai), Mua gạo, nước mắm và trứng, Gọi cho chị Lan (T6 23/10) |
| 14.1 VI/EN, 3 cột khi mở máy | ✅ Loi xác nhận |

Sửa trong lúc thử (commit 879574c):
- Lần mở đầu tiên hiện tiếng Anh (máy đặt tiếng Anh): Samsung không đẩy ngôn ngữ mặc định vào process đang chạy → `MainActivity.attachBaseContext` tự áp. Thử lại như mới cài: tiếng Việt; VI · EN đổi được hai chiều.
- Loi yêu cầu: **nút micro to giữa đáy** (76dp, vòng sáng, nổi trên thanh gõ; ẩn khi gõ) và **nút "Việc · Ghi chú"** cạnh VI · EN. Ghi vào SPEC "Quyết định đã chốt".
- Đóng bàn phím mà ô gõ còn focus → micro không hiện lại → bỏ focus khi bàn phím đóng.

## Nhật ký thử trên máy ảo (2026-10-19)

Máy ảo Android 17 (API 37, 1080×2400), app debug trỏ vào server local (`adb reverse`, `http://127.0.0.1:8001`), dữ liệu mẫu giống mockup. 76 unit test Android qua.

- ✅ Icon logo C, màn khởi động, xin quyền micro + thông báo lần đầu; chưa có token → mở thẳng Cài đặt; "Lưu và kiểm tra" → "Kết nối OK".
- ✅ Ngày của tôi, Quan trọng, Đã lên kế hoạch (5 nhóm), menu bên với số đếm, Ghi chú (chip lọc, thẻ nhãn màu, câu hay có tác giả): khớp mockup v3.
- ✅ Chi tiết việc: Ngày của tôi, Nhắc tôi (chọn nhanh: Muộn hơn hôm nay 20:00 lúc 16:54, Ngày mai T3 09:00, Tuần sau T2 09:00; lịch tháng làm mờ ngày qua, chip giờ đã qua bị tắt), Ngày hạn, ghi chú tự lưu khi đóng sheet → PATCH đúng.
- ✅ Gõ việc ở Quan trọng → tự gắn sao; đang gõ thì micro đổi thành ✓. Hoàn thành → vào "Đã hoàn thành". Xóa → snackbar "Đã xóa …" → Hoàn tác khôi phục.
- ✅ English + giao diện tối; màn rộng (giả lập mở máy) → 3 cột: menu | danh sách | chi tiết.
- ✅ Ghi âm: sheet "Đang nghe…" có đồng hồ + thanh sóng; 8 s im lặng → "Không nghe thấy gì" + Nói lại (máy ảo không có micro thật).
- ✅ Giọng thật (file `hai-viec.m4a`, `y-tuong.m4a` của Loi, đưa vào hàng đợi bằng lối vào debug): "Đang xử lý…" → 2 việc tô cam + snackbar "Đã thêm 2 việc" kèm câu đã nghe; ý tưởng → "Đã lưu 1 ghi chú"; Hoàn tác → server `undo` 204. Server local 3,1–4,4 s (gọi OpenAI từ laptop ở VN).
- ✅ Chế độ máy bay: 3 bản ghi → banner "Ngoại tuyến · cập nhật lúc …" + "3 bản ghi đã lưu, sẽ gửi khi có mạng" → tắt chế độ máy bay → cả 3 lên server (3 `entry_id` khác nhau, mỗi bản đúng số việc/ghi chú, không trùng).
- Đã sửa trong lúc thử: logo trong menu quá nhỏ; Back đóng menu / về Ngày của tôi thay vì thoát app; ô "Thêm việc" thiếu nhãn trợ năng; khi mất mạng vẫn hiện "Đang xử lý…" (NetworkMonitor báo offline trễ); "Không nghe thấy gì" thêm dòng giải thích + Nói lại như mockup.
