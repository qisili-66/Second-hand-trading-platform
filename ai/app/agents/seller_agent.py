from __future__ import annotations

import re

from app.config import Settings
from app.llm import QwenLLM
from app.pydantic_compat import dump_json
from app.schemas import AgentRequest, PublishDraft, SellerAgentResponse
from app.tools.item_search import extract_campus
from app.tools.pricing import seller_price_range
from app.tools.risk import seller_risk_tips


class SellerAgent:
    def __init__(self, settings: Settings, llm: QwenLLM | None = None):
        self.settings = settings
        self.llm = llm or QwenLLM(settings)

    def run(self, request: AgentRequest) -> SellerAgentResponse:
        fallback = self._fallback(request)
        enhanced = self._enhance_with_llm(request, fallback)
        return enhanced or fallback

    def _fallback(self, request: AgentRequest) -> SellerAgentResponse:
        text = request.message.strip()
        category = self._infer_category(text)
        condition = self._infer_condition(text)
        campus = extract_campus(text, request.campus) or "校本部"
        title = self._build_title(text, category, condition)
        price_range = seller_price_range(text, category, condition)
        swap_supported = self._infer_swap_supported(text)
        draft = PublishDraft(
            title=title,
            description=self._build_description(text, category, condition, campus, price_range),
            category=category,
            condition=condition,
            price_range=price_range,
            campus_suggestion=campus,
            trade_place_suggestion=self._trade_place(campus, category),
            swap_supported=swap_supported,
        )
        return SellerAgentResponse(
            mode="fallback",
            draft=draft,
            risk_tips=seller_risk_tips(text),
            next_actions=[
                "补充至少 2 张实拍图后再发布",
                "发布前核对价格、成色和交易地点",
                "买家咨询时优先走平台聊天并保留记录",
                "如果接受置换，发布页会同步打开以物换物开关",
            ],
        )

    def _enhance_with_llm(self, request: AgentRequest, fallback: SellerAgentResponse) -> SellerAgentResponse | None:
        if not self.llm.configured:
            return None
        system_prompt = (
            "你是校园二手平台的发布 Agent，只能生成商品发布草稿和风险提示，不能替用户真正发布商品。"
            "必须用中文，标题自然但不夸张，价格建议要保守可信，结果必须符合给定 JSON schema。"
        )
        user_prompt = (
            f"卖家输入：{request.message}\n"
            f"规则兜底结果 JSON：{dump_json(fallback)}\n"
            "请优化标题、描述、分类、成色、价格区间、交易地点建议和风险提示。"
        )
        enhanced = self.llm.complete_json(system_prompt, user_prompt, SellerAgentResponse)
        if enhanced:
            enhanced.mode = "llm"
        return enhanced

    def _infer_category(self, text: str) -> str:
        mapping = [
            (["iPad", "ipad", "平板", "手机", "电脑", "笔记本", "键盘", "鼠标", "耳机", "相机"], "数码3C"),
            (["教材", "考研", "四六级", "真题", "书", "资料"], "教材教辅"),
            (["冰箱", "台灯", "收纳", "宿舍", "桌", "椅", "锅", "杯"], "生活日用"),
            (["包", "衣服", "鞋", "拉杆箱", "帽"], "服饰鞋包"),
            (["球", "自行车", "瑜伽", "健身", "拍", "滑板"], "运动户外"),
        ]
        for words, category in mapping:
            if any(word in text for word in words):
                return category
        return "其他"

    def _infer_condition(self, text: str) -> str:
        if "全新" in text or "未拆" in text:
            return "全新"
        if any(word in text for word in ["九成", "9成", "95新", "九九"]):
            return "9成新"
        if any(word in text for word in ["八成", "8成", "八五", "85"]):
            return "8成新"
        if any(word in text for word in ["瑕疵", "划痕", "明显", "旧"]):
            return "明显使用"
        return "轻微使用"

    def _infer_swap_supported(self, text: str) -> bool:
        if any(word in text for word in ["不换", "只卖", "不置换", "不接受换"]):
            return False
        return any(word in text for word in ["可换", "能换", "置换", "交换", "以物换物", "补差价"])

    def _build_title(self, text: str, category: str, condition: str) -> str:
        cleaned = re.sub(r"^(出|转|卖|出售|转让)一个?", "", text)
        cleaned = re.split(r"[，。,.！!？?]", cleaned)[0].strip()
        if len(cleaned) < 4:
            cleaned = category
        return f"{condition} {cleaned}"[:32]

    def _build_description(self, text: str, category: str, condition: str, campus: str, price_range: str) -> str:
        return (
            f"商品情况：{text}\n"
            f"推荐分类：{category}，成色建议标为{condition}。\n"
            f"建议价格：{price_range}，可根据实拍图、配件完整度和买家诚意微调。\n"
            f"交易建议：优先在{campus}人流较多的位置面交，支持现场验货后确认。"
        )

    def _trade_place(self, campus: str, category: str) -> str:
        if category in {"数码3C", "教材教辅"}:
            return f"{campus}图书馆或教学楼大厅"
        if category == "生活日用":
            return f"{campus}宿舍楼下或生活区门口"
        return f"{campus}食堂门口或校内快递站"
