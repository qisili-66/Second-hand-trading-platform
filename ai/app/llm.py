from __future__ import annotations

import json
import queue
import threading
from typing import Any, TypeVar
from pydantic import BaseModel
from .config import Settings
from .pydantic_compat import validate_model

T = TypeVar("T", bound=BaseModel)


class QwenLLM:
    def __init__(self, settings: Settings):
        self.settings = settings

    @property
    def configured(self) -> bool:
        return bool(self.settings.qwen_api_key.strip())

    def complete_json(self, system_prompt: str, user_prompt: str, output_model: type[T]) -> T | None:
        if not self.configured:
            return None
        result_queue: queue.Queue[T | None] = queue.Queue(maxsize=1)

        def invoke_llm() -> None:
            result_queue.put(self._complete_json_sync(system_prompt, user_prompt, output_model))

        thread = threading.Thread(target=invoke_llm, daemon=True)
        thread.start()
        thread.join(timeout=max(self.settings.llm_timeout_seconds, 1))
        if thread.is_alive():
            return None
        try:
            return result_queue.get_nowait()
        except queue.Empty:
            return None

    def _complete_json_sync(self, system_prompt: str, user_prompt: str, output_model: type[T]) -> T | None:
        try:
            from langchain_openai import ChatOpenAI
        except Exception:
            return None

        try:
            llm = ChatOpenAI(
                api_key=self.settings.qwen_api_key,
                base_url=self.settings.qwen_base_url,
                model=self.settings.qwen_model,
                temperature=0.2,
                timeout=self.settings.llm_timeout_seconds,
            )
            message = llm.invoke([
                ("system", system_prompt),
                ("user", user_prompt + "\n\n只返回 JSON，不要 Markdown，不要解释。"),
            ])
            data = self._extract_json(getattr(message, "content", ""))
            return validate_model(output_model, data)
        except Exception:
            return None

    def _extract_json(self, text: str) -> dict[str, Any]:
        raw = text.strip()
        if raw.startswith("```"):
            raw = raw.strip("`")
            raw = raw.removeprefix("json").strip()
        start = raw.find("{")
        end = raw.rfind("}")
        if start >= 0 and end >= start:
            raw = raw[start : end + 1]
        return json.loads(raw)
