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
