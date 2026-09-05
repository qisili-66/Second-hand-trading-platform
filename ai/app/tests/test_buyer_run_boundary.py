import time

from app.agents.buyer_run_agent import BuyerRunAgent
from app.agents.buyer_run_agent import BuyerGraphState
from app.config import Settings
from app.schemas import AgentRequest
from app.tool_gateway import ToolGateway
from langgraph.graph import StateGraph


class ToolCallMessage:
    def __init__(self, tool_calls):
        self.tool_calls = tool_calls

    def model_copy(self, update):
        return ToolCallMessage(update["tool_calls"])


def test_langgraph_can_resolve_the_buyer_graph_state_annotations():
    graph = StateGraph(BuyerGraphState)

    assert "messages" in graph.channels
    assert "calls" in graph.channels


def test_buyer_run_uses_only_read_only_tool_names():
    agent = BuyerRunAgent(Settings(external_llm_api_key="", agent_service_token=""))
    response = agent.run(AgentRequest(message="想买 iPad", user_id=7), "run-1", "trace-1")

    assert response.steps
    assert {step.tool for step in response.steps} <= {"search_items", "user_preferences"}
    assert all(step.status == "FAILED" for step in response.steps)


def test_graph_tool_limit_is_hard_capped_at_six():
    agent = BuyerRunAgent(Settings(external_llm_api_key="key", agent_max_tool_calls=99))

    assert agent._tool_call_limit() == 6
    assert BuyerRunAgent.READ_ONLY_TOOL_NAMES == {
        "search-items",
        "item-realtime",
        "seller-summary",
        "order-status",
        "user-preferences",
        "trade-rules",
        "product-knowledge",
    }
    assert agent._remaining_tool_call_limit(2) == 4


def test_graph_trims_a_single_model_response_to_the_remaining_tool_budget():
    message = ToolCallMessage([{"id": str(index)} for index in range(7)])

    limited = BuyerRunAgent._limit_tool_calls(message, 6)

    assert len(limited.tool_calls) == 6


def test_expired_run_deadline_records_a_failed_tool_step_without_network_access():
    gateway = ToolGateway(
        Settings(agent_service_token="internal-token"),
        user_id=7,
        deadline=time.monotonic() - 1,
    )

    result = gateway.call("search-items", {"keyword": "iPad"})

    assert result["source"] == "unavailable"
    assert gateway.steps[0]["status"] == "FAILED"
    assert gateway.steps[0]["errorCode"] == "agent_run_timeout"
