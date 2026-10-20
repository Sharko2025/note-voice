# Spec MVP – Note Cá Mập (việc cần làm bằng giọng nói)

Oct 19, 2026 · Loi duyệt logo + UI 2026-10-19 · thêm Ghi chú (mockup v3) 2026-10-19

## Mục tiêu và phạm vi MVP

App việc cần làm kiểu Microsoft To Do, thêm việc nhanh nhất bằng giọng nói: mở app → nhấn micro → nói → việc hiện trong danh sách, phản hồi trong khoảng 3 giây. App chỉ dùng cho 1 người (anh), 1 thiết bị (Samsung Galaxy Z Fold 8), không phát hành lên Play Store. Song ngữ như expense-voice: giao diện Việt/English (**mặc định tiếng Việt**), nói được cả tiếng Việt lẫn tiếng Anh.

Ví dụ câu nói: *"mai 9 giờ sáng gọi cho anh Tuấn, mua sữa cho con, quan trọng"* → 2 việc: "Gọi cho anh Tuấn" (hạn mai, nhắc 09:00 mai) và "Mua sữa cho con" (gắn sao). Tiếng Anh: *"remind me to call the bank tomorrow at 3pm"*.

Ngoài việc cần làm, anh còn ghi lại **ý tưởng bất chợt** và **câu nói hay**. Phần nào trong lời nói không phải việc cần làm thì lưu thành **ghi chú** (nguyên văn, có nhãn Ý tưởng / Câu hay / Ghi chú), không bao giờ bỏ đi. Ví dụ: *"ý tưởng: làm app nhắc con uống nước"* → 1 ghi chú nhãn Ý tưởng; *"câu hay của Steve Jobs: stay hungry, stay foolish"* → 1 ghi chú nhãn Câu hay, tác giả Steve Jobs. (Loi chốt 2026-10-19.)

**Phải có (MVP)**

- Mở như app bình thường: icon launcher mở danh sách việc; nút micro nổi trong danh sách để ghi âm.
- Ghi âm, tự dừng khi im lặng khoảng 1,5 giây.
- OpenAI chuyển giọng nói thành chữ, sau đó parse thành 1 hoặc nhiều việc (tiêu đề, ngày hạn, giờ nhắc, gắn sao) và ghi chú (nguyên văn, nhãn, tác giả của câu hay). Một lần nói có thể ra cả việc lẫn ghi chú.
- Màn hình **Ghi chú**: danh sách ý tưởng, câu hay, ghi chú; lọc theo nhãn; gắn sao, sửa, xóa (có hoàn tác). Nói khi đang ở màn Ghi chú → mọi thứ thành ghi chú.
- Thêm và sửa việc, ghi chú bằng cách gõ.
- Lưu vào Postgres trên server; hàng đợi offline cho ghi âm khi mất sóng.
- Kết quả ghi âm hiện ngay trong danh sách; snackbar hiện câu đã nghe kèm nút **Hoàn tác**.
- Bốn màn hình: **Ngày của tôi**, **Quan trọng**, **Đã lên kế hoạch**, **Tác vụ** (tất cả).
- Đánh dấu hoàn thành, gắn sao, sửa, xóa (có hoàn tác).
- Nhắc việc bằng notification đúng giờ, chạy hoàn toàn trên điện thoại (AlarmManager), không cần server push; notification có nút **Xong** và **Báo lại sau 10 phút**.

**Nice to have (sau MVP)**

- Quick Settings tile / widget để ghi âm không cần mở app.
- Việc lặp lại (hằng ngày, hằng tuần…).
- Nhiều danh sách (Công việc, Nhà…), bước con trong một việc.
- Thêm/sửa việc khi mất mạng (MVP: chỉ ghi âm được xếp hàng offline).
- Backup ngoài máy EC2 (S3 + IAM role, hoặc chỗ khác). MVP: 30 bản trên chính server (Loi đưa vào backlog 2026-10-20).

**Ngoài phạm vi**

- Đồng bộ với Microsoft To Do, Google Tasks, lịch.
- Chia sẻ việc, nhiều người dùng.
- Ghi âm từ màn hình khóa, phím bên (nút nguồn).

## Tech stack

Giống expense-voice: app Android, backend trên EC2 và OpenAI. OpenAI key chỉ nằm trên server; app chỉ giữ token để gọi backend.

