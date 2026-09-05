from __future__ import annotations

from secrets import compare_digest

from fastapi import Body, FastAPI, Header, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from app.agents.buyer_run_agent import BuyerRunAgent
from app.config import Settings, get_settings
from app.llm import ExternalLLM
from app.observability import configure_observability
from app.schemas import AgentRequest, BuyerAgentResponse, HealthResponse

app = FastAPI(title="Campus Trade AI", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://127.0.0.1:5173"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

configure_observability(get_settings())


def _settings() -> Settings:
    return get_settings()


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    settings = _settings()
    llm = ExternalLLM(settings)
    runtime = llm.runtime_status
    return HealthResponse(
        model=settings.external_llm_model,
        llm_configured=llm.configured,
        llm_circuit_state=runtime.state,
        llm_consecutive_failures=runtime.consecutive_failures,
        llm_max_concurrent_requests=runtime.max_concurrent_requests,
    )


@app.post("/agents/buyer/runs", response_model=BuyerAgentResponse)
def buyer_agent_run(
    request: AgentRequest | None = Body(default=None),
    service_token: str | None = Header(default=None, alias="X-Agent-Service-Token"),
) -> BuyerAgentResponse:
    settings = _settings()
    if not settings.agent_service_token.strip() or not service_token or not compare_digest(
        settings.agent_service_token, service_token
    ):
        raise HTTPException(status_code=401, detail="Agent service authentication failed")
    if request is None:
        raise HTTPException(status_code=400, detail="请先输入 Agent 需求内容")
    return BuyerRunAgent(settings).run(
        request,
        request.run_id,
        request.trace_id,
    )
