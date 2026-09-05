from __future__ import annotations

import json
import logging
import re
import threading
import time
from concurrent.futures import Future, ThreadPoolExecutor, TimeoutError as FutureTimeoutError
from dataclasses import dataclass, field
from typing import Any, ClassVar, TypeVar

from pydantic import BaseModel, ValidationError

from .config import Settings
from .pydantic_compat import validate_model

T = TypeVar("T", bound=BaseModel)

logger = logging.getLogger(__name__)


class LLMInvocationError(Exception):
    """A transient upstream model failure that can be retried within the time budget."""


class LLMResponseFormatError(Exception):
    """A non-retryable response that cannot be safely mapped to the requested schema."""


@dataclass(frozen=True)
class LLMRuntimeStatus:
    state: str
    consecutive_failures: int
    max_concurrent_requests: int


@dataclass
class LLMRuntime:
    max_concurrent_requests: int
    failure_threshold: int
    recovery_seconds: int
    _slots: threading.BoundedSemaphore = field(init=False, repr=False)
    _executor: ThreadPoolExecutor = field(init=False, repr=False)
    _lock: threading.Lock = field(default_factory=threading.Lock, init=False, repr=False)
    _consecutive_failures: int = field(default=0, init=False)
    _open_until: float = field(default=0.0, init=False)
    _half_open_probe_running: bool = field(default=False, init=False)

    def __post_init__(self) -> None:
        self._slots = threading.BoundedSemaphore(self.max_concurrent_requests)
        self._executor = ThreadPoolExecutor(
            max_workers=self.max_concurrent_requests,
            thread_name_prefix="campus-agent-llm",
        )

    def allow_request(self) -> bool:
        now = time.monotonic()
        with self._lock:
            if self._open_until <= 0:
                return True
            if now < self._open_until:
                return False
            if self._half_open_probe_running:
                return False
            self._half_open_probe_running = True
            return True

    def cancel_half_open_probe(self) -> bool:
        with self._lock:
            was_half_open = self._half_open_probe_running
            self._half_open_probe_running = False
            return was_half_open

    def try_acquire_slot(self, timeout_seconds: float) -> bool:
        return self._slots.acquire(timeout=max(timeout_seconds, 0.0))

    def submit(self, task: Any) -> Future[Any]:
        return self._executor.submit(task)

    def release_slot(self) -> None:
        self._slots.release()

    def record_success(self) -> None:
        with self._lock:
            self._consecutive_failures = 0
            self._open_until = 0.0
            self._half_open_probe_running = False

    def record_failure(self) -> bool:
        now = time.monotonic()
        with self._lock:
            self._consecutive_failures += 1
            self._half_open_probe_running = False
            if self._consecutive_failures >= self.failure_threshold:
                self._open_until = now + self.recovery_seconds
                return True
            return False

    def status(self) -> LLMRuntimeStatus:
        now = time.monotonic()
        with self._lock:
            if self._open_until > now:
                state = "OPEN"
            elif self._open_until > 0 or self._half_open_probe_running:
                state = "HALF_OPEN"
            else:
                state = "CLOSED"
            return LLMRuntimeStatus(state, self._consecutive_failures, self.max_concurrent_requests)


