from __future__ import annotations

from app.config import Settings
from app.llm import QwenLLM
from app.schemas import (
    AgentRequest,
    BuyerAgentResponse,
    ItemRecommendation,
    ParsedNeed,
    SwapDraft,
    WantedDraft,
)
from app.pydantic_compat import dump_json
from app.tools.item_search import ItemRow, extract_budget, extract_campus, infer_keyword, rank_items, search_items
from app.tools.pricing import bargain_range
from app.tools.risk import buyer_risk


class BuyerAgent:
    def __init__(self, settings: Settings, llm: QwenLLM | None = None):
        self.settings = settings
        self.llm = llm or QwenLLM(settings)

    def run(self, request: AgentRequest) -> BuyerAgentResponse:
        fallback = self._fallback(request)
        enhanced = self._enhance_with_llm(request, fallback)
        return enhanced or fallback

    def _fallback(self, request: AgentRequest) -> BuyerAgentResponse:
        budget = extract_budget(request.message)
        campus = extract_campus(request.message, request.campus)
        keyword = infer_keyword(request.message)
        usage = self._infer_usage(request.message)
        swap_intent = self._has_swap_intent(request.message)
        items = rank_items(search_items(self.settings, keyword, campus, budget), budget, campus)[:3]
        recommendations = [self._to_recommendation(item, request.message) for item in items]
        should_create_wanted = len(recommendations) == 0

        wanted_draft = None
        if should_create_wanted:
            wanted_draft = WantedDraft(
                title=f"求购 {keyword}",
                description=self._wanted_description(request.message, keyword, campus, budget),
                budget_min=round(budget * 0.85, 2) if budget else None,
                budget_max=float(budget) if budget else None,
                campus=campus,
            )

        swap_draft = None
        if swap_intent:
            swap_keyword = self._swap_target_keyword(request.message) or keyword
            swap_draft = SwapDraft(
                title=f"想换 {swap_keyword}",
                expected_title=swap_keyword,
                description=self._swap_description(request.message, swap_keyword, campus, budget),
                category=self._swap_category(swap_keyword, request.message),
                target_category=self._swap_category(swap_keyword, request.message),
                campus=campus,
            )

        summary = (
            f"我按“{keyword}”、预算{budget}元左右、{campus or '不限校区'}帮你筛了校园闲置。"
            if recommendations
            else "暂时没有特别合适的在售商品，我先帮你整理一份求购草稿。"
        )
        return BuyerAgentResponse(
            mode="fallback",
            parsed_need=ParsedNeed(keyword=keyword, budget=budget, campus=campus, usage=usage),
            recommendations=recommendations,
            should_create_wanted=should_create_wanted,
            wanted_draft=wanted_draft,
            swap_draft=swap_draft,
            summary=summary,
            next_actions=[
                "先查看推荐商品详情和卖家信用",
                "面交前确认配件、瑕疵和验货时间",
                "价格合适后再复制私聊草稿发起沟通",
            ],
        )

    def _enhance_with_llm(self, request: AgentRequest, fallback: BuyerAgentResponse) -> BuyerAgentResponse | None:
        if not self.llm.configured:
            return None
        system_prompt = (
            "你是校园二手平台的淘货 Agent，负责生成建议、草稿和可确认执行的动作素材；真正的下单、私聊或求购发布由前端在用户确认后调用平台接口完成。"
            "你必须保留输入 JSON 中的商品 id、标题和价格，不得编造不存在的商品。"
            "用中文输出，语气像可靠的同校区学长学姐，结果必须符合给定 JSON schema。"
        )
        user_prompt = (
            f"用户需求：{request.message}\n"
            f"规则兜底结果 JSON：{dump_json(fallback)}\n"
            "请优化推荐理由、风险点、砍价区间和私聊草稿。"
        )
        enhanced = self.llm.complete_json(system_prompt, user_prompt, BuyerAgentResponse)
        if enhanced:
            enhanced.mode = "llm"
        return enhanced

    def _to_recommendation(self, item: ItemRow, user_message: str) -> ItemRecommendation:
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
            chat_draft=self._chat_draft(item, user_message),
        )

    def _chat_draft(self, item: ItemRow, user_message: str) -> str:
        return (
            f"同学你好，我在平台看到你发布的《{item.title}》，我主要想确认一下成色、配件和是否方便面交验货。"
            f"如果没问题的话，价格能不能按 {bargain_range(item.price, item.condition)} 聊聊？我的需求是：{user_message}"
        )

    def _wanted_description(self, message: str, keyword: str, campus: str, budget: int | None) -> str:
        parts = [f"想求购一件和“{keyword}”相关的校园闲置。"]
        if budget:
            parts.append(f"预算大约 {budget} 元，可根据成色和配件小幅调整。")
        if campus:
            parts.append(f"优先 {campus} 面交，可现场验货。")
        parts.append(f"补充需求：{message}")
        return "".join(parts)

    def _swap_description(self, message: str, keyword: str, campus: str, budget: int | None) -> str:
        parts = [f"想用自己的闲置换一件和“{keyword}”相关的物品。"]
        if budget:
            parts.append(f"可按约 {budget} 元的价值区间沟通补差价。")
        if campus:
            parts.append(f"优先 {campus} 面交互验。")
        parts.append(f"补充需求：{message}")
        return "".join(parts)

    def _infer_usage(self, text: str) -> str:
        if "考研" in text:
            return "考研备考"
        if any(word in text for word in ["上课", "记笔记", "学习"]):
            return "学习上课"
        if any(word in text for word in ["宿舍", "搬宿舍", "毕业"]):
            return "宿舍生活"
        return "校园日常"

    def _has_swap_intent(self, text: str) -> bool:
        return any(word in text for word in ["换", "置换", "交换", "以物换物", "补差价"])

    def _swap_target_keyword(self, text: str) -> str:
        for marker in ["换成", "换一", "换个", "换", "置换", "交换"]:
            if marker in text:
                target = text.split(marker, 1)[1]
                target = target.split("，", 1)[0].split("。", 1)[0].split(",", 1)[0].strip()
                return target[:24]
        return ""

    def _swap_category(self, keyword: str, text: str) -> str:
        source = f"{keyword} {text}"
        if any(word in source for word in ["iPad", "ipad", "平板", "手机", "电脑", "键盘", "耳机"]):
            return "数码3C"
        if any(word in source for word in ["教材", "考研", "四六级", "真题", "书"]):
            return "教材教辅"
        if any(word in source for word in ["冰箱", "台灯", "收纳", "宿舍", "桌", "椅"]):
            return "生活日用"
        if any(word in source for word in ["包", "衣服", "鞋", "箱"]):
            return "服饰鞋包"
        if any(word in source for word in ["球", "自行车", "健身", "拍", "滑板"]):
            return "运动户外"
        return ""

    def _condition_label(self, condition: str) -> str:
        return {
            "NEW": "全新",
            "LIKE_NEW": "几乎全新",
            "GOOD": "轻微使用",
            "FAIR": "明显使用",
        }.get(condition, condition or "未标注")
