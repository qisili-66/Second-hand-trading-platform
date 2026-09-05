from __future__ import annotations

import re
from dataclasses import dataclass


@dataclass
class ItemRow:
    item_id: int
    title: str
    description: str
    price: float
    original_price: float | None
    condition: str
    campus: str
    trade_place: str
    category: str
    view_count: int
    favorite_count: int
    image_url: str


def extract_budget(text: str) -> int | None:
    matches = re.findall(r"(\d{2,5})\s*(?:元|块|左右|以内|以下)?", text)
    if not matches:
        return None
    values = [int(value) for value in matches]
    return max(values)


def extract_campus(text: str, fallback: str | None = None) -> str:
    for campus in ["校本部", "东校区", "西校区", "南校区", "大学城校区"]:
        if campus in text:
            return campus
    return fallback or ""


def infer_keyword(text: str) -> str:
    keyword_map = [
        (["ipad", "iPad", "平板", "记笔记"], "iPad 平板"),
        (["电脑", "笔记本", "键盘", "鼠标", "耳机", "数码"], "数码"),
        (["考研", "教材", "四六级", "真题", "书"], "教材 资料"),
        (["冰箱", "台灯", "收纳", "宿舍", "桌"], "宿舍 生活"),
        (["包", "衣服", "鞋", "拉杆箱"], "服饰 箱包"),
        (["球", "自行车", "瑜伽", "健身", "拍"], "运动"),
        (["吉他", "门票", "乐器"], "其他"),
    ]
    for words, keyword in keyword_map:
        if any(word in text for word in words):
            return keyword
    cleaned = re.sub(r"[，。,.！!？?]", " ", text).strip()
    return cleaned[:30] or "校园闲置"


def rank_items(
    items: list[ItemRow], budget: int | None, campus: str,
    preferred_categories: set[str] | None = None, preferred_campuses: set[str] | None = None,
) -> list[ItemRow]:
    preferred_categories = preferred_categories or set()
    preferred_campuses = preferred_campuses or set()

    def score(item: ItemRow) -> float:
        value = item.favorite_count * 3 + item.view_count * 0.08
        if campus and item.campus == campus:
            value += 25
        if budget:
            diff = abs(item.price - min(item.price, budget))
            value += max(0, 30 - diff / max(budget, 1) * 30)
        if item.condition in {"NEW", "LIKE_NEW"}:
            value += 8
        if item.category in preferred_categories:
            value += 14
        if item.campus in preferred_campuses:
            value += 10
        return value

    return sorted(items, key=score, reverse=True)
