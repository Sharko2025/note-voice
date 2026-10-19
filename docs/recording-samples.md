# Ghi âm mẫu để test

10 file ghi bằng Fold 8, dùng cho `pytest -m openai` (`tests/test_voice_openai.py`). Mỗi file một câu.

## Cách ghi

1. Mở app **Ghi âm** (Samsung Voice Recorder). Định dạng mặc định `.m4a` là đúng.
2. Nói như khi dùng app thật: tốc độ bình thường, cầm máy như thường lệ, có chút tiếng ồn xung quanh cũng được. Không cần nói chậm hay rõ hơn thường ngày.
3. Ghi ngày giờ nào cũng được: test cố định "bây giờ" là Thứ Hai 19/10/2026 14:30, nên "mai" luôn là 20/10.
4. Đổi tên file **đúng như bảng** (trên điện thoại hoặc trên máy tính).
5. Chép vào `C:\vibecoding\ClaudeAgent\note-voice\samples\` (Quick Share, cáp USB hoặc Google Drive). Thư mục này không bao giờ được commit.

## Danh sách

| # | File | Nói | Kết quả mong đợi |
| --- | --- | --- | --- |
| 1 | `goi-anh-tuan.m4a` | mai 9 giờ sáng gọi cho anh Tuấn | 1 việc · hạn 20/10 |
| 2 | `uong-thuoc.m4a` | nhắc tôi 4 giờ chiều uống thuốc | 1 việc · hạn 19/10 |
| 3 | `bao-cao-thu-6.m4a` | nộp báo cáo thứ 6, quan trọng | 1 việc · hạn 23/10 · gắn sao |
| 4 | `hai-viec.m4a` | mua sữa cho con, gửi email cho chị Lan chiều mai nhắc tôi | 2 việc: sữa (không hạn) · chị Lan (hạn 20/10) |
| 5 | `sieu-thi-chu-nhat.m4a` | chủ nhật đi siêu thị 9 rưỡi sáng | 1 việc · hạn 25/10 |
| 6 | `troi-dep.m4a` | hôm nay trời đẹp quá | 0 việc · 1 ghi chú |
| 7 | `call-bank.m4a` | remind me to call the bank tomorrow at 3pm | 1 việc · hạn 20/10 |
| 8 | `milk-rent.m4a` | buy milk and pay rent on Friday, it's urgent | 2 việc: milk · rent (hạn 23/10, gắn sao) |
| 9 | `y-tuong.m4a` | ý tưởng: làm app nhắc con uống nước | 0 việc · 1 ghi chú Ý tưởng |
| 10 | `cau-hay.m4a` | câu hay của Steve Jobs: stay hungry, stay foolish | 0 việc · 1 ghi chú Câu hay |

File nào chưa có thì test tương ứng tự bỏ qua, nên có thể ghi dần.
