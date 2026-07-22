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
    chat_draft: str


class WantedDraft(BaseModel):
    title: str
    description: str
    budget_min: float | None = None
    budget_max: float | None = None
    campus: str = ""


class SwapDraft(BaseModel):
    title: str
    expected_title: str
    description: str
    category: str = ""
    target_category: str = ""
    campus: str = ""


class BuyerAgentResponse(BaseModel):
    agent: Literal["buyer"] = "buyer"
    mode: str = "fallback"
    intent: Literal["BUY"] = "BUY"
    parsed_need: ParsedNeed
    recommendations: list[ItemRecommendation] = Field(default_factory=list)
    should_create_wanted: bool = False
    wanted_draft: WantedDraft | None = None
    swap_draft: SwapDraft | None = None
    summary: str = ""
    next_actions: list[str] = Field(default_factory=list)


class PublishDraft(BaseModel):
    title: str
    description: str
    category: str
    condition: str
    price_range: str
    campus_suggestion: str
    trade_place_suggestion: str
    swap_supported: bool = False


class SellerAgentResponse(BaseModel):
    agent: Literal["seller"] = "seller"
    mode: str = "fallback"
    intent: Literal["SELL"] = "SELL"
    draft: PublishDraft
    risk_tips: list[str] = Field(default_factory=list)
    next_actions: list[str] = Field(default_factory=list)


class HealthResponse(BaseModel):
    status: str = "UP"
    service: str = "campus-trade-ai"
    model: str
    llm_configured: bool
