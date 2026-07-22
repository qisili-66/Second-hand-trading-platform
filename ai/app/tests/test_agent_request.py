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


def test_buyer_endpoint_accepts_prompt_payload(monkeypatch):
    monkeypatch.setattr(main, "_settings", lambda: Settings(qwen_api_key="", db_password="invalid"))
    client = TestClient(main.app)

    response = client.post("/agents/buyer", json={"prompt": "我想买一个考研用的 iPad，预算 1500"})

    assert response.status_code == 200
    assert response.json()["agent"] == "buyer"


def test_buyer_endpoint_returns_clear_error_without_body():
    client = TestClient(main.app)

    response = client.post("/agents/buyer")

    assert response.status_code == 400
    assert response.json()["detail"] == "请先输入 Agent 需求内容"


def test_seller_endpoint_accepts_user_message_payload(monkeypatch):
    monkeypatch.setattr(main, "_settings", lambda: Settings(qwen_api_key="", db_password="invalid"))
    client = TestClient(main.app)

    response = client.post("/agents/seller", json={"userMessage": "出一个宿舍小冰箱，八成新"})

    assert response.status_code == 200
    assert response.json()["agent"] == "seller"
