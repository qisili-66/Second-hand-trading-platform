from app.agents.buyer_agent import BuyerAgent
from app.config import Settings
from app.schemas import AgentRequest


def test_buyer_agent_returns_safe_read_only_fallback_without_llm_or_db():
    settings = Settings(external_llm_api_key="")
    agent = BuyerAgent(settings)

    response = agent._fallback(
        AgentRequest(message="我想买一个考研用的 iPad，预算 1500 左右，最好校本部面交。")
    )

    assert response.agent == "buyer"
    assert response.mode == "fallback"
    assert response.parsed_need.budget == 1500
    assert response.parsed_need.campus == "校本部"
    assert response.recommendations == []
    assert "当前没有匹配的在售商品" in response.summary
    payload = response.model_dump()
    assert not {"chat_draft", "wanted_draft", "swap_draft", "should_create_wanted"} & set(payload)
    assert all("写操作" not in action for action in response.next_actions)
