package com.example.Second_hand.trading.platform.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Owns the durable boundary around Agent execution. The AI service can suggest
 * recommendations, but this service is the only place where business facts are
 * read, verified and persisted.
 */
@Service
public class AgentRunService {
	private static final int MAX_HISTORY = 20;
	private static final int MAX_TOOL_ROWS = 10;

	private final JdbcTemplate jdbcTemplate;
	private final AgentService agentService;
	private final ObjectMapper objectMapper;
	private final int traceRetentionDays;

	public AgentRunService(JdbcTemplate jdbcTemplate, AgentService agentService, ObjectMapper objectMapper,
			@Value("${app.ai.trace-retention-days:90}") int traceRetentionDays) {
		this.jdbcTemplate = jdbcTemplate;
		this.agentService = agentService;
		this.objectMapper = objectMapper;
		this.traceRetentionDays = Math.max(30, Math.min(traceRetentionDays, 365));
	}

	public Map<String, Object> createBuyerRun(Long userId, Map<String, Object> body) {
		if (userId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
		}
		String message = requiredText(body == null ? null : body.get("message"));
		String runId = UUID.randomUUID().toString();
		String traceId = UUID.randomUUID().toString().replace("-", "");
		jdbcTemplate.update("""
				INSERT INTO agent_runs (run_id, user_id, agent_type, input_text, status, trace_id, started_at)
				VALUES (?, ?, 'BUYER', ?, 'RUNNING', ?, CURRENT_TIMESTAMP)
				""", runId, userId, message, traceId);

		Map<String, Object> request = new LinkedHashMap<>();
		if (body != null) {
			request.putAll(body);
		}
		request.put("message", message);
		request.put("userId", userId); // authenticated value always wins over browser input
		request.put("runId", runId);
		request.put("traceId", traceId);

		try {
			Map<String, Object> result = agentService.buyerRun(request);
			List<Map<String, Object>> verified = persistVerifiedRecommendations(runId, result.get("recommendations"));
			result.put("recommendations", verified);
			result.put("runId", runId);
			result.put("traceId", traceId);
			persistSteps(runId, result.get("steps"));
			List<Map<String, Object>> timeline = timeline(runId);
			result.put("timeline", timeline);
			jdbcTemplate.update("""
				UPDATE agent_runs
				SET status = 'SUCCEEDED', model_name = ?, output_json = ?, finished_at = CURRENT_TIMESTAMP
				WHERE run_id = ?
				""", stringValue(result.get("model")), json(result), runId);
			return result;
		} catch (com.example.Second_hand.trading.platform.exception.AgentServiceException exception) {
			persistSteps(runId, List.of(Map.of(
					"type", "service", "tool", "agent_service", "input", json(request),
					"output", "{}", "status", "FAILED", "durationMs", 0,
					"errorCode", exception.getReason().name())));
			Map<String, Object> fallback = unavailableFallback(runId, traceId, exception.getReason().name());
			fallback.put("timeline", timeline(runId));
			jdbcTemplate.update("""
				UPDATE agent_runs
				SET status = 'FAILED', model_name = 'rule-fallback', output_json = ?, error_code = ?, finished_at = CURRENT_TIMESTAMP
				WHERE run_id = ?
				""", json(fallback), exception.getReason().name(), runId);
			return fallback;
		} catch (RuntimeException exception) {
			persistSteps(runId, List.of(Map.of(
					"type", "service", "tool", "agent_service", "input", json(request),
					"output", "{}", "status", "FAILED", "durationMs", 0,
					"errorCode", exception.getClass().getSimpleName())));
			jdbcTemplate.update("""
				UPDATE agent_runs
				SET status = 'FAILED', error_code = ?, finished_at = CURRENT_TIMESTAMP
				WHERE run_id = ?
				""", exception.getClass().getSimpleName(), runId);
			throw exception;
		}
	}

	private Map<String, Object> unavailableFallback(String runId, String traceId, String reason) {
		Map<String, Object> fallback = new LinkedHashMap<>();
		fallback.put("agent", "buyer");
		fallback.put("mode", "basic-filter");
		fallback.put("model", "rule-fallback");
		fallback.put("runId", runId);
		fallback.put("traceId", traceId);
		fallback.put("recommendations", List.of());
		fallback.put("summary", "Agent 服务暂时不可用，当前未生成推荐。请使用商品列表的关键词、价格和校区筛选。");
		fallback.put("nextActions", List.of("使用商品列表筛选在售商品", "服务恢复后重新发起导购"));
		fallback.put("failureReason", reason);
		return fallback;
	}

