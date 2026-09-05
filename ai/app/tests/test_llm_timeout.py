import time

from app.config import Settings
from app.llm import ExternalLLM
from app.schemas import BuyerAgentResponse


class SlowExternalLLM(ExternalLLM):
    def _complete_json_sync(self, system_prompt, user_prompt, output_model):
        time.sleep(2)
        return None


def test_llm_returns_none_when_hard_timeout_is_reached():
    llm = SlowExternalLLM(Settings(external_llm_api_key="test-key", llm_timeout_seconds=1))

    started_at = time.monotonic()
    response = llm.complete_json("system", "user", BuyerAgentResponse)
    elapsed = time.monotonic() - started_at

    assert response is None
    assert elapsed < 1.5
