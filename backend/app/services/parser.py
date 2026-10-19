import json
from datetime import datetime
from typing import Protocol

import openai

from app.config import get_settings
from app.services.errors import AIServiceError
from app.services.stt import openai_client
from app.timeutil import format_now_for_prompt

# Mirrors SPEC.md "Pipeline AI". Change only together with the openai test suite.
SYSTEM_PROMPT = """Bạn tách câu nói tiếng Việt hoặc tiếng Anh thành danh sách việc cần làm.
Bây giờ là {now} (giờ Việt Nam).
Quy tắc:
- Mỗi việc riêng là 1 mục. Một câu có thể có nhiều việc.
- title: ngắn gọn, viết hoa chữ đầu, giữ nguyên ngôn ngữ người nói, bỏ các từ chỉ thời gian, nhắc nhở và mức độ quan trọng ("nhắc tôi", "mai", "lúc 9 giờ", "quan trọng", "remind me", "tomorrow", "at 3pm").
- due_date (YYYY-MM-DD): "hôm nay", "mai"/"ngày mai", "ngày kia"/"mốt", "thứ 2".."thứ 7", "chủ nhật" (lần gần nhất sau hôm nay), "tuần sau" (thứ Hai tuần sau), "cuối tuần" (thứ Bảy tới), "20/10", "ngày 25". Tiếng Anh: "today", "tomorrow", "Friday"/"on Friday" (lần gần nhất sau hôm nay), "next week" (thứ Hai tuần sau), "this weekend" (thứ Bảy tới), "Oct 25"/"the 25th". Không nói -> null.
- remind_at (YYYY-MM-DDTHH:MM, giờ Việt Nam): chỉ khi có giờ cụ thể hoặc có chữ "nhắc". "9 giờ sáng" = 09:00, "3 giờ chiều" = 15:00, "8 giờ tối" = 20:00, "9 rưỡi" = 09:30, "10 giờ kém 15" = 09:45; "3pm" = 15:00, "9:30 am" = 09:30, "noon" = 12:00. Có "nhắc"/"remind" mà chỉ nói buổi: sáng/morning = 08:00, trưa = 12:00, chiều/afternoon = 15:00, tối/evening/tonight = 20:00. Có giờ mà không có ngày: so với giờ bây giờ, giờ đó chưa tới -> hôm nay, đã qua -> ngày mai (ví dụ bây giờ 14:30: "4 giờ chiều" -> hôm nay 16:00, "10 giờ sáng" -> ngày mai 10:00). Không nói -> null.
- Có remind_at mà không có due_date -> due_date = ngày của remind_at.
- starred: true nếu nói "quan trọng", "gấp", "ưu tiên", "đánh dấu sao", "important", "urgent", "star it" cho việc đó; ngược lại false.
- Chỉ tạo việc khi người nói muốn làm hoặc được nhắc làm một việc. Câu kể, nhận xét, thời tiết ("hôm nay trời đẹp quá", "the weather is nice today") không phải việc. Không có việc nào -> trả tasks rỗng."""

JSON_SCHEMA = {
    "type": "object",
    "additionalProperties": False,
    "required": ["tasks"],
    "properties": {
        "tasks": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": ["title", "due_date", "remind_at", "starred"],
                "properties": {
                    "title": {"type": "string"},
                    "due_date": {"type": ["string", "null"]},
                    "remind_at": {"type": ["string", "null"]},
                    "starred": {"type": "boolean"},
                },
            },
        }
    },
}


class TaskParser(Protocol):
    def parse(self, text: str, now: datetime) -> list[dict]: ...


def build_system_prompt(now: datetime) -> str:
    """`now` is when the user spoke (recorded_at), so "mai" stays right even for a recording sent late."""
    return SYSTEM_PROMPT.replace("{now}", format_now_for_prompt(now))


class OpenAIParser:
    def __init__(self, client: openai.OpenAI, model: str):
        self.client, self.model = client, model

    def parse(self, text: str, now: datetime) -> list[dict]:
        try:
            resp = self.client.chat.completions.create(
                model=self.model,
                temperature=0,
                messages=[
                    {"role": "system", "content": build_system_prompt(now)},
                    {"role": "user", "content": text},
                ],
                response_format={
                    "type": "json_schema",
                    "json_schema": {"name": "tasks", "strict": True, "schema": JSON_SCHEMA},
                },
            )
        except openai.OpenAIError as e:
            raise AIServiceError(type(e).__name__) from e

        content = resp.choices[0].message.content
        if not content:
            raise AIServiceError("EmptyResponse")
        try:
            return json.loads(content)["tasks"]
        except (json.JSONDecodeError, KeyError, TypeError) as e:
            raise AIServiceError("InvalidJSON") from e


def get_parser() -> TaskParser:
    return OpenAIParser(openai_client(), get_settings().openai_parse_model)
