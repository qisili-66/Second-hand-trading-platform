import os
from dataclasses import dataclass
from functools import lru_cache

try:
    from dotenv import load_dotenv

    load_dotenv()
except Exception:
    pass


@dataclass
class Settings:
    qwen_api_key: str = os.getenv("QWEN_API_KEY", "")
    qwen_base_url: str = os.getenv(
        "QWEN_BASE_URL",
        "https://ws-2gca4xhi5wbpqj12.cn-beijing.maas.aliyuncs.com/compatible-mode/v1",
    )
    qwen_model: str = os.getenv("QWEN_MODEL", "qwen3.7-max")
    llm_timeout_seconds: int = int(os.getenv("LLM_TIMEOUT_SECONDS", "18"))

    db_host: str = os.getenv("DB_HOST", "127.0.0.1")
    db_port: int = int(os.getenv("DB_PORT", "3306"))
    db_name: str = os.getenv("DB_NAME", "second_hand_trade")
    db_user: str = os.getenv("DB_USER", "root")
    db_password: str = os.getenv("DB_PASSWORD", "root")


@lru_cache
def get_settings() -> Settings:
    return Settings()
