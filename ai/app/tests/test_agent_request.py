from fastapi.testclient import TestClient

import app.main as main
from app.config import Settings
from app.pydantic_compat import dump_json
from app.schemas import AgentRequest


def test_agent_request_accepts_common_message_aliases():
    for key in ["message", "prompt", "input", "content", "query", "text", "userMessage"]:
        request = AgentRequest.model_validate({key: "我想买一个考研用的 iPad，预算 1500", "userId": 7})

        assert request.message == "我想买一个考研用的 iPad，预算 1500"
        assert request.user_id == 7


def test_dump_json_serializes_pydantic_models_without_ascii_escaping():
    payload = dump_json(AgentRequest(message="我想买 iPad"))

    assert "我想买 iPad" in payload


def test_legacy_buyer_and_seller_endpoints_are_removed():
    client = TestClient(main.app)

    assert client.post("/agents/buyer", json={"message": "想买 iPad"}).status_code == 404
    assert client.post("/agents/seller", json={"message": "发布冰箱"}).status_code == 404


def test_buyer_run_requires_a_request_body_after_service_authentication(monkeypatch):
    monkeypatch.setattr(main, "_settings", lambda: Settings(agent_service_token="internal-token"))
    client = TestClient(main.app)

    response = client.post("/agents/buyer/runs", headers={"X-Agent-Service-Token": "internal-token"})

    assert response.status_code == 400
    assert response.json()["detail"] == "请先输入 Agent 需求内容"


def test_buyer_run_rejects_missing_or_incorrect_service_token(monkeypatch):
    monkeypatch.setattr(main, "_settings", lambda: Settings(agent_service_token="internal-token"))
    client = TestClient(main.app)

    payload = {
        "message": "想买考研 iPad，预算 1500",
        "userId": 7,
        "runId": "run-123",
        "traceId": "trace-123",
    }
    assert client.post("/agents/buyer/runs", json=payload).status_code == 401
    assert client.post("/agents/buyer/runs", json=payload, headers={"X-Agent-Service-Token": "wrong"}).status_code == 401


def test_buyer_run_returns_auditable_rule_fallback_when_tool_token_is_missing(monkeypatch):
    monkeypatch.setattr(main, "_settings", lambda: Settings(external_llm_api_key="", agent_service_token="internal-token"))
    client = TestClient(main.app)

    response = client.post("/agents/buyer/runs", json={
        "message": "想买考研 iPad，预算 1500",
        "userId": 7,
        "runId": "run-123",
        "traceId": "trace-123",
    }, headers={"X-Agent-Service-Token": "internal-token"})

    assert response.status_code == 200
    body = response.json()
    assert body["mode"] == "fallback"
    assert body["run_id"] == "run-123"
    assert body["trace_id"] == "trace-123"
    assert body["steps"][0]["status"] == "FAILED"
    assert body["recommendations"] == []
    assert not {"chat_draft", "wanted_draft", "swap_draft", "should_create_wanted"} & set(body)
