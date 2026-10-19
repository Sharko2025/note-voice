import json
from datetime import datetime
from typing import Protocol

import openai

from app.config import get_settings
from app.services.errors import AIServiceError
from app.services.stt import openai_client
from app.timeutil import format_now_for_prompt

# Mirrors SPEC.md "Pipeline AI". Change only together with the openai test suite.
SYSTEM_PROMPT = """Bạn tách câu nói tiếng Việt hoặc tiếng Anh thành việc cần làm (tasks) và ghi chú (notes).
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
- Không có việc nào -> tasks rỗng. Không có ghi chú nào -> notes rỗng."""
AS_NOTE_LINE = "Người nói đang ở màn hình Ghi chú: mọi nội dung là ghi chú, tasks luôn rỗng."

JSON_SCHEMA = {
    "type": "object",
    "additionalProperties": False,
    "required": ["tasks", "notes"],
    "properties": {
        "tasks": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": ["source", "title", "due_date", "remind_at", "starred"],
                "properties": {
                    "source": {"type": "string"},  # spoken words of this task; makes dates attach to the right task
                    "title": {"type": "string"},
                    "due_date": {"type": ["string", "null"]},
                    "remind_at": {"type": ["string", "null"]},
                    "starred": {"type": "boolean"},
                },
            },
        },
        "notes": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": ["source", "text", "label", "author"],
                "properties": {
                    "source": {"type": "string"},
                    "text": {"type": "string"},
                    "label": {"type": "string", "enum": ["idea", "quote", "note"]},
                    "author": {"type": ["string", "null"]},
                },
            },
        },
    },
}


class TaskParser(Protocol):
    def parse(self, text: str, now: datetime, as_note: bool = False) -> dict:
        """Returns {"tasks": [...], "notes": [...]}, both lists of raw LLM items."""
        ...


def build_system_prompt(now: datetime, as_note: bool = False) -> str:
    """`now` is when the user spoke (recorded_at), so "mai" stays right even for a recording sent late."""
    prompt = SYSTEM_PROMPT.replace("{now}", format_now_for_prompt(now))
    return f"{prompt}\n{AS_NOTE_LINE}" if as_note else prompt


class OpenAIParser:
    def __init__(self, client: openai.OpenAI, model: str):
        self.client, self.model = client, model

    def parse(self, text: str, now: datetime, as_note: bool = False) -> dict:
        try:
            resp = self.client.chat.completions.create(
                model=self.model,
                temperature=0,
                messages=[
                    {"role": "system", "content": build_system_prompt(now, as_note)},
                    {"role": "user", "content": text},
                ],
                response_format={
                    "type": "json_schema",
                    "json_schema": {"name": "tasks_and_notes", "strict": True, "schema": JSON_SCHEMA},
                },
            )
        except openai.OpenAIError as e:
            raise AIServiceError(type(e).__name__) from e

        content = resp.choices[0].message.content
        if not content:
            raise AIServiceError("EmptyResponse")
        try:
            data = json.loads(content)
            result = {"tasks": data["tasks"], "notes": data["notes"]}
        except (json.JSONDecodeError, KeyError, TypeError) as e:
            raise AIServiceError("InvalidJSON") from e
        if not all(isinstance(v, list) for v in result.values()):
            raise AIServiceError("InvalidJSON")
        return result


def get_parser() -> TaskParser:
    return OpenAIParser(openai_client(), get_settings().openai_parse_model)
