package com.example.Second_hand.trading.platform.service;

import java.math.BigDecimal;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminService {
	private static final DateTimeFormatter DAY_KEY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd");

	private final JdbcTemplate jdbcTemplate;
	private final MessageService messageService;

	public AdminService(JdbcTemplate jdbcTemplate, MessageService messageService) {
		this.jdbcTemplate = jdbcTemplate;
		this.messageService = messageService;
	}

	public Map<String, Object> dashboard() {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("totalUsers", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE deleted = 0", Long.class));
		data.put("todayNewUsers",
				jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE DATE(created_at) = CURRENT_DATE", Long.class));
		data.put("totalItems",
				jdbcTemplate.queryForObject("SELECT COUNT(*) FROM items WHERE deleted = 0", Long.class));
		data.put("onSaleItems",
				jdbcTemplate.queryForObject("SELECT COUNT(*) FROM items WHERE status = 'ON_SALE' AND deleted = 0", Long.class));
		data.put("todayAmount", jdbcTemplate.queryForObject(
				"SELECT COALESCE(SUM(amount), 0) FROM payments WHERE status = 'PAID' AND DATE(paid_at) = CURRENT_DATE",
				BigDecimal.class));
		data.put("totalAmount", jdbcTemplate.queryForObject(
				"SELECT COALESCE(SUM(amount), 0) FROM payments WHERE status = 'PAID'",
				BigDecimal.class));
		data.put("activeUsers", jdbcTemplate.queryForObject("""
				SELECT COUNT(DISTINCT user_id)
				FROM (
				  SELECT id AS user_id FROM users WHERE deleted = 0 AND DATE(last_login_at) = CURRENT_DATE
				  UNION
				  SELECT buyer_id AS user_id FROM orders WHERE DATE(created_at) = CURRENT_DATE
				  UNION
				  SELECT seller_id AS user_id FROM orders WHERE DATE(created_at) = CURRENT_DATE
				  UNION
				  SELECT sender_id AS user_id FROM chat_messages WHERE DATE(created_at) = CURRENT_DATE
				) active_users
				""", Long.class));
		data.put("pendingVerifiedUsers", jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM users
				WHERE deleted = 0 AND status = 'NORMAL' AND verified_status <> 'VERIFIED'
				""", Long.class));
		data.put("pendingReports", jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM reports WHERE status = 'PENDING'", Long.class));
		data.put("pendingDisputes", jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM disputes WHERE status = 'PENDING'", Long.class));
		data.put("pendingOrders", jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orders WHERE status IN ('PENDING', 'ACCEPTED', 'PAYING')", Long.class));
		data.put("amountTrend", amountTrend());
		data.put("categoryDistribution", categoryDistribution());
		data.put("campusDistribution", campusDistribution());
		return data;
	}

	private List<Map<String, Object>> amountTrend() {
		Map<String, BigDecimal> amountByDate = new HashMap<>();
		jdbcTemplate.queryForList("""
				SELECT DATE_FORMAT(paid_at, '%Y-%m-%d') AS dayKey, COALESCE(SUM(amount), 0) AS amount
				FROM payments
				WHERE status = 'PAID'
				  AND paid_at >= DATE_SUB(CURRENT_DATE, INTERVAL 6 DAY)
				GROUP BY DATE_FORMAT(paid_at, '%Y-%m-%d')
				ORDER BY dayKey
				""").forEach(row -> amountByDate.put(
						String.valueOf(row.get("dayKey")),
						(BigDecimal) row.get("amount")));

		return java.util.stream.IntStream.rangeClosed(0, 6)
				.mapToObj(index -> LocalDate.now().minusDays(6L - index))
				.map(day -> {
					Map<String, Object> row = new LinkedHashMap<>();
					row.put("date", day.format(DAY_LABEL));
					row.put("dayKey", day.format(DAY_KEY));
					row.put("amount", amountByDate.getOrDefault(day.format(DAY_KEY), BigDecimal.ZERO));
					return row;
				})
				.toList();
	}

	private List<Map<String, Object>> categoryDistribution() {
		return jdbcTemplate.queryForList("""
				SELECT c.id AS categoryId, c.name AS category, COUNT(i.id) AS count
				FROM categories c
				LEFT JOIN items i ON i.category_id = c.id AND i.deleted = 0
				WHERE c.enabled = 1
				GROUP BY c.id, c.name, c.sort_order
				ORDER BY c.sort_order, c.id
				""");
	}

	private List<Map<String, Object>> campusDistribution() {
		return jdbcTemplate.queryForList("""
				SELECT campus, COUNT(*) AS count
				FROM items
				WHERE deleted = 0 AND campus IS NOT NULL AND campus <> ''
				GROUP BY campus
				ORDER BY count DESC, campus
				""");
	}

	public List<Map<String, Object>> disputes() {
		return jdbcTemplate.queryForList("""
				SELECT id AS disputeId, dispute_no AS disputeNo, order_id AS orderId, applicant_id AS applicantId,
				  reason, evidence_urls AS evidenceUrls, status, handled_by AS handledBy, handled_at AS handledAt,
				  result_remark AS resultRemark, created_at AS createdAt, updated_at AS updatedAt
				FROM disputes
				ORDER BY created_at DESC
				LIMIT 100
				""");
	}

	public List<Map<String, Object>> reports() {
		return jdbcTemplate.queryForList("""
				SELECT id AS reportId, reporter_id AS reporterId, target_type AS targetType, target_id AS targetId,
				  report_type AS reportType, content, status, handled_by AS handledBy, handled_at AS handledAt,
				  result_remark AS resultRemark, created_at AS createdAt
				FROM reports
				ORDER BY created_at DESC
				LIMIT 100
				""");
	}

	public Map<String, Object> settings() {
		Map<String, Object> data = new LinkedHashMap<>();
		jdbcTemplate.queryForList("""
				SELECT setting_key AS settingKey, setting_value AS settingValue
				FROM system_settings
				ORDER BY id
				""").forEach(row -> data.put(String.valueOf(row.get("settingKey")), row.get("settingValue")));
		data.put("sensitiveWords", jdbcTemplate.queryForList(
				"SELECT word FROM sensitive_words WHERE enabled = 1 ORDER BY id", String.class));
		return data;
	}

	public List<Map<String, Object>> notices() {
		return jdbcTemplate.queryForList("""
				SELECT id AS noticeId, title, content, scope_type AS scopeType, campus, popup_enabled AS popupEnabled,
				  status, published_at AS publishedAt, created_by AS createdBy, created_at AS createdAt, updated_at AS updatedAt
				FROM announcements
				ORDER BY created_at DESC
				LIMIT 100
				""");
	}

	@Transactional
	public Map<String, Object> createNotice(Long adminId, Map<String, Object> body) {
		if (adminId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录管理员账号");
		}
		NoticePayload payload = noticePayload(body);
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement("""
					INSERT INTO announcements (
					  title, content, scope_type, campus, popup_enabled, status, published_at, created_by
					) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
					""", Statement.RETURN_GENERATED_KEYS);
			statement.setString(1, payload.title());
			statement.setString(2, payload.content());
			statement.setString(3, payload.scopeType());
			statement.setString(4, payload.campus());
			statement.setInt(5, payload.popupEnabled() ? 1 : 0);
			statement.setString(6, payload.status());
			statement.setObject(7, payload.publishedAt());
			statement.setLong(8, adminId);
			return statement;
		}, keyHolder);
		Number key = keyHolder.getKey();
		if (key == null) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "公告创建失败");
		}
		if ("PUBLISHED".equals(payload.status())) {
			notifyAnnouncementUsers(key.longValue(), payload);
		}
		return notice(key.longValue());
	}

	@Transactional
	public boolean updateNotice(Integer noticeId, Map<String, Object> body) {
		String oldStatus = noticeStatusById(noticeId);
		NoticePayload payload = noticePayload(body);
		int updated = jdbcTemplate.update("""
				UPDATE announcements
				SET title = ?, content = ?, scope_type = ?, campus = ?, popup_enabled = ?, status = ?, published_at = ?
				WHERE id = ?
				""",
				payload.title(), payload.content(), payload.scopeType(), payload.campus(),
				payload.popupEnabled() ? 1 : 0, payload.status(), payload.publishedAt(), noticeId);
		if (updated == 0) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "公告不存在");
		}
		if (!"PUBLISHED".equals(oldStatus) && "PUBLISHED".equals(payload.status())) {
			notifyAnnouncementUsers(noticeId.longValue(), payload);
		}
		return true;
	}

	@Transactional
	public boolean deleteNotice(Integer noticeId) {
		int deleted = jdbcTemplate.update("DELETE FROM announcements WHERE id = ?", noticeId);
		if (deleted == 0) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "公告不存在");
		}
		return true;
	}

	private Map<String, Object> notice(Long noticeId) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
				SELECT id AS noticeId, title, content, scope_type AS scopeType, campus, popup_enabled AS popupEnabled,
				  status, published_at AS publishedAt, created_by AS createdBy, created_at AS createdAt, updated_at AS updatedAt
				FROM announcements
				WHERE id = ?
				LIMIT 1
				""", noticeId);
		if (rows.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "公告不存在");
		}
		return rows.get(0);
	}

	private String noticeStatusById(Integer noticeId) {
		List<String> statuses = jdbcTemplate.queryForList(
				"SELECT status FROM announcements WHERE id = ? LIMIT 1", String.class, noticeId);
		if (statuses.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "公告不存在");
		}
		return statuses.get(0);
	}

	private void notifyAnnouncementUsers(Long noticeId, NoticePayload payload) {
		String targetSql = "ALL".equals(payload.scopeType())
				? "SELECT id FROM users WHERE deleted = 0 AND status = 'NORMAL'"
				: "SELECT id FROM users WHERE deleted = 0 AND status = 'NORMAL' AND campus = ?";
		List<Long> userIds = "ALL".equals(payload.scopeType())
				? jdbcTemplate.queryForList(targetSql, Long.class)
				: jdbcTemplate.queryForList(targetSql, Long.class, payload.campus());
		for (Long userId : userIds) {
			messageService.createNotification(userId, "SYSTEM", truncate("平台公告：" + payload.title(), 150),
					truncate(payload.content(), 1000));
		}
		messageService.broadcast(payload.title(), payload.content());
	}

	private String truncate(String value, int maxLength) {
		if (value == null || value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength);
	}

	private NoticePayload noticePayload(Map<String, Object> body) {
		String title = requiredText(body, "title", "公告标题");
		String content = requiredText(body, "content", "公告内容");
		String status = noticeStatus(optionalText(body, "status"));
		String scope = optionalText(body, "scope");
		String scopeType = optionalText(body, "scopeType");
		String campus = optionalText(body, "campus");

		if (!StringUtils.hasText(scopeType)) {
			scopeType = StringUtils.hasText(scope) && !"全平台".equals(scope) ? "CAMPUS" : "ALL";
		}
		scopeType = scopeType.toUpperCase();
		if ("ALL".equals(scopeType)) {
			campus = null;
		} else if (!StringUtils.hasText(campus)) {
			campus = scope;
		}
		if (!"ALL".equals(scopeType) && !StringUtils.hasText(campus)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择公告推送校区");
		}

		boolean popupEnabled = boolValue(body.get("popupEnabled"));
		LocalDateTime publishedAt = "PUBLISHED".equals(status) ? LocalDateTime.now() : null;
		return new NoticePayload(title, content, scopeType, campus, popupEnabled, status, publishedAt);
	}

	private String noticeStatus(String value) {
		if (!StringUtils.hasText(value)) {
			return "DRAFT";
		}
		String text = value.trim().toUpperCase();
		return text.contains("PUBLISH") || value.contains("发布") || value.contains("已发布") ? "PUBLISHED" : "DRAFT";
	}

	private String requiredText(Map<String, Object> body, String key, String label) {
		String value = optionalText(body, key);
		if (!StringUtils.hasText(value)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写" + label);
		}
		return value.trim();
	}

	private String optionalText(Map<String, Object> body, String key) {
		Object value = body.get(key);
		return value == null || !StringUtils.hasText(String.valueOf(value)) ? "" : String.valueOf(value).trim();
	}

	private boolean boolValue(Object value) {
		return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value))
				|| "1".equals(String.valueOf(value));
	}

	private record NoticePayload(String title, String content, String scopeType, String campus, boolean popupEnabled,
			String status, LocalDateTime publishedAt) {
	}
}
