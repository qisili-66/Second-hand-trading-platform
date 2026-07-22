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


def seller_risk_tips(text: str) -> list[str]:
    tips = []
    if not any(word in text for word in ["图", "照片", "铭牌", "细节"]):
        tips.append("建议补充 2-3 张实拍图，包含整体、细节和瑕疵位置")
    if any(word in text for word in ["冰箱", "电器", "台灯"]):
        tips.append("建议说明功能是否正常，并补充铭牌或通电照片")
    if any(word in text for word in ["iPad", "ipad", "手机", "电脑", "耳机"]):
        tips.append("建议说明电池、屏幕、配件、维修记录和序列号可核验情况")
    if not any(word in text for word in ["校本部", "东校区", "西校区", "南校区", "大学城校区"]):
        tips.append("建议补充校区和可面交时间，减少来回沟通")
    if not tips:
        tips.append("信息较完整，发布前再确认价格、成色和交易地点即可")
    return tips
