from functools import lru_cache

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    device_token: str
    database_url: str
    openai_api_key: str = ""
    openai_stt_model: str = "gpt-4o-mini-transcribe"
    openai_stt_language: str = ""  # "" = auto-detect (Vietnamese + English); "vi" = Vietnamese only
    openai_parse_model: str = ""
    openai_timeout_s: float = 20.0
    max_upload_bytes: int = 10 * 1024 * 1024


@lru_cache
def get_settings() -> Settings:
    return Settings()