	public List<Map<String, Object>> runsForUser(Long userId) {
		if (userId == null) {
			return List.of();
		}
		return jdbcTemplate.queryForList("""
				SELECT run_id AS runId, agent_type AS agentType, input_text AS message, status,
				  started_at AS createdAt, finished_at AS finishedAt, output_json AS outputJson
				FROM agent_runs WHERE user_id = ? ORDER BY created_at DESC LIMIT ?
				""", userId, MAX_HISTORY).stream().map(this::historyRow).toList();
	}

	public Map<String, Object> runForUser(Long userId, String runId) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
				SELECT run_id AS runId, agent_type AS agentType, input_text AS message, status,
				  trace_id AS traceId, started_at AS startedAt, finished_at AS finishedAt, output_json AS outputJson,
				  error_code AS errorCode
				FROM agent_runs WHERE run_id = ? AND user_id = ? LIMIT 1
				""", runId, userId);
		if (rows.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent 运行记录不存在");
		}
		Map<String, Object> row = historyRow(rows.get(0));
		row.put("timeline", timeline(runId));
		return row;
	}

	public void clearRunsForUser(Long userId) {
		if (userId == null) return;
		List<String> runIds = jdbcTemplate.queryForList("SELECT run_id FROM agent_runs WHERE user_id = ?", String.class, userId);
		if (!runIds.isEmpty()) {
			String placeholders = String.join(",", java.util.Collections.nCopies(runIds.size(), "?"));
			jdbcTemplate.update("DELETE FROM agent_recommendations WHERE run_id IN (" + placeholders + ")", runIds.toArray());
			jdbcTemplate.update("DELETE FROM agent_steps WHERE run_id IN (" + placeholders + ")", runIds.toArray());
		}
		jdbcTemplate.update("DELETE FROM agent_runs WHERE user_id = ?", userId);
	}

	public Map<String, Object> searchItems(Map<String, Object> body) {
		String keyword = text(body, "keyword", "query");
		String campus = text(body, "campus");
		BigDecimal maxPrice = money(body == null ? null : body.get("maxPrice"));
		StringBuilder sql = new StringBuilder("""
				SELECT i.id AS itemId, i.title, i.description, i.price, i.condition_level AS conditionLevel,
				  i.campus, COALESCE(i.trade_place, '') AS tradePlace, i.trade_modes AS tradeModes,
				  i.swap_supported AS swapSupported, c.name AS category, i.seller_id AS sellerId,
				  i.view_count AS viewCount, i.favorite_count AS favoriteCount,
				  u.credit_score AS sellerCredit,
				  COALESCE((SELECT AVG(r.rating) FROM reviews r WHERE r.target_user_id = i.seller_id), 0) AS sellerRating,
				  (SELECT image_url FROM item_images img WHERE img.item_id = i.id ORDER BY sort_order, id LIMIT 1) AS imageUrl
				FROM items i JOIN categories c ON c.id = i.category_id
				JOIN users u ON u.id = i.seller_id
				WHERE i.deleted = 0 AND i.status = 'ON_SALE'
				""");
		List<Object> params = new ArrayList<>();
		if (StringUtils.hasText(keyword)) {
			sql.append(" AND (i.title LIKE ? OR i.description LIKE ? OR c.name LIKE ?)");
			String pattern = "%" + keyword.trim().replace("%", "\\%").replace("_", "\\_") + "%";
			params.add(pattern);
			params.add(pattern);
			params.add(pattern);
		}
		if (StringUtils.hasText(campus)) {
			sql.append(" AND i.campus = ?");
			params.add(campus.trim());
		}
		if (maxPrice != null) {
			sql.append(" AND i.price <= ?");
			params.add(maxPrice);
		}
		sql.append(" ORDER BY i.favorite_count DESC, i.view_count DESC, i.created_at DESC LIMIT ?");
		params.add(MAX_TOOL_ROWS);
		return Map.of("items", jdbcTemplate.queryForList(sql.toString(), params.toArray()), "source", "realtime");
	}

	public Map<String, Object> itemRealtime(Map<String, Object> body) {
		Long itemId = longValue(body == null ? null : body.get("itemId"));
		if (itemId == null) {
			itemId = longValue(body == null ? null : body.get("item_id"));
		}
		if (itemId == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少商品 ID");
		}
		List<Map<String, Object>> items = jdbcTemplate.queryForList("""
				SELECT i.id AS itemId, i.title, i.description, i.price, i.condition_level AS conditionLevel,
				  i.campus, COALESCE(i.trade_place, '') AS tradePlace, i.trade_modes AS tradeModes,
				  i.status, i.swap_supported AS swapSupported, c.name AS category, i.seller_id AS sellerId,
				  u.credit_score AS sellerCredit,
				  COALESCE((SELECT AVG(r.rating) FROM reviews r WHERE r.target_user_id = i.seller_id), 0) AS sellerRating,
				  (SELECT image_url FROM item_images img WHERE img.item_id = i.id ORDER BY sort_order, id LIMIT 1) AS imageUrl
				FROM items i JOIN categories c ON c.id = i.category_id JOIN users u ON u.id = i.seller_id
				WHERE i.id = ? AND i.deleted = 0 AND i.status = 'ON_SALE' LIMIT 1
				""", itemId);
		if (items.isEmpty()) {
			return Map.of("itemId", itemId, "available", false, "source", "realtime");
		}
		Map<String, Object> result = new LinkedHashMap<>(items.get(0));
		result.put("available", true);
		result.put("source", "realtime");
		return result;
	}

	public Map<String, Object> sellerSummary(Map<String, Object> body) {
		Long sellerId = longValue(body == null ? null : body.get("sellerId"));
		if (sellerId == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少卖家 ID");
		}
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
				SELECT u.id AS sellerId, u.nickname, u.credit_score AS creditScore, u.verified_status AS verifiedStatus,
				  COUNT(r.id) AS reviewCount, COALESCE(AVG(r.rating), 0) AS averageRating
				FROM users u LEFT JOIN reviews r ON r.target_user_id = u.id
				WHERE u.id = ? AND u.deleted = 0 GROUP BY u.id, u.nickname, u.credit_score, u.verified_status LIMIT 1
				""", sellerId);
		if (rows.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "卖家不存在");
		}
		return Map.of("seller", rows.get(0), "source", "realtime");
	}

	public Map<String, Object> orderStatus(Map<String, Object> body) {
		Long userId = longValue(body == null ? null : body.get("userId"));
		Long orderId = longValue(body == null ? null : body.get("orderId"));
		if (userId == null || orderId == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少用户或订单 ID");
		}
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
				SELECT o.id AS orderId, o.order_no AS orderNo, o.status, o.amount, o.trade_mode AS tradeMode,
				  o.updated_at AS updatedAt, i.title AS itemTitle
				FROM orders o JOIN items i ON i.id = o.item_id
				WHERE o.id = ? AND (o.buyer_id = ? OR o.seller_id = ?) LIMIT 1
				""", orderId, userId, userId);
		if (rows.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在或无权查看");
		}
		return Map.of("order", rows.get(0), "source", "realtime");
	}

	public Map<String, Object> userPreferences(Map<String, Object> body) {
		Long userId = longValue(body == null ? null : body.get("userId"));
		if (userId == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少用户 ID");
		}
		List<Map<String, Object>> favorites = jdbcTemplate.queryForList("""
				SELECT i.id AS itemId, i.title, c.name AS category, i.campus
				FROM favorites f JOIN items i ON i.id = f.item_id JOIN categories c ON c.id = i.category_id
				WHERE f.user_id = ? AND i.deleted = 0 ORDER BY f.created_at DESC LIMIT 5
				""", userId);
		return Map.of("recentFavorites", favorites, "source", "realtime");
	}

	public Map<String, Object> tradeRules() {
		List<String> rules = jdbcTemplate.queryForList("""
				SELECT setting_value FROM system_settings WHERE setting_key = 'trade_rules' LIMIT 1
				""", String.class);
		return Map.of("rules", rules.isEmpty() ? "" : rules.get(0), "source", "rule-store");
	}

	@Scheduled(cron = "${app.ai.trace-cleanup-cron:0 20 3 * * *}")
	public void cleanExpiredTraces() {
		LocalDateTime cutoff = LocalDateTime.now().minusDays(traceRetentionDays);
		jdbcTemplate.update("DELETE FROM agent_recommendations WHERE run_id IN (SELECT run_id FROM agent_runs WHERE created_at < ?)", cutoff);
		jdbcTemplate.update("DELETE FROM agent_steps WHERE run_id IN (SELECT run_id FROM agent_runs WHERE created_at < ?)", cutoff);
		jdbcTemplate.update("DELETE FROM agent_runs WHERE created_at < ?", cutoff);
	}

	private List<Map<String, Object>> persistVerifiedRecommendations(String runId, Object rawRecommendations) {
		if (!(rawRecommendations instanceof List<?> recommendations)) {
			return List.of();
		}
		List<Map<String, Object>> verified = new ArrayList<>();
		for (Object raw : recommendations) {
			if (!(raw instanceof Map<?, ?> rawMap)) {
				continue;
			}
			Long itemId = longValue(rawMap.get("item_id"));
			if (itemId == null) {
				itemId = longValue(rawMap.get("itemId"));
			}
			if (itemId == null) {
				continue;
			}
			Map<String, Object> snapshot = itemRealtime(Map.of("itemId", itemId));
			if (!Boolean.TRUE.equals(snapshot.get("available"))) {
				continue;
			}
			if (!sameMoney(rawMap.get("price"), snapshot.get("price"))) {
				continue;
			}
			Map<String, Object> recommendation = new LinkedHashMap<>();
			for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
				recommendation.put(String.valueOf(entry.getKey()), entry.getValue());
			}
			recommendation.put("item_id", itemId);
			recommendation.put("title", snapshot.get("title"));
			recommendation.put("price", snapshot.get("price"));
			recommendation.put("campus", snapshot.get("campus"));
			recommendation.put("condition", snapshot.get("conditionLevel"));
			String reason = text(recommendation, "reason");
			jdbcTemplate.update("""
					INSERT INTO agent_recommendations (run_id, item_id, reason, snapshot_json)
					VALUES (?, ?, ?, ?)
					""", runId, itemId, reason, json(snapshot));
			verified.add(recommendation);
		}
		return verified;
	}

	private void persistSteps(String runId, Object rawSteps) {
		if (!(rawSteps instanceof List<?> steps)) {
			return;
		}
		int stepNo = 1;
		for (Object raw : steps) {
			if (!(raw instanceof Map<?, ?> map)) {
				continue;
			}
			jdbcTemplate.update("""
					INSERT INTO agent_steps (run_id, step_no, step_type, tool_name, input_summary,
					 output_summary, status, duration_ms, error_code)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", runId, stepNo++, stringValue(map.get("type")), stringValue(map.get("tool")),
					redact(map.get("input")), redact(map.get("output")), valueOrDefault(map.get("status"), "SUCCEEDED"),
					longValue(map.get("durationMs")) == null ? 0 : longValue(map.get("durationMs")),
					stringValue(map.get("errorCode")));
		}
	}

	private List<Map<String, Object>> timeline(String runId) {
		return jdbcTemplate.queryForList("""
				SELECT step_no AS stepNo, step_type AS type, tool_name AS tool, status,
				  duration_ms AS durationMs, output_summary AS summary, error_code AS errorCode, created_at AS createdAt
				FROM agent_steps WHERE run_id = ? ORDER BY step_no
				""", runId);
	}

	private Map<String, Object> historyRow(Map<String, Object> source) {
		Map<String, Object> row = new LinkedHashMap<>(source);
		Object outputJson = row.remove("outputJson");
		if (outputJson != null && StringUtils.hasText(String.valueOf(outputJson))) {
			try {
				row.put("result", objectMapper.readValue(String.valueOf(outputJson), new TypeReference<Map<String, Object>>() {}));
			} catch (Exception ignored) {
				row.put("result", Map.of());
			}
		}
		return row;
	}

	private String requiredText(Object value) {
		String text = stringValue(value).trim();
		if (!StringUtils.hasText(text)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先输入 Agent 需求内容");
		}
		return text.length() > 1000 ? text.substring(0, 1000) : text;
	}

	private String text(Map<String, Object> body, String... keys) {
		if (body == null) return "";
		for (String key : keys) {
			String value = stringValue(body.get(key)).trim();
			if (StringUtils.hasText(value)) return value;
		}
		return "";
	}

	private BigDecimal money(Object value) {
		try {
			return value == null || !StringUtils.hasText(String.valueOf(value)) ? null : new BigDecimal(String.valueOf(value));
		} catch (NumberFormatException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "价格格式错误");
		}
	}

	private boolean sameMoney(Object expected, Object actual) {
		try {
			if (expected == null || actual == null) return false;
			return new BigDecimal(String.valueOf(expected)).compareTo(new BigDecimal(String.valueOf(actual))) == 0;
		} catch (NumberFormatException exception) {
			return false;
		}
	}

	private Long longValue(Object value) {
		if (value == null || !StringUtils.hasText(String.valueOf(value))) return null;
		try {
			return value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private String valueOrDefault(Object value, String fallback) {
		String result = stringValue(value);
		return StringUtils.hasText(result) ? result : fallback;
	}

	private String stringValue(Object value) {
		return value == null ? "" : String.valueOf(value);
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception exception) {
			return "{}";
		}
	}

	private String redact(Object value) {
		String text = stringValue(value).replaceAll("(?i)(phone|email|studentNo|token)\\s*[:=]\\s*[^,}\\s]+", "$1=[redacted]");
		return text.length() <= 1200 ? text : text.substring(0, 1200) + "…";
	}
}
