package com.example.Second_hand.trading.platform.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Read-only product analytics derived from business and Agent audit facts.
 * It intentionally does not expose chat content or provide any write action.
 */
@Service
public class AgentInsightsService {
	private final JdbcTemplate jdbcTemplate;

	public AgentInsightsService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Map<String, Object> buyerInsights(Long userId) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("recentAgentRuns", count("SELECT COUNT(*) FROM agent_runs WHERE user_id = ? AND created_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 DAY)", userId));
		result.put("favoriteCount", count("SELECT COUNT(*) FROM favorites WHERE user_id = ?", userId));
		result.put("completedOrders", count("SELECT COUNT(*) FROM orders WHERE buyer_id = ? AND status = 'COMPLETED'", userId));
		result.put("favoriteCategories", jdbcTemplate.queryForList("""
				SELECT c.name AS category, COUNT(*) AS count
				FROM favorites f
				JOIN items i ON i.id = f.item_id
				JOIN categories c ON c.id = i.category_id
				WHERE f.user_id = ? AND i.deleted = 0
				GROUP BY c.id, c.name
				ORDER BY count DESC, c.name
				LIMIT 3
				""", userId));
		result.put("favoriteCampuses", jdbcTemplate.queryForList("""
				SELECT i.campus, COUNT(*) AS count
				FROM favorites f JOIN items i ON i.id = f.item_id
				WHERE f.user_id = ? AND i.deleted = 0 AND i.campus IS NOT NULL AND i.campus <> ''
				GROUP BY i.campus
				ORDER BY count DESC, i.campus
				LIMIT 3
				""", userId));
		result.put("summary", "偏好根据你的收藏、已完成订单和 Agent 查询生成，仅用于本人的只读推荐排序。");
		return result;
	}

	public Map<String, Object> sellerInsights(Long userId) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("onSaleItems", count("SELECT COUNT(*) FROM items WHERE seller_id = ? AND deleted = 0 AND status = 'ON_SALE'", userId));
		result.put("soldItems", count("SELECT COUNT(*) FROM items WHERE seller_id = ? AND deleted = 0 AND status = 'SOLD'", userId));
		result.put("totalViews", scalar("SELECT COALESCE(SUM(view_count), 0) FROM items WHERE seller_id = ? AND deleted = 0", userId));
		result.put("totalFavorites", scalar("SELECT COALESCE(SUM(favorite_count), 0) FROM items WHERE seller_id = ? AND deleted = 0", userId));
		result.put("completedOrders", count("SELECT COUNT(*) FROM orders WHERE seller_id = ? AND status = 'COMPLETED'", userId));
		result.put("averageRating", scalar("SELECT COALESCE(ROUND(AVG(rating), 1), 0) FROM reviews WHERE target_user_id = ?", userId));
		result.put("topItems", jdbcTemplate.queryForList("""
				SELECT id AS itemId, title, status, price, view_count AS viewCount, favorite_count AS favoriteCount
				FROM items
				WHERE seller_id = ? AND deleted = 0
				ORDER BY favorite_count DESC, view_count DESC, created_at DESC
				LIMIT 5
				""", userId));
		result.put("summary", "数据来自你自己的商品、订单和评价汇总；它不代表平台对成交原因的推断。");
		return result;
	}

	public Map<String, Object> operationsInsights() {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("runsLast30Days", scalar("SELECT COUNT(*) FROM agent_runs WHERE created_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 DAY)"));
		result.put("successfulRunsLast30Days", scalar("SELECT COUNT(*) FROM agent_runs WHERE status = 'SUCCEEDED' AND created_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 DAY)"));
		result.put("failedRunsLast30Days", scalar("SELECT COUNT(*) FROM agent_runs WHERE status = 'FAILED' AND created_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 DAY)"));
		result.put("averageToolDurationMs", scalar("SELECT COALESCE(ROUND(AVG(duration_ms)), 0) FROM agent_steps WHERE created_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 DAY)"));
		result.put("failedToolStepsLast30Days", scalar("SELECT COUNT(*) FROM agent_steps WHERE status = 'FAILED' AND created_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 DAY)"));
		result.put("recommendationsLast30Days", scalar("SELECT COUNT(*) FROM agent_recommendations WHERE created_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 DAY)"));
		result.put("recommendedItemOrdersLast30Days", scalar("""
				SELECT COUNT(DISTINCT o.id)
				FROM agent_recommendations r
				JOIN agent_runs ar ON ar.run_id = r.run_id
				JOIN orders o ON o.item_id = r.item_id AND o.buyer_id = ar.user_id AND o.created_at >= r.created_at
				WHERE r.created_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 DAY)
				"""));
		result.put("failureBreakdown", jdbcTemplate.queryForList("""
				SELECT COALESCE(NULLIF(error_code, ''), 'unknown') AS errorCode, COUNT(*) AS count
				FROM agent_steps
				WHERE status = 'FAILED' AND created_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 DAY)
				GROUP BY COALESCE(NULLIF(error_code, ''), 'unknown')
				ORDER BY count DESC, errorCode
				LIMIT 5
				"""));
		result.put("note", "“推荐后下单”仅表示同一用户在推荐记录之后购买了该商品，不能作为 Agent 直接促成交易的因果结论。");
		return result;
	}

	private long count(String sql, Object... args) {
		Object value = jdbcTemplate.queryForObject(sql, Object.class, args);
		return number(value).longValue();
	}

	private Number scalar(String sql, Object... args) {
		Object value = jdbcTemplate.queryForObject(sql, Object.class, args);
		return number(value);
	}

	private Number number(Object value) {
		return value instanceof Number number ? number : BigDecimal.ZERO;
	}
}
