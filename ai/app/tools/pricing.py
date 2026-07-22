from __future__ import annotations


def bargain_range(price: float | None, condition: str = "") -> str:
    if price is None:
        return "先询问最低可接受价格"
    discount = 0.9
    if condition == "NEW":
        discount = 0.95
    elif condition == "LIKE_NEW":
        discount = 0.9
    elif condition == "GOOD":
        discount = 0.85
    else:
        discount = 0.8
    low = max(1, round(price * (discount - 0.07)))
    high = max(low, round(price * discount))
    return f"{low}-{high} 元"


def seller_price_range(text: str, category: str, condition: str) -> str:
    base = 80
    if category == "数码3C":
        base = 600
    elif category == "教材教辅":
        base = 35
    elif category == "生活日用":
        base = 120
    elif category == "服饰鞋包":
        base = 90
    elif category == "运动户外":
        base = 140
    elif category == "其他":
        base = 100

    if any(word in text for word in ["iPad", "ipad", "平板"]):
        base = 1500
    elif "冰箱" in text:
        base = 220
    elif "键盘" in text:
        base = 120
    elif "自行车" in text:
        base = 320

    factor = 1.0
    if "全新" in condition:
        factor = 1.2
    elif "9" in condition:
        factor = 1.05
    elif "8" in condition:
        factor = 0.9
    elif "明显" in condition:
        factor = 0.65

    low = round(base * factor * 0.82)
    high = round(base * factor * 1.18)
    return f"{low}-{high} 元"