class ExternalLLM:
    _runtimes: ClassVar[dict[tuple[str, str, int, int, int], LLMRuntime]] = {}
    _runtimes_lock: ClassVar[threading.Lock] = threading.Lock()

    def __init__(self, settings: Settings, runtime: LLMRuntime | None = None):
        self.settings = settings
        self.runtime = runtime or self._shared_runtime(settings)

    @classmethod
    def _shared_runtime(cls, settings: Settings) -> LLMRuntime:
        key = (
            settings.external_llm_base_url,
            settings.external_llm_model,
            settings.llm_max_concurrent_requests,
            settings.llm_failure_threshold,
            settings.llm_circuit_recovery_seconds,
        )
        with cls._runtimes_lock:
            runtime = cls._runtimes.get(key)
            if runtime is None:
                runtime = LLMRuntime(
                    max_concurrent_requests=settings.llm_max_concurrent_requests,
                    failure_threshold=settings.llm_failure_threshold,
                    recovery_seconds=settings.llm_circuit_recovery_seconds,
                )
                cls._runtimes[key] = runtime
            return runtime

    @classmethod
    def reset_shared_runtimes_for_test(cls) -> None:
        with cls._runtimes_lock:
            runtimes = list(cls._runtimes.values())
            cls._runtimes.clear()
        for runtime in runtimes:
            runtime._executor.shutdown(wait=False, cancel_futures=True)

    @property
    def configured(self) -> bool:
        api_key = self.settings.external_llm_api_key.strip()
        return bool(api_key) and not api_key.startswith("replace_with_")

    @property
    def runtime_status(self) -> LLMRuntimeStatus:
        return self.runtime.status()

    def complete_json(self, system_prompt: str, user_prompt: str, output_model: type[T]) -> T | None:
        if not self.configured:
            return None
        if not self.runtime.allow_request():
            logger.warning("llm_circuit_open model=%s", self.settings.external_llm_model)
            return None

        deadline = time.monotonic() + self.settings.llm_timeout_seconds
        for attempt in range(self.settings.llm_max_retries + 1):
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                return self._fail("timeout")
            if not self.runtime.try_acquire_slot(min(remaining, self.settings.llm_queue_timeout_ms / 1000)):
                was_half_open_probe = self.runtime.cancel_half_open_probe()
                logger.warning("llm_overloaded model=%s", self.settings.external_llm_model)
                if was_half_open_probe:
                    return self._fail("half_open_overloaded")
                return None

            future = self.runtime.submit(
                lambda: self._run_attempt(system_prompt, user_prompt, output_model)
            )
            try:
                result = future.result(timeout=max(deadline - time.monotonic(), 0.01))
                if result is None:
                    raise LLMInvocationError("empty_response")
                self.runtime.record_success()
                return result
            except FutureTimeoutError:
                logger.warning("llm_timeout model=%s attempt=%s", self.settings.external_llm_model, attempt + 1)
                return self._fail("timeout")
            except LLMResponseFormatError:
                logger.warning("llm_invalid_response_format model=%s", self.settings.external_llm_model)
                return self._fail("invalid_format")
            except LLMInvocationError as exc:
                if attempt >= self.settings.llm_max_retries or deadline - time.monotonic() <= 0:
                    return self._fail("upstream_error")
                delay = min(
                    self.settings.llm_retry_backoff_ms / 1000 * (2**attempt),
                    max(deadline - time.monotonic(), 0),
                )
                logger.warning("llm_retry model=%s attempt=%s reason=%s", self.settings.external_llm_model, attempt + 1, exc)
                if delay > 0:
                    time.sleep(delay)
        return self._fail("retry_exhausted")

    def _run_attempt(self, system_prompt: str, user_prompt: str, output_model: type[T]) -> T | None:
        try:
            return self._complete_json_sync(system_prompt, user_prompt, output_model)
        finally:
            self.runtime.release_slot()

    def _fail(self, reason: str) -> None:
        circuit_opened = self.runtime.record_failure()
        logger.warning(
            "llm_fallback model=%s reason=%s circuit_opened=%s",
            self.settings.external_llm_model,
            reason,
            circuit_opened,
        )
        return None

    def _complete_json_sync(self, system_prompt: str, user_prompt: str, output_model: type[T]) -> T | None:
        try:
            from langchain_openai import ChatOpenAI
        except Exception as exc:
            raise LLMInvocationError("dependency_unavailable") from exc

        try:
            llm = ChatOpenAI(
                api_key=self.settings.external_llm_api_key,
                base_url=self.settings.external_llm_base_url,
                model=self.settings.external_llm_model,
                temperature=0.2,
                timeout=self.settings.llm_timeout_seconds,
                max_retries=0,
            )
            message = llm.invoke([
                ("system", system_prompt),
                ("user", user_prompt + "\n\n只返回 JSON，不要 Markdown，不要解释。"),
            ])
            data = self._extract_json(getattr(message, "content", ""))
            return validate_model(output_model, data)
        except LLMResponseFormatError:
            raise
        except (json.JSONDecodeError, ValidationError, TypeError, ValueError) as exc:
            raise LLMResponseFormatError("invalid_json_schema") from exc
        except Exception as exc:
            raise LLMInvocationError(type(exc).__name__) from exc

    def _extract_json(self, text: Any) -> dict[str, Any]:
        raw = self._content_text(text).strip()
        fenced = re.fullmatch(r"```(?:json)?\s*(.*?)\s*```", raw, flags=re.IGNORECASE | re.DOTALL)
        if fenced:
            raw = fenced.group(1).strip()
        try:
            data = json.loads(raw)
        except json.JSONDecodeError:
            start = raw.find("{")
            end = raw.rfind("}")
            if start < 0 or end < start:
                raise LLMResponseFormatError("json_object_not_found")
            data = json.loads(raw[start : end + 1])
        if not isinstance(data, dict):
            raise LLMResponseFormatError("json_object_expected")
        return data

    def _content_text(self, content: Any) -> str:
        if isinstance(content, str):
            return content
        if isinstance(content, list):
            return "".join(
                part.get("text", "") if isinstance(part, dict) else str(part)
                for part in content
            )
        return str(content or "")
