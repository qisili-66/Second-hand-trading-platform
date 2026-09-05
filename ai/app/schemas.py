from typing import Any, Literal
from pydantic import AliasChoices, BaseModel, ConfigDict, Field


class AgentRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    message: str = Field(
        ...,
        min_length=1,
        max_length=1000,
        validation_alias=AliasChoices("message", "prompt", "input", "content", "query", "text", "userMessage"),
    )
    campus: str | None = Field(default=None, validation_alias=AliasChoices("campus", "campusName"))
    user_id: int | None = Field(default=None, validation_alias=AliasChoices("user_id", "userId"))
    context: dict[str, Any] = Field(default_factory=dict)
    run_id: str = Field(default="", validation_alias=AliasChoices("run_id", "runId"))
    trace_id: str = Field(default="", validation_alias=AliasChoices("trace_id", "traceId"))


class ParsedNeed(BaseModel):
    keyword: str = ""
    budget: int | None = None
    campus: str = ""
    usage: str = ""


class ItemRecommendation(BaseModel):
    item_id: int | None = None
    title: str
    price: float | None = None
    campus: str = ""
    condition: str = ""
    reason: str
    risk: str
    bargain_range: str


class AgentStep(BaseModel):
    type: str = "tool"
    tool: str = ""
    input: str = ""
    output: str = ""
    status: str = "SUCCEEDED"
    duration_ms: int = Field(default=0, validation_alias=AliasChoices("duration_ms", "durationMs"))
    error_code: str = Field(default="", validation_alias=AliasChoices("error_code", "errorCode"))


class KnowledgeCitation(BaseModel):
    source_id: str = Field(default="", validation_alias=AliasChoices("source_id", "sourceId"))
    source_type: str = Field(default="", validation_alias=AliasChoices("source_type", "sourceType"))
    title: str = ""
    score: float | None = None


class BuyerAgentResponse(BaseModel):
    agent: Literal["buyer"] = "buyer"
    mode: str = "fallback"
    intent: Literal["BUY"] = "BUY"
    parsed_need: ParsedNeed
    recommendations: list[ItemRecommendation] = Field(default_factory=list)
    summary: str = ""
    next_actions: list[str] = Field(default_factory=list)
    run_id: str = Field(default="", validation_alias=AliasChoices("run_id", "runId"))
    trace_id: str = Field(default="", validation_alias=AliasChoices("trace_id", "traceId"))
    model: str = ""
    steps: list[AgentStep] = Field(default_factory=list)
    citations: list[KnowledgeCitation] = Field(default_factory=list)


class HealthResponse(BaseModel):
    status: str = "UP"
    service: str = "campus-trade-ai"
    model: str
    llm_configured: bool
    llm_circuit_state: str = "CLOSED"
    llm_consecutive_failures: int = 0
    llm_max_concurrent_requests: int = 0
