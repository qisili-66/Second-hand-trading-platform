from __future__ import annotations

from fastapi import Body, FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from app.agents.buyer_agent import BuyerAgent
from app.agents.seller_agent import SellerAgent
from app.config import Settings, get_settings
from app.llm import QwenLLM
from app.schemas import AgentRequest, BuyerAgentResponse, HealthResponse, SellerAgentResponse

app = FastAPI(title="Campus Trade AI", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://127.0.0.1:5173"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


def _settings() -> Settings:
    return get_settings()


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    settings = _settings()
    return HealthResponse(model=settings.qwen_model, llm_configured=QwenLLM(settings).configured)


@app.post("/agents/buyer", response_model=BuyerAgentResponse)
def buyer_agent(request: AgentRequest | None = Body(default=None)) -> BuyerAgentResponse:
    if request is None:
        raise HTTPException(status_code=400, detail="请先输入 Agent 需求内容")
    settings = _settings()
    return BuyerAgent(settings).run(request)


@app.post("/agents/seller", response_model=SellerAgentResponse)
def seller_agent(request: AgentRequest | None = Body(default=None)) -> SellerAgentResponse:
    if request is None:
        raise HTTPException(status_code=400, detail="请先输入 Agent 需求内容")
    settings = _settings()
    return SellerAgent(settings).run(request)