| Lớp | Công nghệ | Ghi chú |
| --- | --- | --- |
| Android UI | Kotlin + Jetpack Compose | 1 Activity (`MainActivity`) là launcher duy nhất |
| Ghi âm | `MediaRecorder` → AAC/m4a, 16 kHz mono | Tự dừng khi im lặng \~1,5 giây, tối đa 30 giây |
| Lưu trên máy | Room (SQLite) | Hàng đợi upload + cache việc |
| Đồng bộ | WorkManager + Retrofit/OkHttp | Tự gửi lại khi có mạng |
| Nhắc việc | `AlarmManager.setExactAndAllowWhileIdle` + `BroadcastReceiver` | Đặt lại sau khi khởi động máy |
| Bảo mật trên máy | EncryptedSharedPreferences | Lưu device token |
| Backend | Python 3.12 + FastAPI + SQLAlchemy + Alembic | Chạy trong Docker |
| Database | PostgreSQL 16 | Container riêng, không publish port |
| Reverse proxy | nginx + certbot (Let's Encrypt) | Đã có trên EC2 |
| Speech-to-text | OpenAI transcribe (`gpt-4o-mini-transcribe` hoặc bản mới nhất) | Tự nhận tiếng Việt / tiếng Anh |
| Parse | OpenAI model mini + Structured Outputs | JSON luôn đúng schema |
| Hạ tầng | AWS EC2 công ty, Docker Compose | Chung máy với expense-voice, tách container và DB |

## Luồng hoạt động

Một lần nói đi từ điện thoại lên server, qua OpenAI 2 lần, lưu vào Postgres rồi trả kết quả về danh sách trong app.

1. Nhấn micro → ghi âm → tự dừng.
2. App lưu file, tạo `PendingUpload` (`entry_id` = UUID, `recorded_at`), hiện dòng "⏳ Đang xử lý…" trên đầu danh sách.
3. `UploadWorker` gửi `POST /voice`. Server transcribe → parse (lấy `recorded_at` làm "bây giờ" để hiểu "mai", "thứ 6") → lưu → trả danh sách việc.
4. App chèn các việc và ghi chú mới vào danh sách, hiện snackbar "Đã thêm 2 việc" (hoặc "Đã thêm 1 việc, 1 ghi chú") kèm câu đã nghe và **Hoàn tác**, đặt báo thức cho việc có `remind_at`.

Mất mạng thì file nằm trong hàng đợi Room và tự gửi khi có mạng; `entry_id` giúp server không tạo bản ghi trùng. Nếu app đang đóng khi kết quả về thì hiện notification kết quả (giống expense-voice).

## Schema DB

Ba bảng: `voice_entries` lưu mỗi lần nói (giữ nguyên từ expense-voice), `tasks` lưu từng việc, `notes` lưu từng ghi chú.

```sql
CREATE TABLE voice_entries (
  id           UUID PRIMARY KEY,           -- app sinh ra, chống gửi trùng
  recorded_at  TIMESTAMPTZ NOT NULL,       -- giờ anh nói (trên máy)
  received_at  TIMESTAMPTZ DEFAULT now(),
  raw_text     TEXT,                       -- câu gốc sau transcribe
  status       TEXT NOT NULL,              -- received | transcribed | parsed | failed
  error        TEXT,
  stt_ms       INT,
  parse_ms     INT,
  total_ms     INT
);

CREATE TABLE tasks (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  voice_entry_id  UUID REFERENCES voice_entries(id) ON DELETE CASCADE,  -- NULL nếu gõ tay
  title           TEXT NOT NULL CHECK (title <> ''),
  note            TEXT,
  starred         BOOLEAN NOT NULL DEFAULT FALSE,   -- Quan trọng
  due_date        DATE,                             -- ngày hạn theo giờ VN, NULL = không hạn
  remind_at       TIMESTAMPTZ,                      -- NULL = không nhắc
  my_day_date     DATE,                             -- ngày được thêm vào "Ngày của tôi"
  completed_at    TIMESTAMPTZ,                      -- NULL = chưa xong
  deleted_at      TIMESTAMPTZ,                      -- xóa mềm, phục vụ Hoàn tác
  created_at      TIMESTAMPTZ DEFAULT now(),
  updated_at      TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX ON tasks (due_date) WHERE deleted_at IS NULL;

CREATE TABLE notes (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  voice_entry_id  UUID REFERENCES voice_entries(id) ON DELETE CASCADE,  -- NULL nếu gõ tay
  text            TEXT NOT NULL CHECK (text <> ''),              -- nguyên văn lời nói
  label           TEXT NOT NULL DEFAULT 'note'
                  CHECK (label IN ('idea', 'quote', 'note')),    -- Ý tưởng | Câu hay | Ghi chú
  author          TEXT,                                          -- tác giả câu hay, NULL = không nói
  starred         BOOLEAN NOT NULL DEFAULT FALSE,
  deleted_at      TIMESTAMPTZ,
  created_at      TIMESTAMPTZ DEFAULT now(),
  updated_at      TIMESTAMPTZ DEFAULT now()
);
```

- Có `remind_at` mà không có `due_date` → server đặt `due_date` = ngày (giờ VN) của `remind_at`.
- Việc mới (nói hoặc gõ) tạo khi đang ở "Ngày của tôi" thì `my_day_date` = hôm nay.
- File audio không lưu vào DB; xóa khỏi đĩa ngay sau khi transcribe xong.

**Định nghĩa các màn hình** (tính theo `Asia/Ho_Chi_Minh`, bỏ việc đã xóa mềm):

| Màn hình | Điều kiện |
| --- | --- |
| Ngày của tôi | `my_day_date = hôm nay` **hoặc** (`due_date <= hôm nay` và chưa xong) |
| Quan trọng | `starred = true` và chưa xong |
| Đã lên kế hoạch | `due_date IS NOT NULL` và chưa xong; nhóm theo Quá hạn / Hôm nay / Ngày mai / Tuần này / Sau này |
| Tác vụ | tất cả |

Việc đã xong của mỗi màn hình nằm trong nhóm "Đã hoàn thành" thu gọn ở cuối (Ngày của tôi: chỉ việc xong hôm nay; Tác vụ: tất cả).

Ghi chú không hiện trong 4 màn hình việc. Màn **Ghi chú**: mọi ghi chú chưa xóa, mới nhất trước, lọc theo nhãn (Tất cả / Ý tưởng / Câu hay / Ghi chú). Ghi chú không có ngày hạn, giờ nhắc hay hoàn thành.

## API backend

Mọi endpoint đều yêu cầu header `Authorization: Bearer <DEVICE_TOKEN>`. Prefix `/api/v1`.

| Method | Endpoint | Việc làm | Trả về |
| --- | --- | --- | --- |
| POST | `/voice` | Nhận audio (multipart: `file`, `entry_id`, `recorded_at`, `my_day?`, `as_note?`), transcribe, parse, lưu | `entry_id`, `raw_text`, danh sách việc và ghi chú đã tạo |
| GET | `/tasks?view=my_day\|important\|planned\|all` | Danh sách việc theo màn hình, kèm việc đã xong | mảng việc |
| POST | `/tasks` | Thêm việc gõ tay (`id` do app sinh, idempotent) | việc |
| PATCH | `/tasks/{id}` | Sửa `title`, `note`, `starred`, `due_date`, `remind_at`, `my_day` (bool), `completed` (bool) | việc sau khi sửa |
| DELETE | `/tasks/{id}` | Xóa mềm | 204 |
| POST | `/tasks/{id}/restore` | Hoàn tác xóa | việc |
| GET | `/notes?label=idea\|quote\|note` | Ghi chú chưa xóa, mới nhất trước (`label` bỏ trống = tất cả) | mảng ghi chú |
| POST | `/notes` | Thêm ghi chú gõ tay (`id` do app sinh, idempotent; `label` mặc định `note`) | ghi chú |
| PATCH | `/notes/{id}` | Sửa `text`, `label`, `author`, `starred` | ghi chú sau khi sửa |
| DELETE | `/notes/{id}` | Xóa mềm | 204 |
| POST | `/notes/{id}/restore` | Hoàn tác xóa | ghi chú |
| POST | `/voice/{entry_id}/undo` | Hoàn tác cả lần nói (xóa mềm mọi việc và ghi chú của nó) | 204 |
| GET | `/health` | Kiểm tra sống (không cần token) | `ok` |

**Quy tắc xử lý** (giữ nguyên từ expense-voice)

- `POST /voice` **idempotent** theo `entry_id`; entry đang xử lý → 409; kẹt quá 2 phút thì cho xử lý lại; `failed` thì xử lý lại từ đầu.
- Xử lý đồng bộ trong request (mục tiêu dưới 3 giây). Timeout OpenAI 20 giây; lỗi → `status=failed`, trả 502 để app thử lại.
- `as_note=true` (nói khi đang ở màn Ghi chú) → mọi nội dung thành ghi chú, không tạo việc.
- Parse ra 0 việc và 0 ghi chú (chỉ có tiếng ồn, "ừm…") → 422 kèm `raw_text`, app hiện "Không hiểu, nói lại nhé".
- `recorded_at` thiếu múi giờ → 422.

## Pipeline AI

Hai lần gọi OpenAI: transcribe audio thành chữ, rồi parse chữ thành JSON (việc + ghi chú) bằng Structured Outputs. Không nói giờ nhắc thì để `null`, **không đoán**. Không bỏ nội dung nào: phần không phải việc thành ghi chú.

**Bước 1: Transcribe**

- Model: biến `OPENAI_STT_MODEL`. Không cố định `language` (biến `OPENAI_STT_LANGUAGE`, rỗng = tự nhận, `vi` = chỉ tiếng Việt), giống expense-voice.
- `prompt` gợi ý từ vựng: `"nhắc, hạn, mai, ngày kia, thứ hai, chủ nhật, tuần sau, giờ, rưỡi, kém, sáng, trưa, chiều, tối, quan trọng, gấp, ý tưởng, ghi chú, câu hay, remind me, tomorrow, next week, Monday, a.m., p.m., important, urgent, idea, quote"`.

**Bước 2: Parse**

- Model: biến `OPENAI_PARSE_MODEL`, `temperature=0`, JSON Schema strict bên dưới.
- Chèn thời điểm nói vào prompt mỗi lần gọi: `{now}` = `recorded_at` theo giờ VN, dạng `Thứ Hai (Monday), 19/10/2026 14:30`.

System prompt:

```text
Bạn tách câu nói tiếng Việt hoặc tiếng Anh thành việc cần làm (tasks) và ghi chú (notes).
Bây giờ là {now} (giờ Việt Nam).
Quy tắc cho việc (tasks):
- Mỗi việc riêng là 1 mục. Một câu có thể có nhiều việc. Bản chép giọng nói có thể thiếu dấu phẩy: "gọi cho anh Tuấn mua sữa cho con" là 2 việc. Hai việc khác nhau nối bằng "và"/"and" vẫn là 2 mục: "call mom and book the dentist" = 2 việc. Các hành động phục vụ cùng một việc là 1 mục: "đi siêu thị mua sữa và trứng" = 1 việc.
- source: chép nguyên văn phần câu nói thuộc về việc này (gồm cả từ chỉ ngày, giờ, nhắc, quan trọng của riêng việc đó). Source của các việc không trùng nhau: "thứ 6 3 giờ chiều họp với khách mua quà cho vợ, quan trọng" -> source "thứ 6 3 giờ chiều họp với khách" và "mua quà cho vợ, quan trọng". Chỉ lấy due_date, remind_at, starred từ source của chính việc đó.
- title: ngắn gọn, viết hoa chữ đầu, giữ nguyên ngôn ngữ người nói, bỏ các từ chỉ thời gian, nhắc nhở và mức độ quan trọng ("nhắc tôi", "mai", "lúc 9 giờ", "quan trọng", "remind me", "tomorrow", "at 3pm").
- due_date (YYYY-MM-DD): "hôm nay", "mai"/"ngày mai", "ngày kia"/"mốt", "thứ 2".."thứ 7", "chủ nhật" (lần gần nhất sau hôm nay), "tuần sau" (thứ Hai tuần sau), "cuối tuần" (thứ Bảy tới), "20/10", "ngày 25". Tiếng Anh: "today", "tomorrow", "Friday"/"on Friday" (lần gần nhất sau hôm nay), "next week" (thứ Hai tuần sau), "this weekend" (thứ Bảy tới), "Oct 25"/"the 25th". Không nói -> null.
- remind_at (YYYY-MM-DDTHH:MM, giờ Việt Nam): chỉ khi có giờ cụ thể hoặc có chữ "nhắc". "9 giờ sáng" = 09:00, "3 giờ chiều" = 15:00, "8 giờ tối" = 20:00, "9 rưỡi" = 09:30, "10 giờ kém 15" = 09:45; "3pm" = 15:00, "9:30 am" = 09:30, "noon" = 12:00. Có "nhắc"/"remind" mà chỉ nói buổi: sáng/morning = 08:00, trưa = 12:00, chiều/afternoon = 15:00, tối/evening/tonight = 20:00. Có giờ mà không có ngày: so với giờ bây giờ, giờ đó chưa tới -> hôm nay, đã qua -> ngày mai (ví dụ bây giờ 14:30: "4 giờ chiều" -> hôm nay 16:00, "10 giờ sáng" -> ngày mai 10:00). Không nói -> null.
- Có remind_at mà không có due_date -> due_date = ngày của remind_at.
- starred: true nếu nói "quan trọng", "gấp", "ưu tiên", "đánh dấu sao", "important", "urgent", "star it" cho việc đó; ngược lại false. Từ chỉ mức độ quan trọng ở cuối câu chỉ thuộc về việc nói ngay trước nó, không áp cho các việc trước đó.
- Chỉ tạo việc khi người nói muốn làm hoặc được nhắc làm một việc cụ thể. Câu kể, nhận xét, thời tiết ("hôm nay trời đẹp quá", "the weather is nice today"), ý tưởng, câu nói hay không phải việc: đó là ghi chú.
Quy tắc cho ghi chú (notes):
- Mọi phần của câu nói không phải việc là ghi chú. Không bỏ nội dung nào. Một câu có thể có cả việc lẫn ghi chú.
- Mở đầu bằng "ý tưởng", "ghi chú", "ghi lại", "câu hay", "idea", "note", "quote" -> phần theo sau là ghi chú, kể cả khi nghe giống việc cần làm ("ý tưởng: làm app nhắc con uống nước" là ghi chú, không phải việc).
- source: chép nguyên văn phần câu nói thuộc về ghi chú này.
- text: nguyên văn lời nói, chỉ thêm dấu câu và viết hoa chữ đầu. Bỏ từ mở đầu ("ý tưởng là", "câu hay của Steve Jobs", "ghi chú", "idea:", "quote:"). Không tóm tắt, không dịch, không viết lại.
- label: "quote" cho câu nói hay, trích dẫn, tục ngữ; "idea" cho ý tưởng, dự định chợt nghĩ ra ("hay là…", "mình nên thử…", "what if…"); còn lại "note".
- author: người nói câu trích dẫn nếu được nêu ("câu của Steve Jobs", "as Steve Jobs said"), ngược lại null.
- Tiếng ngập ngừng, tiếng ồn, không có nội dung ("ừm", "à", "alo alo") -> không tạo gì.
- Không có việc nào -> tasks rỗng. Không có ghi chú nào -> notes rỗng.
```

Khi `as_note=true`, thêm dòng cuối vào prompt: `Người nói đang ở màn hình Ghi chú: mọi nội dung là ghi chú, tasks luôn rỗng.` Server vẫn chuyển việc nào model lỡ trả về thành ghi chú (lấy `source` làm `text`, nhãn `note`).

JSON Schema (strict):

```json
{
  "type": "object",
  "additionalProperties": false,
  "required": ["tasks", "notes"],
  "properties": {
    "tasks": {
      "type": "array",
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["source", "title", "due_date", "remind_at", "starred"],
        "properties": {
          "source":    {"type": "string"},
          "title":     {"type": "string"},
          "due_date":  {"type": ["string", "null"]},
          "remind_at": {"type": ["string", "null"]},
          "starred":   {"type": "boolean"}
        }
      }
    },
    "notes": {
      "type": "array",
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["source", "text", "label", "author"],
        "properties": {
          "source": {"type": "string"},
          "text":   {"type": "string"},
          "label":  {"type": "string", "enum": ["idea", "quote", "note"]},
          "author": {"type": ["string", "null"]}
        }
      }
    }
  }
}
```

`source` chỉ giúp model gắn ngày/giờ/sao đúng việc và tách đúng ghi chú (không lưu). Server kiểm tra lại định dạng ngày giờ sau khi parse; sai định dạng thì bỏ trường đó (để `null`), không bỏ cả việc. Giờ nhắc rơi vào trước lúc nói trong cùng ngày → server dời sang ngày mai (LLM hay sai luật "giờ đã qua"); bảng test dưới đây kiểm kết quả sau bước này.

**Bộ test bắt buộc** (pytest, gọi API thật, `now` cố định = Thứ Hai 19/10/2026 14:30). Dòng không nhắc tới ghi chú thì mong đợi 0 ghi chú:

| Câu nói | Kết quả mong đợi |
| --- | --- |
| mua sữa | Mua sữa · null · null · false |
| mai 9 giờ sáng gọi cho anh Tuấn, mua sữa cho con, quan trọng | 2 việc: Gọi cho anh Tuấn · 2026-10-20 · 2026-10-20T09:00 · false; Mua sữa cho con · null · null · true |
| Mai chín giờ sáng gọi cho anh Tuấn mua sữa cho con, quan trọng. (STT mất dấu phẩy) | như dòng trên |
| đi siêu thị mua sữa và trứng | 1 việc · null · null · false |
| mai 9 giờ sáng gọi cho anh Tuấn | Gọi cho anh Tuấn · 2026-10-20 · 2026-10-20T09:00 · false |
| nộp báo cáo thứ 6, quan trọng | Nộp báo cáo · 2026-10-23 · null · true |
| nhắc tôi 4 giờ chiều uống thuốc | Uống thuốc · 2026-10-19 · 2026-10-19T16:00 · false |
| nhắc tôi 10 giờ sáng đi lấy đồ | (10:00 đã qua) 2026-10-20 · 2026-10-20T10:00 |
| nhắc tôi 9 giờ sáng gọi mẹ | (09:00 đã qua) Gọi mẹ · 2026-10-20 · 2026-10-20T09:00 |
| mua sữa cho con, gửi email cho chị Lan chiều mai nhắc tôi | 2 việc: Mua sữa cho con (null); Gửi email cho chị Lan · 2026-10-20 · 2026-10-20T15:00 |
| ngày kia đóng tiền điện | Đóng tiền điện · 2026-10-21 · null |
| chủ nhật đi siêu thị 9 rưỡi sáng | Đi siêu thị · 2026-10-25 · 2026-10-25T09:30 |
| hạn 25/10 nộp thuế, gấp | Nộp thuế · 2026-10-25 · null · true |
| hôm nay trời đẹp quá | 0 việc; 1 ghi chú · note · "Hôm nay trời đẹp quá" |
| remind me to call the bank tomorrow at 3pm | Call the bank · 2026-10-20 · 2026-10-20T15:00 · false |
| buy milk and pay rent on Friday, it's urgent | 2 việc: Buy milk (không sao; ngày không kiểm vì "on Friday" có thể cho cả 2 việc); Pay rent · 2026-10-23 · null · true |
| the weather is nice today | 0 việc; 1 ghi chú · note |
| ý tưởng: làm app nhắc con uống nước | 0 việc; 1 ghi chú · idea · có "uống nước", không có "ý tưởng" |
| câu hay của Steve Jobs: stay hungry, stay foolish | 0 việc; 1 ghi chú · quote · "Stay hungry, stay foolish" · tác giả Steve Jobs |
| có công mài sắt có ngày nên kim | 0 việc; 1 ghi chú · quote · tác giả null |
| idea: a podcast about sharks for kids | 0 việc; 1 ghi chú · idea · có "podcast" |
| mai 9 giờ sáng gọi anh Tuấn, à ý tưởng là thêm chế độ tối cho app | 1 việc: Gọi anh Tuấn · 2026-10-20 · 2026-10-20T09:00; 1 ghi chú · idea · có "chế độ tối" |
| gọi cho anh Tuấn hỏi về hợp đồng (`as_note=true`) | 0 việc; 1 ghi chú · có "hợp đồng" |
| ừm… à… | 0 việc, 0 ghi chú → 422 |

## Android app

**Tên và ngôn ngữ:** tên app **Note Cá Mập** / **Sharko Notes**; tên dưới icon trên launcher luôn là **Note Cá Mập**. Giao diện song ngữ giống expense-voice: **mặc định tiếng Việt** khi mở app lần đầu, nút **VI / EN** trên thanh trên cùng, đồng bộ với Settings → Apps → Language của Android. Package `com.sitpro.notevoice`. Ngày hiển thị `T2, 20/10` (VI) / `Mon, Oct 20` (EN); giờ luôn dạng 24h `09:00`.

**Logo:** phương án **C "Tờ note"** (Loi chọn 2026-10-19, [canvas](https://claude.ai/artifact/V4QFEjaMxsRDk6YCGr3ZfH)): tờ note màu giấy `#F4F7FB` gấp góc trên nền navy `#0B2545`, vây cá mập cam `#F28C28` trên sóng xanh `#3FA7D6`, 2 dòng việc bên dưới. Icon notification: tờ note trắng, vây và dòng khoét rỗng.

**Màu và UI:** bảng màu **cố định** navy + cam theo logo (không lấy màu theo hình nền như Ví Cá Mập), có giao diện sáng và tối. Mọi màn hình theo [mockup đã duyệt](https://claude.ai/artifact/5wQsjDRmKu5ZYgYmVjN8Ch).

**Cấu trúc project**

```text
app/src/main/java/com/sitpro/notevoice/
  audio/     Recorder, SilenceDetector (copy từ expense-voice)
  data/      Room: PendingUpload, CachedTask, CachedNote; TaskRepository, NoteRepository; SettingsStore
  sync/      UploadWorker, UploadProcessor
  api/       Retrofit ApiService, AuthInterceptor, Dto
  reminder/  ReminderScheduler, ReminderReceiver, BootReceiver
  notify/    ResultNotifier (kết quả khi app đóng + kênh nhắc việc)
  ui/        TaskListScreen (dùng chung 4 màn), TaskEditSheet, NoteListScreen, NoteEditSheet, SettingsScreen, Texts, AppLanguage
```

**Màn hình chính**

- **Side menu** (ngăn kéo, mở bằng ☰; không dùng bottom bar) với 4 mục: Ngày của tôi (mặc định), Quan trọng, Đã lên kế hoạch, Tác vụ; mỗi mục có số việc chưa xong. Dưới vạch ngăn là mục **Ghi chú** (số ghi chú). Fold 8 mở ra thì hiện thanh bên cố định.
- **Màn Ghi chú:** chip lọc Tất cả / Ý tưởng / Câu hay / Ghi chú; mỗi thẻ: nhãn (Ý tưởng cam 💡, Câu hay xanh ❝, Ghi chú xám) · nguyên văn (tối đa 6 dòng) · "— tác giả" nếu có · giờ tạo · ngôi sao. Ô dưới cùng "Thêm ghi chú"; micro ở màn này gửi `as_note=true`.
- **Sửa ghi chú** (bottom sheet): nội dung nhiều dòng, chip nhãn, tác giả (khi nhãn Câu hay), sao, xóa → snackbar Hoàn tác.
- Mỗi dòng: ô tròn hoàn thành · tiêu đề · dòng phụ (ngày hạn, 🔔 giờ nhắc; quá hạn tô đỏ) · ngôi sao.
- Ô "Thêm việc" ở dưới cùng để gõ; nút micro nổi cạnh đó để ghi âm.
- Đang ghi âm: thanh ghi âm có sóng âm, nút Dừng / Hủy. 8 giây đầu không có tiếng → hủy, báo "Không nghe thấy gì".
- Kéo xuống để tải lại; mất mạng thì hiện cache kèm "cập nhật lúc …".

**Sửa việc** (bottom sheet khi chạm vào dòng): tiêu đề, thêm/bỏ khỏi Ngày của tôi, ngày hạn (DatePicker), nhắc lúc (DatePicker + TimePicker, không cho chọn quá khứ), sao, ghi chú, xóa. Xóa → snackbar Hoàn tác.

**Nhắc việc**

- Mỗi việc có `remind_at` trong tương lai và chưa xong → 1 báo thức chính xác, `requestCode` theo id việc.
- Đặt lại toàn bộ báo thức sau mỗi lần tải danh sách, sau khi sửa việc, và sau `BOOT_COMPLETED`.
- Nhắc bị lỡ lúc máy tắt (trong 24 giờ qua, chưa xong) → báo ngay khi máy bật lại, mỗi việc 1 lần (Loi chốt 2026-10-20).
- Kênh "Nhắc việc" ưu tiên cao: tiếng + rung, hiện nổi, hiện trên màn hình khóa; không reo toàn màn hình. Chạm notification / **Mở** → mở chi tiết việc đó.
- Notification: tiêu đề việc, giờ nhắc; chạm để mở app; nút **Xong** (gọi `PATCH completed=true` qua WorkManager), **Báo lại sau 10 phút** (đặt lại báo thức ngay trên máy, rồi `PATCH remind_at` = bây giờ + 10 phút qua WorkManager), **Mở**.

**Cài đặt** (màn hình phụ): URL server (mặc định `https://notecamap.s-itpro.com`), device token, nút "Gửi lại hàng đợi", số mục đang chờ.

**Quyền cần xin:** `RECORD_AUDIO`, `POST_NOTIFICATIONS`, `INTERNET`, `USE_EXACT_ALARM` (tự có khi cài, không lên Play Store; Loi chốt 2026-10-20), `RECEIVE_BOOT_COMPLETED`.

## Hạ tầng

Hai container trong 1 file Docker Compose, đứng sau nginx sẵn có trên EC2, chạy song song với expense-voice.

**Thông tin triển khai**

- Domain: `notecamap.s-itpro.com` → A record `54.251.52.235`
- Repo: github.com/Sharko2025/note-voice (public)
- API bind `127.0.0.1:8001` (8000 là của expense-voice); Postgres riêng, `POSTGRES_DB=notevoice`.

**.env**

```text
DEVICE_TOKEN=<chuỗi ngẫu nhiên 32+ ký tự, khác expense-voice>
OPENAI_API_KEY=sk-...
OPENAI_STT_MODEL=gpt-4o-mini-transcribe
OPENAI_PARSE_MODEL=gpt-5.4-mini
POSTGRES_DB=notevoice
POSTGRES_USER=notevoice
POSTGRES_PASSWORD=<mạnh>
DATABASE_URL=postgresql+psycopg://notevoice:<pw>@db:5432/notevoice
TZ=Asia/Ho_Chi_Minh
BACKUP_S3_URI=s3://<bucket>/notevoice/
```

**nginx** (`notecamap.conf`): HTTPS cho `notecamap.s-itpro.com`, `client_max_body_size 10M`, `proxy_read_timeout 60s`, `proxy_pass http://127.0.0.1:8001`, `limit_req_zone` tên riêng (`notecamap`), log riêng. SSL: `sudo certbot --nginx -d notecamap.s-itpro.com`.

**Bảo mật và backup:** giống expense-voice (token so bằng `hmac.compare_digest`, không log audio/token, OpenAI key có giới hạn chi tiêu, `backup.sh` lúc 02:00 đẩy lên S3 giữ 30 bản).

## Build và cài lên Fold 8

Giống expense-voice (xem `docs/fold8-setup.md`): dev qua Wireless debugging, dùng thật bằng APK tự ký với keystore riêng `notevoice.jks`. Pin đặt **Không hạn chế** để báo thức và hàng đợi chạy đúng giờ.

## Thứ tự triển khai

Làm backend trước và test bằng `curl` với audio thu sẵn, sau đó mới làm app. Mỗi phase xong mới sang phase sau.

**Phase 1: Backend + AI**

- [x] Khởi tạo repo từ expense-voice, Docker Compose, Alembic migration
- [x] `POST /voice`: transcribe + parse + lưu, idempotent theo `entry_id`
- [x] Bộ test parse ở mục Pipeline AI đều qua
- [x] Các endpoint tasks (4 view), health
- [x] Deploy lên EC2, nginx + SSL, `curl` từ ngoài gửi audio ra kết quả đúng
- [x] Ghi chú: bảng `notes`, `POST /voice` ra cả ghi chú (+ `as_note`), endpoint `/notes`, bộ test parse mới đều qua, deploy (2026-10-19; audio TTS qua HTTPS 2,7 s, còn chờ file ghi âm thật)

**Trước Phase 2: Mockup**

- [x] Mockup logo, Loi chọn 1 phương án (C, 2026-10-19)
- [x] Mockup toàn bộ màn hình (VI + EN, sáng + tối), Loi duyệt (2026-10-19)
- [x] Mockup v3 thêm màn Ghi chú, Loi duyệt (2026-10-19)

**Phase 2: App Android cơ bản**

- [x] 4 màn hình danh sách, thêm/sửa/xóa/hoàn thành/gắn sao bằng tay
- [x] Màn Ghi chú: lọc nhãn, thêm/sửa/xóa/gắn sao bằng tay, ghi âm `as_note`
- [x] Ghi âm trong app + hàng đợi Room + `UploadWorker` + kết quả kèm Hoàn tác
- [x] Bật chế độ máy bay, nói 3 lần, tắt chế độ máy bay → cả 3 lên server, không trùng

**Phase 3: Nhắc việc**

- [ ] Báo thức chính xác + notification có nút Xong, Báo lại sau 10 phút
- [ ] Khởi động lại máy → báo thức vẫn kêu đúng giờ

**Phase 4: Dùng thật**

- [ ] Build APK đã ký, cài lên Fold 8
- [ ] Backup hằng đêm chạy, thử khôi phục 1 lần
- [ ] Dùng 1 tuần, ghi lại các câu parse sai để chỉnh prompt

**Tiêu chí hoàn thành MVP**

- Từ lúc dừng nói đến lúc việc hiện trong danh sách dưới 3 giây khi có 4G/Wi-Fi.
- Ít nhất 9/10 câu nói thường ngày ra đúng tiêu đề, ngày và giờ nhắc, không cần sửa.
- Mất mạng không mất lần nói nào, không tạo việc trùng.
- Không bỏ lỡ báo thức nào trong 1 tuần dùng thật.

## Quyết định đã chốt

| Chủ đề | Quyết định | Lý do |
| --- | --- | --- |
| Tên, domain | **Note Cá Mập**, `notecamap.s-itpro.com` | Loi chốt 2026-10-19 |
| Ngôn ngữ | Song ngữ Việt/English giống expense-voice, mặc định tiếng Việt; STT tự nhận ngôn ngữ | Loi chốt 2026-10-19 |
| Logo | Phương án C "Tờ note" | Loi chọn 2026-10-19 |
| Màu | Cố định navy + cam theo logo, sáng/tối | Loi chốt 2026-10-19; giống họ Ví Cá Mập qua logo |
| Điều hướng | Side menu, không bottom bar | Loi chốt 2026-10-19; giữ chỗ cho danh sách trên màn hình ngoài |
| Kết quả ghi âm | Snackbar hiện câu đã nghe + Hoàn tác | Loi chốt 2026-10-19; thấy ngay khi STT nghe nhầm |
| Báo lại (snooze) | Có trong MVP, cố định 10 phút | Loi chốt 2026-10-19 |
| Ghi chú (ý tưởng, câu hay) | Có trong MVP. AI tự phân loại: phần không phải việc → ghi chú nguyên văn, không bỏ gì. 1 mục Ghi chú chung, nhãn Ý tưởng / Câu hay / Ghi chú | Loi chốt 2026-10-19 (phương án A, 1 mục chung, mockup v3) |
| Cách mở app | App bình thường, micro trong danh sách | Giống Microsoft To Do; không cần màn hình khóa/phím bên |
| Lọc 4 màn hình | App tải 1 danh sách (`view=all`) rồi tự lọc theo bảng "Định nghĩa các màn hình" | Loi chốt 2026-10-19; có số đếm trong menu, xem được khi mất mạng |
| Gõ việc ở Quan trọng / Đã lên kế hoạch | Tự gắn sao / đặt hạn hôm nay như Microsoft To Do (việc nói chỉ theo `my_day`) | Loi chốt 2026-10-19 |
| Kết quả ghi âm | App đang mở → snackbar; app đã đóng → notification kèm Hoàn tác | Loi chốt 2026-10-19 |
| Nút micro | Tròn to 76dp, cam, có vòng sáng, nằm giữa sát đáy, nổi đè lên thanh "Thêm việc"; ẩn khi đang gõ (✓ nhỏ trong ô gõ) | Loi chốt 2026-10-20 khi thử Fold 8: dùng 1 tay, khác mockup v3 (mic bên phải) |
| Chuyển nhanh Việc ↔ Ghi chú | Nút viên thuốc "Việc · Ghi chú" cạnh VI · EN; từ Ghi chú quay về danh sách việc mở gần nhất | Loi chốt 2026-10-20 |
| Nhắc việc | Báo thức trên máy, không push từ server | 1 thiết bị, không cần FCM |
| Thời gian tương đối | Tính theo `recorded_at`, không theo lúc server nhận | Nói offline hôm qua "mai" vẫn ra đúng ngày |
| Code | Tái dùng tối đa từ expense-voice (xem `docs/reuse-from-expense-voice.md`) | Đã chạy ổn trên Fold 8 |
| Hạ tầng | Cùng EC2 với expense-voice, Compose + DB riêng, port 8001 | Có sẵn, tách biệt |
