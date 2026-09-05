import threading
import time

import pytest

from app.config import Settings
from app.llm import ExternalLLM, LLMInvocationError, LLMResponseFormatError, LLMRuntime
from app.schemas import BuyerAgentResponse


def settings(**overrides):
    values = {
        "external_llm_api_key": "test-key",
        "llm_timeout_seconds": 1,
        "llm_max_retries": 0,
        "llm_queue_timeout_ms": 10,
        "llm_failure_threshold": 2,
        "llm_circuit_recovery_seconds": 30,
    }
    values.update(overrides)
    return Settings(**values)


class FailingExternalLLM(ExternalLLM):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.calls = 0

    def _complete_json_sync(self, system_prompt, user_prompt, output_model):
        self.calls += 1
        raise LLMInvocationError("upstream_unavailable")


class BlockingExternalLLM(ExternalLLM):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.started = threading.Event()
        self.release = threading.Event()

    def _complete_json_sync(self, system_prompt, user_prompt, output_model):
        self.started.set()
        self.release.wait(timeout=3)
        return None


def test_circuit_opens_after_bounded_failures_and_skips_future_calls():
    runtime = LLMRuntime(max_concurrent_requests=1, failure_threshold=2, recovery_seconds=30)
    llm = FailingExternalLLM(settings(), runtime=runtime)

    assert llm.complete_json("system", "user", BuyerAgentResponse) is None
    assert llm.complete_json("system", "user", BuyerAgentResponse) is None
    assert llm.runtime_status.state == "OPEN"

    assert llm.complete_json("system", "user", BuyerAgentResponse) is None
    assert llm.calls == 2


def test_busy_runtime_returns_fallback_without_starting_another_upstream_call():
    runtime = LLMRuntime(max_concurrent_requests=1, failure_threshold=3, recovery_seconds=30)
    llm = BlockingExternalLLM(settings(llm_timeout_seconds=2), runtime=runtime)
    first_call = threading.Thread(
        target=lambda: llm.complete_json("system", "user", BuyerAgentResponse), daemon=True
    )
    first_call.start()
    assert llm.started.wait(timeout=0.5)

    started_at = time.monotonic()
    result = llm.complete_json("system", "user", BuyerAgentResponse)
    elapsed = time.monotonic() - started_at

    assert result is None
    assert elapsed < 0.2
    llm.release.set()
    first_call.join(timeout=1)


def test_invalid_json_is_reported_as_a_format_error_without_guessing():
    llm = ExternalLLM(settings(), runtime=LLMRuntime(1, 3, 30))

    with pytest.raises(LLMResponseFormatError):
        llm._extract_json("这不是 JSON，也不包含对象")
