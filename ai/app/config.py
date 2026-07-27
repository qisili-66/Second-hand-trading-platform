import os
from dataclasses import dataclass
from functools import lru_cache
import logging

try:
    from dotenv import load_dotenv

    load_dotenv()
except Exception:
    pass

logger = logging.getLogger(__name__)


def _bounded_int(name: str, default: int, minimum: int, maximum: int) -> int:
    raw = os.getenv(name)
    if raw is None or not raw.strip():
        return default
    try:
        value = int(raw)
    except ValueError:
        logger.warning("invalid_integer_setting name=%s; using_default=%s", name, default)
        return default
    if value < minimum or value > maximum:
        logger.warning("out_of_range_setting name=%s; using_default=%s", name, default)
        return default
    return value


@dataclass
class Settings:
    qwen_api_key: str = os.getenv("QWEN_API_KEY", "")
    qwen_base_url: str = os.getenv(
        "QWEN_BASE_URL",
        "https://ws-2gca4xhi5wbpqj12.cn-beijing.maas.aliyuncs.com/compatible-mode/v1",
    )
    qwen_model: str = os.getenv("QWEN_MODEL", "qwen3.7-max")
    llm_timeout_seconds: int = _bounded_int("LLM_TIMEOUT_SECONDS", 18, 1, 120)
    llm_max_retries: int = _bounded_int("LLM_MAX_RETRIES", 1, 0, 2)
    llm_retry_backoff_ms: int = _bounded_int("LLM_RETRY_BACKOFF_MS", 250, 0, 5_000)
    llm_max_concurrent_requests: int = _bounded_int("LLM_MAX_CONCURRENT_REQUESTS", 4, 1, 32)
    llm_queue_timeout_ms: int = _bounded_int("LLM_QUEUE_TIMEOUT_MS", 100, 0, 5_000)
    llm_failure_threshold: int = _bounded_int("LLM_FAILURE_THRESHOLD", 3, 1, 20)
    llm_circuit_recovery_seconds: int = _bounded_int("LLM_CIRCUIT_RECOVERY_SECONDS", 30, 1, 600)

    db_host: str = os.getenv("DB_HOST", "127.0.0.1")
    db_port: int = int(os.getenv("DB_PORT", "3306"))
    db_name: str = os.getenv("DB_NAME", "second_hand_trade")
    db_user: str = os.getenv("DB_USER", "")
    db_password: str = os.getenv("DB_PASSWORD", "")


@lru_cache
def get_settings() -> Settings:
    return Settings()
