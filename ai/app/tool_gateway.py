from __future__ import annotations

import json
import time
from dataclasses import dataclass, field
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from app.config import Settings


@dataclass
class ToolGateway:
    """Calls Spring's authenticated internal, read-only tool endpoints."""

    settings: Settings
    user_id: int | None
    run_id: str = ""
    deadline: float | None = None
    steps: list[dict[str, Any]] = field(default_factory=list)
    latest_search: list[dict[str, Any]] = field(default_factory=list)

    def call(self, tool: str, payload: dict[str, Any]) -> Any:
        started = time.monotonic()
        request_payload = {**payload, "userId": self.user_id, "runId": self.run_id}
        try:
            from opentelemetry import trace
            span_context = trace.get_tracer("campus-agent").start_as_current_span(f"agent.tool.{tool}")
        except Exception:
            span_context = _NoopSpan()
        with span_context as span:
            try:
                result = self._post(tool, request_payload)
                if tool == "search-items" and isinstance(result, dict):
                    self.latest_search = list(result.get("items") or [])
                self._step(tool, request_payload, result, "SUCCEEDED", started)
                span.set_attribute("agent.tool.status", "SUCCEEDED")
                span.set_attribute("agent.tool.duration_ms", self.steps[-1]["durationMs"])
                return result
            except Exception as exc:
                span.set_attribute("agent.tool.error", type(exc).__name__)
                self._step(tool, request_payload, {}, "FAILED", started, self._error_code(exc))
                span.set_attribute("agent.tool.status", "FAILED")
                span.set_attribute("agent.tool.duration_ms", self.steps[-1]["durationMs"])
                return {"error": "工具暂时不可用", "source": "unavailable"}

    def _post(self, tool: str, payload: dict[str, Any]) -> Any:
        if not self.settings.agent_service_token.strip():
            raise RuntimeError("agent_service_token_not_configured")
        remaining = self._remaining_seconds()
        if remaining is not None and remaining <= 0:
            raise RuntimeError("agent_run_timeout")
        url = f"{self.settings.backend_base_url.rstrip('/')}/api/internal/agent-tools/{tool}"
        request = Request(
            url,
            data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            headers={
                "Content-Type": "application/json; charset=utf-8",
                "X-Agent-Service-Token": self.settings.agent_service_token,
            },
            method="POST",
        )
        try:
            timeout = self.settings.agent_tool_timeout_seconds if remaining is None else min(
                self.settings.agent_tool_timeout_seconds, remaining
            )
            with urlopen(request, timeout=max(timeout, 0.01)) as response:
                raw = json.loads(response.read().decode("utf-8"))
        except (HTTPError, URLError, TimeoutError) as exc:
            raise RuntimeError("tool_gateway_unavailable") from exc
        if raw.get("code") != 0 or raw.get("data") is None:
            raise RuntimeError("tool_gateway_invalid_response")
        return raw["data"]

    def _remaining_seconds(self) -> float | None:
        return None if self.deadline is None else self.deadline - time.monotonic()

    @staticmethod
    def _error_code(exc: Exception) -> str:
        known_codes = {
            "agent_service_token_not_configured",
            "agent_run_timeout",
            "tool_gateway_unavailable",
            "tool_gateway_invalid_response",
        }
        return str(exc) if str(exc) in known_codes else type(exc).__name__

    def _step(
        self,
        tool: str,
        payload: dict[str, Any],
        result: dict[str, Any],
        status: str,
        started: float,
        error_code: str = "",
    ) -> None:
        self.steps.append(
            {
                "type": "tool",
                "tool": tool.replace("-", "_"),
                "input": json.dumps(self._redact(payload), ensure_ascii=False),
                "output": json.dumps(self._redact(result), ensure_ascii=False)[:1200],
                "status": status,
                "durationMs": int((time.monotonic() - started) * 1000),
                "errorCode": error_code,
            }
        )

    def _redact(self, value: Any) -> Any:
        if isinstance(value, dict):
            return {key: "[redacted]" if key.lower() in {"token", "phone", "email", "studentno"} else self._redact(item) for key, item in value.items()}
        if isinstance(value, list):
            return [self._redact(item) for item in value]
        return value


class _NoopSpan:
    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return False

    def set_attribute(self, *_args) -> None:
        return None
