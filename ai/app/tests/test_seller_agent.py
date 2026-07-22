from app.agents.seller_agent import SellerAgent
from app.config import Settings
from app.schemas import AgentRequest


def test_seller_agent_generates_publish_draft_without_llm():
    settings = Settings(qwen_api_key="")
    agent = SellerAgent(settings)

    response = agent.run(AgentRequest(message="出一个宿舍小冰箱，八成新，毕业搬宿舍用不上了。"))

    assert response.agent == "seller"
    assert response.mode == "fallback"
    assert response.draft.category == "生活日用"
    assert response.draft.condition == "8成新"
    assert "冰箱" in response.draft.title
    assert response.risk_tips
    assert response.next_actions


def test_seller_agent_marks_swap_supported_when_requested():
    settings = Settings(qwen_api_key="")
    agent = SellerAgent(settings)

    response = agent.run(AgentRequest(message="出一个机械键盘，9成新，可换考研资料或补差价。"))

    assert response.draft.swap_supported is True
