from __future__ import annotations


def buyer_risk(title: str, description: str, price: float | None) -> str:
    text = f"{title} {description}"
    risks = []
    if any(word in text for word in ["定金", "先付", "加微信", "不走平台", "私下"]):
        risks.append("描述中可能存在脱离平台或提前付款风险")
    if price is not None and price <= 20:
        risks.append("价格较低，建议确认是否为单件或配件")
    if not risks:
        risks.append("暂无明显高风险，建议面交前确认配件、瑕疵和可验货时间")
    return "；".join(risks)
