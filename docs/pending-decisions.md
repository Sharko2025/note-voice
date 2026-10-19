# Pending decisions

Định dạng: ngày · vấn đề · đề xuất (phương án an toàn đang dùng)

- 2026-10-19 · Parse ra `remind_at` sớm hơn `recorded_at` (ví dụ nói "nhắc 9 giờ sáng nay" lúc 14:30) · Giữ `due_date`, bỏ `remind_at` (không đặt báo thức trong quá khứ).
- 2026-10-19 · `GET /tasks` trả việc đã xong không giới hạn số lượng · MVP trả hết (1 người dùng); Ngày của tôi chỉ trả việc xong hôm nay.
- 2026-10-19 · Thứ tự việc trong danh sách · Mới nhất trước; riêng Đã lên kế hoạch theo `due_date` rồi `remind_at`. Việc trong cùng 1 lần nói giữ đúng thứ tự nói (`created_at = clock_timestamp()` như expense-voice).
