from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Any

from app.config import Settings


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


def search_items(settings: Settings, keyword: str, campus: str = "", budget: int | None = None, limit: int = 8) -> list[ItemRow]:
    try:
        import pymysql
    except Exception:
        return []

    words = [word for word in re.split(r"\s+", keyword) if word]
    where = ["i.deleted = 0", "i.status = 'ON_SALE'"]
    params: list[Any] = []

    if words:
        like_parts = []
        for word in words[:4]:
            like_parts.append("(i.title LIKE %s OR i.description LIKE %s OR c.name LIKE %s)")
            like = f"%{word}%"
            params.extend([like, like, like])
        where.append("(" + " OR ".join(like_parts) + ")")
    if campus:
        where.append("i.campus = %s")
        params.append(campus)
    if budget:
        where.append("i.price <= %s")
        params.append(budget * 1.15)

    sql = f"""
        SELECT i.id AS item_id, i.title, COALESCE(i.description, '') AS description,
               i.price, i.original_price, i.condition_level AS condition_level,
               i.campus, COALESCE(i.trade_place, '') AS trade_place,
               c.name AS category, i.view_count, i.favorite_count,
               COALESCE(img.image_url, '') AS image_url
        FROM items i
        JOIN categories c ON c.id = i.category_id
        LEFT JOIN item_images img ON img.item_id = i.id AND img.sort_order = 0
        WHERE {' AND '.join(where)}
        ORDER BY i.favorite_count DESC, i.view_count DESC, i.created_at DESC
        LIMIT %s
    """
    params.append(limit)

    try:
        connection = pymysql.connect(
            host=settings.db_host,
            port=settings.db_port,
            user=settings.db_user,
            password=settings.db_password,
            database=settings.db_name,
            charset="utf8mb4",
            cursorclass=pymysql.cursors.DictCursor,
            connect_timeout=3,
            read_timeout=4,
        )
        with connection:
            with connection.cursor() as cursor:
                cursor.execute(sql, params)
                rows = cursor.fetchall()
        return [
            ItemRow(
                item_id=int(row["item_id"]),
                title=row["title"],
                description=row["description"],
                price=float(row["price"]),
                original_price=float(row["original_price"]) if row["original_price"] is not None else None,
                condition=row["condition_level"],
                campus=row["campus"],
                trade_place=row["trade_place"],
                category=row["category"],
                view_count=int(row["view_count"] or 0),
                favorite_count=int(row["favorite_count"] or 0),
                image_url=row["image_url"],
            )
            for row in rows
        ]
    except Exception:
        return []


def rank_items(items: list[ItemRow], budget: int | None, campus: str) -> list[ItemRow]:
    def score(item: ItemRow) -> float:
        value = item.favorite_count * 3 + item.view_count * 0.08
        if campus and item.campus == campus:
            value += 25
        if budget:
            diff = abs(item.price - min(item.price, budget))
            value += max(0, 30 - diff / max(budget, 1) * 30)
        if item.condition in {"NEW", "LIKE_NEW"}:
            value += 8
        return value

    return sorted(items, key=score, reverse=True)
