from __future__ import annotations

from app.config import Settings
from app.schemas import (
    AgentRequest,
    BuyerAgentResponse,
    ItemRecommendation,
    ParsedNeed,
)
from app.tools.item_search import ItemRow, extract_budget, extract_campus, infer_keyword, rank_items
from app.tool_gateway import ToolGateway
from app.tools.pricing import bargain_range
from app.tools.risk import buyer_risk


class BuyerAgent:
    def __init__(self, settings: Settings, gateway: ToolGateway | None = None):
        self.settings = settings
        self.gateway = gateway or ToolGateway(settings, None)

    def _fallback(self, request: AgentRequest) -> BuyerAgentResponse:
        budget = extract_budget(request.message)
        campus = extract_campus(request.message, request.campus)
        keyword = infer_keyword(request.message)
        usage = self._infer_usage(request.message)
        preference_payload = self.gateway.call("user-preferences", {})
        preferences = preference_payload.get("recentFavorites") if isinstance(preference_payload, dict) else []
        preferences = preferences if isinstance(preferences, list) else []
        preferred_categories = {str(row.get("category")) for row in preferences if isinstance(row, dict) and row.get("category")}
        preferred_campuses = {str(row.get("campus")) for row in preferences if isinstance(row, dict) and row.get("campus")}
        items = rank_items(
            self._search_items(keyword, campus, budget), budget, campus, preferred_categories, preferred_campuses
        )[:3]
        recommendations = [self._to_recommendation(item) for item in items]

        summary = (
            f"我按“{keyword}”、预算{budget}元左右、{campus or '不限校区'}帮你筛了校园闲置。"
            if recommendations
            else "当前没有匹配的在售商品，请调整关键词、预算或校区后重新筛选。"
        )
        return BuyerAgentResponse(
            mode="fallback",
            parsed_need=ParsedNeed(keyword=keyword, budget=budget, campus=campus, usage=usage),
            recommendations=recommendations,
            summary=summary,
            next_actions=[
                "先查看推荐商品详情和卖家信用",
                "面交前确认配件、瑕疵和验货时间",
                "如需交易，请在商品详情页自行确认后使用既有交易功能",
            ],
        )

    def _search_items(self, keyword: str, campus: str, budget: int | None) -> list[ItemRow]:
        payload = self.gateway.call("search-items", {"keyword": keyword, "campus": campus, "maxPrice": budget})
        rows = payload.get("items") if isinstance(payload, dict) else []
        if not isinstance(rows, list):
            return []
        result = []
        for row in rows:
            if not isinstance(row, dict):
                continue
            try:
                result.append(ItemRow(
                    item_id=int(row.get("itemId")), title=str(row.get("title") or ""),
                    description=str(row.get("description") or ""), price=float(row.get("price") or 0),
                    original_price=None, condition=str(row.get("conditionLevel") or ""),
                    campus=str(row.get("campus") or ""), trade_place=str(row.get("tradePlace") or ""),
                    category=str(row.get("category") or ""), view_count=int(row.get("viewCount") or 0),
                    favorite_count=int(row.get("favoriteCount") or 0),
                    image_url=str(row.get("imageUrl") or ""),
                ))
            except (TypeError, ValueError):
                continue
        return result

    def _to_recommendation(self, item: ItemRow) -> ItemRecommendation:
        condition_label = self._condition_label(item.condition)
        price_text = f"{round(item.price)}元" if item.price is not None else "这个价格"
        reason = f"匹配你提到的需求，价格为{price_text}，位于{item.campus or '校园内'}，适合优先看实物后再决定。"
        if item.trade_place:
            reason += f"卖家建议在{item.trade_place}交易，沟通成本比较低。"
        return ItemRecommendation(
            item_id=item.item_id,
            title=item.title,
            price=item.price,
            campus=item.campus,
            condition=condition_label,
            reason=reason,
            risk=buyer_risk(item.title, item.description, item.price),
            bargain_range=bargain_range(item.price, item.condition),
        )

    def _infer_usage(self, text: str) -> str:
        if "考研" in text:
            return "考研备考"
        if any(word in text for word in ["上课", "记笔记", "学习"]):
            return "学习上课"
        if any(word in text for word in ["宿舍", "搬宿舍", "毕业"]):
            return "宿舍生活"
        return "校园日常"

    def _condition_label(self, condition: str) -> str:
        return {
            "NEW": "全新",
            "LIKE_NEW": "几乎全新",
            "GOOD": "轻微使用",
            "FAIR": "明显使用",
        }.get(condition, condition or "未标注")
