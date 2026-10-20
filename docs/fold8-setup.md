# Hướng dẫn cài đặt trên Galaxy Z Fold 8

App: tên dưới icon luôn là **Note Cá Mập**; trong app hiện **Note Cá Mập** (tiếng Việt) hoặc **Sharko Notes** (English) theo nút **VI · EN**.

Máy: SM-F971B · Android 17 · One UI 9 · giao diện tiếng Anh (tên menu bên dưới giữ nguyên tiếng Anh).
Máy đã bật Developer options và đã ghép nối adb với laptop từ expense-voice: chỉ cần làm bước 2b.

---

## 1. Bật Developer options (đã làm, chỉ khi đổi máy)

**Settings → About phone → Software information** → chạm 7 lần vào **Build number** → nhập mã khóa máy.

## 2. Kết nối máy với laptop qua Wi-Fi (Wireless debugging)

Điều kiện:
- Điện thoại và laptop **cùng mạng Wi-Fi**.
- **Tắt FortiClient VPN** trên laptop (VPN chặn mạng LAN → laptop không thấy điện thoại).

### 2a. Ghép nối (đã làm, chỉ khi đổi laptop)

1. **Settings → Developer options** → bật **Wireless debugging**.
2. Chạm vào chữ **Wireless debugging** → **Pair device with pairing code**.
3. Trên laptop: `adb pair <IP>:<port-trong-hộp-thoại> <mã-6-số>` → thấy `Successfully paired`.

### 2b. Kết nối (mỗi lần dùng)

1. Bật **Wireless debugging** (tự tắt khi đổi Wi-Fi hoặc khóa máy lâu).
2. Xem `IP address & Port` ở **màn hình Wireless debugging chính** (khác port lúc ghép nối) và gửi cho Claude Code.
3. Trên laptop:
   ```
   adb connect <IP>:<port>
   adb devices          # phải thấy dòng "... device"
   ```

Mất kết nối? Kiểm tra lần lượt: VPN đã tắt chưa → Wireless debugging còn bật không → port có đổi không.

## 3. Cài app

Bản dev (Claude Code chạy giúp):
```
cd android
./gradlew installDebug
```
**Bản dùng thật (APK đã ký, Phase 4):** `android/NoteCaMap-1.0.0.apk`, build bằng `./gradlew assembleRelease` (ký bằng `android/notevoice.jks` + `android/keystore.properties`, hai file **không commit**: cất vào password manager, mất thì không cài đè bản mới được).

Lần đầu chuyển từ bản dev sang bản thật (khác chữ ký nên Android không cho cài đè):
1. Mở app, xem **Cài đặt → Hàng đợi** phải là **0 bản ghi đang chờ** (bản ghi chưa gửi sẽ mất khi gỡ app).
2. Trên laptop (cắm USB):
   ```
   adb uninstall com.sitpro.notevoice
   adb install android/NoteCaMap-1.0.0.apk
   ```
   Không dùng adb: chép APK vào máy, mở bằng **My Files** → cài. Tạm tắt **Settings → Security and privacy → Auto Blocker**, cài xong bật lại.
3. Làm lại mục 4 (quyền, token) và mục 5 (Pin → Unrestricted).

Các lần sau (cùng keystore): `adb install -r android/NoteCaMap-<phiên bản>.apk`, giữ nguyên dữ liệu và token.

App chỉ có **1 icon**: **Note Cá Mập** (tờ note, vây cá mập cam, nền navy). Không có icon ghi âm riêng, không có nút nguồn / tile / widget (ngoài MVP).

## 4. Mở app lần đầu

1. Mở **Note Cá Mập** → cho phép **Microphone** và **Notifications**.
2. Màn **Cài đặt** hiện ra: địa chỉ máy chủ đã điền sẵn `https://notecamap.s-itpro.com`; nhập **Mã thiết bị** (device token).
   - Token nằm trên server: `grep DEVICE_TOKEN ~/note-voice/.env` (khác token của expense-voice). Claude Code có thể nhập hộ qua adb mà không in token ra.
3. Bấm **Lưu và kiểm tra** → phải thấy **Kết nối OK**, rồi bấm ← về **Ngày của tôi**.

## 5. Pin: để hàng đợi offline gửi đúng lúc

**Settings → Apps → Note Cá Mập → Battery → Unrestricted**.

Không đặt Unrestricted, Samsung có thể hoãn việc gửi lại các bản ghi lúc mất mạng (và ở Phase 3, báo thức nhắc việc).

## 6. Đổi ngôn ngữ của app

Bấm **VI · EN** trên thanh trên cùng, hoặc **Settings → Apps → Note Cá Mập → Language**. Mặc định: tiếng Việt.

## 7. Cách dùng hằng ngày

- **Nói:** nhấn nút micro cam ở góc dưới → nói, ví dụ *"mai 9 giờ sáng gọi cho anh Tuấn, mua sữa cho con, quan trọng"* → im lặng ~1,5 giây là tự dừng.
- **Kết quả:** việc mới hiện ngay trong danh sách (tô cam nhạt vài giây), snackbar "Đã thêm 2 việc" kèm câu đã nghe. Sai thì bấm **Hoàn tác**.
- **Ý tưởng / câu hay:** nói bình thường ở bất kỳ màn nào ("ý tưởng: …", "câu hay của …"), hoặc mở **Ghi chú** rồi nói: ở màn Ghi chú, mọi thứ đều thành ghi chú.
- **Nói ở Ngày của tôi** → việc vào Ngày của tôi. Gõ việc ở **Quan trọng** → tự gắn sao; ở **Đã lên kế hoạch** → hạn hôm nay.
- **Mất mạng:** dòng "N bản ghi đã lưu, sẽ gửi khi có mạng"; có mạng lại sẽ tự gửi. Gõ việc / sửa việc cần có mạng.
- **Đóng app ngay sau khi nói:** kết quả hiện ở notification, có nút **Hoàn tác**.
- **Sửa / xóa:** chạm vào dòng việc hoặc thẻ ghi chú. Máy mở ra thì chi tiết hiện ở cột bên phải.
- **Báo "Sai mã thiết bị":** vào **Cài đặt** nhập lại token → **Lưu và kiểm tra** (các bản ghi đang chờ sẽ tự gửi lại).

## Thử trên máy ảo (cho Claude Code)

Bản debug cho phép HTTP thường tới server local và có lối vào để xếp hàng một file ghi âm mẫu (máy ảo không có micro):
```
adb -e reverse tcp:8001 tcp:8001                    # app dùng http://127.0.0.1:8001
adb -e push samples/hai-viec.m4a /sdcard/Android/data/com.sitpro.notevoice/files/hai-viec.m4a
adb -e shell am broadcast -n com.sitpro.notevoice/.DebugVoiceReceiver --es file hai-viec.m4a --ez my_day true
```
Bản release không có hai thứ này (`app/src/debug/`).
