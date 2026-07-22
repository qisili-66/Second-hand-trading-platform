from app.agents.buyer_agent import BuyerAgent
from app.config import Settings
from app.schemas import AgentRequest


def test_buyer_agent_returns_wanted_draft_without_llm_or_db():
    settings = Settings(qwen_api_key="", db_password="invalid")
    agent = BuyerAgent(settings)

    response = agent.run(
        AgentRequest(message="我想买一个考研用的 iPad，预算 1500 左右，最好校本部面交。")
    )

    assert response.agent == "buyer"
    assert response.mode == "fallback"
    assert response.parsed_need.budget == 1500
    assert response.parsed_need.campus == "校本部"
    assert response.should_create_wanted is True
    assert response.wanted_draft is not None
    assert "iPad" in response.wanted_draft.title


def test_buyer_agent_generates_swap_draft_for_exchange_intent():
    settings = Settings(qwen_api_key="", db_password="invalid")
    agent = BuyerAgent(settings)

    response = agent.run(AgentRequest(message="我想用机械键盘换考研英语资料，校本部面交。"))

    assert response.swap_draft is not None
    assert "考研" in response.swap_draft.expected_title
    assert response.swap_draft.campus == "校本部"
