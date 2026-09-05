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
    # EXTERNAL_LLM_* is the only supported model-provider configuration.
    external_llm_api_key: str = os.getenv("EXTERNAL_LLM_API_KEY", "")
    external_llm_base_url: str = os.getenv("EXTERNAL_LLM_BASE_URL", "https://apihub.agnes-ai.com/v1")
    external_llm_model: str = os.getenv("EXTERNAL_LLM_MODEL", "agnes-2.0-flash")
    llm_timeout_seconds: int = _bounded_int("LLM_TIMEOUT_SECONDS", 18, 1, 120)
    llm_max_retries: int = _bounded_int("LLM_MAX_RETRIES", 1, 0, 2)
    llm_retry_backoff_ms: int = _bounded_int("LLM_RETRY_BACKOFF_MS", 250, 0, 5_000)
    llm_max_concurrent_requests: int = _bounded_int("LLM_MAX_CONCURRENT_REQUESTS", 4, 1, 32)
    llm_queue_timeout_ms: int = _bounded_int("LLM_QUEUE_TIMEOUT_MS", 100, 0, 5_000)
    llm_failure_threshold: int = _bounded_int("LLM_FAILURE_THRESHOLD", 3, 1, 20)
    llm_circuit_recovery_seconds: int = _bounded_int("LLM_CIRCUIT_RECOVERY_SECONDS", 30, 1, 600)

    backend_base_url: str = os.getenv("BACKEND_BASE_URL", "http://127.0.0.1:8080")
    agent_service_token: str = os.getenv("AGENT_SERVICE_TOKEN", "")
    agent_tool_timeout_seconds: int = _bounded_int("AGENT_TOOL_TIMEOUT_SECONDS", 3, 1, 10)
    agent_max_tool_calls: int = _bounded_int("AGENT_MAX_TOOL_CALLS", 6, 1, 6)
    agent_run_timeout_seconds: int = _bounded_int("AGENT_RUN_TIMEOUT_SECONDS", 25, 5, 60)
    qdrant_url: str = os.getenv("QDRANT_URL", "")
    qdrant_collection: str = os.getenv("QDRANT_COLLECTION", "campus_trade_knowledge")
    embedding_model: str = os.getenv("EMBEDDING_MODEL", "text-embedding-v4")
    embedding_provider: str = os.getenv("EMBEDDING_PROVIDER", "local").strip().lower()
    local_embedding_model: str = os.getenv("LOCAL_EMBEDDING_MODEL", "BAAI/bge-m3")
    local_embedding_device: str = os.getenv("LOCAL_EMBEDDING_DEVICE", "cpu")
    local_embedding_fallback: bool = os.getenv("LOCAL_EMBEDDING_FALLBACK", "false").strip().lower() in {"1", "true", "yes"}
    otel_service_name: str = os.getenv("OTEL_SERVICE_NAME", "campus-trade-ai")
    otel_exporter_otlp_endpoint: str = os.getenv("OTEL_EXPORTER_OTLP_ENDPOINT", "")
    otel_console_exporter: bool = os.getenv("OTEL_CONSOLE_EXPORTER", "false").strip().lower() in {"1", "true", "yes"}

@lru_cache
def get_settings() -> Settings:
    return Settings()
