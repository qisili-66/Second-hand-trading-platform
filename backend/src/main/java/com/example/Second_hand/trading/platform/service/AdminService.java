package com.example.Second_hand.trading.platform.service;

import java.math.BigDecimal;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

	public List<Map<String, Object>> categories() {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
				SELECT c.id AS categoryId, c.name, c.sort_order AS sortOrder, c.enabled,
				  COUNT(i.id) AS productCount
				FROM categories c
				LEFT JOIN items i ON i.category_id = c.id AND i.deleted = 0
				WHERE c.enabled = 1
				GROUP BY c.id, c.name, c.sort_order, c.enabled
				ORDER BY c.sort_order, c.id
				""");
		return rows.stream().map(row -> {
			Map<String, Object> category = new LinkedHashMap<>(row);
			category.put("tags", categoryTags(toLong(row.get("categoryId"))));
			return category;
		}).toList();
	}

	@Transactional
	public Map<String, Object> createCategory(Map<String, Object> body) {
		String name = requiredText(body, "name", "Category name");
		ensureUniqueCategoryName(name, null);
		Integer sortOrder = optionalInteger(body == null ? null : body.get("sortOrder"));
		if (sortOrder == null) {
			sortOrder = jdbcTemplate.queryForObject(
					"SELECT COALESCE(MAX(sort_order), 0) + 1 FROM categories",
					Integer.class);
		}

		KeyHolder keyHolder = new GeneratedKeyHolder();
		Integer finalSortOrder = sortOrder;
		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement("""
					INSERT INTO categories (name, sort_order, enabled)
					VALUES (?, ?, 1)
					""", Statement.RETURN_GENERATED_KEYS);
			statement.setString(1, name);
			statement.setInt(2, finalSortOrder == null ? 0 : finalSortOrder);
			return statement;
		}, keyHolder);
		Number key = keyHolder.getKey();
		if (key == null) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Category create failed");
		}
		replaceCategoryTags(key.longValue(), stringList(body == null ? null : body.get("tags")));
		return category(key.longValue());
	}

	@Transactional
	public boolean updateCategory(Integer categoryId, Map<String, Object> body) {
		Long id = requiredId(categoryId, "Category id");
		requireCategory(id);
		String name = requiredText(body, "name", "Category name");
		ensureUniqueCategoryName(name, id);
		Integer sortOrder = optionalInteger(body == null ? null : body.get("sortOrder"));
		int enabled = hasKey(body, "enabled") && !boolValue(body.get("enabled")) ? 0 : 1;
		jdbcTemplate.update("""
				UPDATE categories
				SET name = ?, sort_order = COALESCE(?, sort_order), enabled = ?
				WHERE id = ?
				""", name, sortOrder, enabled, id);
		if (hasKey(body, "tags")) {
			replaceCategoryTags(id, stringList(body.get("tags")));
		}
		return true;
	}

	@Transactional
	public boolean deleteCategory(Integer categoryId) {
		Long id = requiredId(categoryId, "Category id");
		requireCategory(id);
		Long itemCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM items
				WHERE category_id = ? AND deleted = 0
				""", Long.class, id);
		if (itemCount != null && itemCount > 0) {
			jdbcTemplate.update("UPDATE categories SET enabled = 0 WHERE id = ?", id);
			return true;
		}
		jdbcTemplate.update("DELETE FROM category_tags WHERE category_id = ?", id);
		jdbcTemplate.update("DELETE FROM categories WHERE id = ?", id);
		return true;
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

	@Transactional
	public boolean resolveDispute(Long adminId, Integer disputeId, Map<String, Object> body) {
		Long id = requiredId(disputeId, "Dispute id");
		Map<String, Object> dispute = dispute(id);
		String remark = optionalText(body, "result");
		if (!StringUtils.hasText(remark)) {
			remark = optionalText(body, "remark");
		}
		if (!StringUtils.hasText(remark)) {
			remark = "Resolved by admin";
		}
		int updated = jdbcTemplate.update("""
				UPDATE disputes
				SET status = 'RESOLVED', handled_by = ?, handled_at = CURRENT_TIMESTAMP, result_remark = ?
				WHERE id = ?
				""", adminId, remark, id);
		if (updated == 0) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dispute not found");
		}
		Long applicantId = toLong(dispute.get("applicantId"));
		messageService.createNotification(applicantId, "SYSTEM", "Dispute resolved", remark);
		return true;
	}

	public List<Map<String, Object>> reports() {
		return jdbcTemplate.queryForList("""
				SELECT r.id AS reportId, r.reporter_id AS reporterId, reporter.nickname AS reporterName,
				  r.target_type AS targetType, r.target_id AS targetId,
				  CASE
				    WHEN r.target_type = 'ITEM' THEN item.title
				    WHEN r.target_type = 'USER' THEN target_user.nickname
				    ELSE CAST(r.target_id AS CHAR)
				  END AS targetName,
				  r.report_type AS reportType, r.content, r.status,
				  r.handled_by AS handledBy, r.handled_at AS handledAt,
				  r.result_remark AS resultRemark, r.created_at AS createdAt
				FROM reports r
				LEFT JOIN users reporter ON reporter.id = r.reporter_id
				LEFT JOIN items item ON item.id = r.target_id AND r.target_type = 'ITEM'
				LEFT JOIN users target_user ON target_user.id = r.target_id AND r.target_type = 'USER'
				ORDER BY r.created_at DESC
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

	@Transactional
	public boolean updateSettings(Long adminId, Map<String, Object> body) {
		if (adminId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please login admin first");
		}
		if (hasKey(body, "sensitiveWords")) {
			replaceSensitiveWords(adminId, stringList(body.get("sensitiveWords")));
		}
		Map<String, Object> payment = mapValue(body == null ? null : body.get("payment"));
		if (!payment.isEmpty()) {
			upsertSetting("payment_wechat", json(Map.of(
					"appId", optionalText(payment, "wechatAppId", "appId"),
					"enabled", boolValue(payment.get("wechatEnabled")))), "Wechat payment config", adminId);
			upsertSetting("payment_alipay", json(Map.of(
					"appId", optionalText(payment, "alipayAppId", "appId"),
					"enabled", boolValue(payment.get("alipayEnabled")))), "Alipay payment config", adminId);
			upsertSetting("payment_campus_card", json(Map.of(
					"merchant", optionalText(payment, "campusCardMerchant", "merchant"),
					"enabled", boolValue(payment.get("campusCardEnabled")))), "Campus card payment config", adminId);
		}
		Map<String, Object> rules = mapValue(body == null ? null : body.get("rules"));
		if (!rules.isEmpty()) {
			upsertSetting("trade_rules", json(Map.of(
					"maxImages", valueOrDefault(rules.get("maxImages"), 9),
					"disputeDays", valueOrDefault(rules.get("disputeDays"), 3),
					"creditDeduction", valueOrDefault(rules.get("creditDeduction"), 10),
					"tradeTip", optionalText(rules, "tradeTip"))), "Trade rules", adminId);
		}
		return true;
	}

	@Transactional
	public boolean handleReport(Long adminId, Integer reportId, boolean approved, Map<String, Object> body) {
		Long id = requiredId(reportId, "Report id");
		Map<String, Object> report = report(id);
		String remark = optionalText(body, "remark");
		if (!StringUtils.hasText(remark)) {
			remark = approved ? "Report approved" : "Report rejected";
		}
		String status = approved ? "APPROVED" : "REJECTED";
		int updated = jdbcTemplate.update("""
				UPDATE reports
				SET status = ?, handled_by = ?, handled_at = CURRENT_TIMESTAMP, result_remark = ?
				WHERE id = ?
				""", status, adminId, remark, id);
		if (updated == 0) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
		}
		if (approved) {
			applyReportAction(report, body);
		}
		messageService.createNotification(toLong(report.get("reporterId")), "SYSTEM", "Report handled", remark);
		return true;
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

	private Map<String, Object> category(Long categoryId) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
				SELECT c.id AS categoryId, c.name, c.sort_order AS sortOrder, c.enabled,
				  COUNT(i.id) AS productCount
				FROM categories c
				LEFT JOIN items i ON i.category_id = c.id AND i.deleted = 0
				WHERE c.id = ?
				GROUP BY c.id, c.name, c.sort_order, c.enabled
				LIMIT 1
				""", categoryId);
		if (rows.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
		}
		Map<String, Object> row = new LinkedHashMap<>(rows.get(0));
		row.put("tags", categoryTags(toLong(row.get("categoryId"))));
		return row;
	}

	private List<String> categoryTags(Long categoryId) {
		return jdbcTemplate.queryForList("""
				SELECT name
				FROM category_tags
				WHERE category_id = ?
				ORDER BY sort_order, id
				""", String.class, categoryId);
	}

	private void replaceCategoryTags(Long categoryId, List<String> tags) {
		jdbcTemplate.update("DELETE FROM category_tags WHERE category_id = ?", categoryId);
		int sortOrder = 1;
		for (String tag : tags) {
			if (!StringUtils.hasText(tag)) {
				continue;
			}
			jdbcTemplate.update("""
					INSERT INTO category_tags (category_id, name, sort_order)
					VALUES (?, ?, ?)
					ON DUPLICATE KEY UPDATE sort_order = VALUES(sort_order)
					""", categoryId, tag.trim(), sortOrder++);
		}
	}

	private void ensureUniqueCategoryName(String name, Long currentId) {
		Long duplicated = currentId == null
				? jdbcTemplate.queryForObject(
						"SELECT COUNT(*) FROM categories WHERE name = ? AND enabled = 1",
						Long.class, name)
				: jdbcTemplate.queryForObject(
						"SELECT COUNT(*) FROM categories WHERE name = ? AND id <> ? AND enabled = 1",
						Long.class, name, currentId);
		if (duplicated != null && duplicated > 0) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Category already exists");
		}
	}

	private void requireCategory(Long categoryId) {
		Long count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM categories WHERE id = ?",
				Long.class, categoryId);
		if (count == null || count == 0) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
		}
	}

	private Map<String, Object> dispute(Long disputeId) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
				SELECT id AS disputeId, dispute_no AS disputeNo, order_id AS orderId, applicant_id AS applicantId,
				  status, reason
				FROM disputes
				WHERE id = ?
				LIMIT 1
				""", disputeId);
		if (rows.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dispute not found");
		}
		return rows.get(0);
	}

	private Map<String, Object> report(Long reportId) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
				SELECT id AS reportId, reporter_id AS reporterId, target_type AS targetType,
				  target_id AS targetId, report_type AS reportType, status
				FROM reports
				WHERE id = ?
				LIMIT 1
				""", reportId);
		if (rows.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
		}
		return rows.get(0);
	}

	private void applyReportAction(Map<String, Object> report, Map<String, Object> body) {
		String action = optionalText(body, "action");
		String targetType = String.valueOf(report.get("targetType"));
		Long targetId = toLong(report.get("targetId"));
		Integer configuredDeduction = optionalInteger(body == null ? null : body.get("creditDeduction"));
		int deduction = configuredDeduction == null ? 10 : configuredDeduction;

		if ("ITEM".equalsIgnoreCase(targetType) && List.of("OFF_SHELF", "OFF_SHELF_AND_PENALIZE").contains(action)) {
			jdbcTemplate.update("UPDATE items SET status = 'REMOVED' WHERE id = ? AND deleted = 0", targetId);
			List<Long> sellers = jdbcTemplate.queryForList(
					"SELECT seller_id FROM items WHERE id = ? LIMIT 1", Long.class, targetId);
			if (!sellers.isEmpty()) {
				messageService.createNotification(sellers.get(0), "SYSTEM", "Item report approved",
						"Your item was removed after admin review.");
				if ("OFF_SHELF_AND_PENALIZE".equals(action)) {
					jdbcTemplate.update("""
							UPDATE users
							SET credit_score = GREATEST(0, credit_score - ?)
							WHERE id = ? AND deleted = 0
							""", deduction, sellers.get(0));
				}
			}
		}

		if ("USER".equalsIgnoreCase(targetType)) {
			if ("DISABLE_USER".equals(action)) {
				jdbcTemplate.update("UPDATE users SET status = 'DISABLED' WHERE id = ? AND deleted = 0", targetId);
			} else if ("OFF_SHELF_AND_PENALIZE".equals(action) || "PENALIZE".equals(action)) {
				jdbcTemplate.update("""
						UPDATE users
						SET credit_score = GREATEST(0, credit_score - ?)
						WHERE id = ? AND deleted = 0
						""", deduction, targetId);
			}
			messageService.createNotification(targetId, "SYSTEM", "Report approved",
					"An admin handled a report related to your account.");
		}
	}

	private void replaceSensitiveWords(Long adminId, List<String> words) {
		jdbcTemplate.update("UPDATE sensitive_words SET enabled = 0");
		for (String word : words) {
			if (!StringUtils.hasText(word)) {
				continue;
			}
			jdbcTemplate.update("""
					INSERT INTO sensitive_words (word, enabled, created_by)
					VALUES (?, 1, ?)
					ON DUPLICATE KEY UPDATE enabled = 1, created_by = VALUES(created_by)
					""", word.trim(), adminId);
		}
	}

	private void upsertSetting(String key, String value, String description, Long adminId) {
		jdbcTemplate.update("""
				INSERT INTO system_settings (setting_key, setting_value, description, updated_by)
				VALUES (?, ?, ?, ?)
				ON DUPLICATE KEY UPDATE
				  setting_value = VALUES(setting_value),
				  description = VALUES(description),
				  updated_by = VALUES(updated_by)
				""", key, value, description, adminId);
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

	private String optionalText(Map<String, Object> body, String... keys) {
		if (body == null) {
			return "";
		}
		for (String key : keys) {
			Object value = body.get(key);
			if (value != null && StringUtils.hasText(String.valueOf(value))) {
				return String.valueOf(value).trim();
			}
		}
		return "";
	}

	private boolean hasKey(Map<String, Object> body, String key) {
		return body != null && body.containsKey(key);
	}

	private Long requiredId(Integer value, String label) {
		if (value == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " is required");
		}
		return value.longValue();
	}

	private Long toLong(Object value) {
		if (value instanceof Number number) {
			return number.longValue();
		}
		return Long.valueOf(String.valueOf(value));
	}

	private Integer optionalInteger(Object value) {
		if (value == null || !StringUtils.hasText(String.valueOf(value))) {
			return null;
		}
		return value instanceof Number number ? number.intValue() : Integer.valueOf(String.valueOf(value));
	}

	private Object valueOrDefault(Object value, Object fallback) {
		return value == null || !StringUtils.hasText(String.valueOf(value)) ? fallback : value;
	}

	private Map<String, Object> mapValue(Object value) {
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> row = new LinkedHashMap<>();
			map.forEach((key, item) -> row.put(String.valueOf(key), item));
			return row;
		}
		return Map.of();
	}

	private List<String> stringList(Object value) {
		List<String> rows = new ArrayList<>();
		if (value instanceof List<?> list) {
			list.stream()
					.filter(item -> item != null && StringUtils.hasText(String.valueOf(item)))
					.map(item -> String.valueOf(item).trim())
					.forEach(rows::add);
			return rows;
		}
		if (value != null && StringUtils.hasText(String.valueOf(value))) {
			for (String item : String.valueOf(value).split("[,，、;；]")) {
				if (StringUtils.hasText(item)) {
					rows.add(item.trim());
				}
			}
		}
		return rows.stream().distinct().toList();
	}

	private String json(Map<String, Object> values) {
		return values.entrySet().stream()
				.map(entry -> "\"" + escapeJson(entry.getKey()) + "\":" + jsonValue(entry.getValue()))
				.reduce((left, right) -> left + "," + right)
				.map(content -> "{" + content + "}")
				.orElse("{}");
	}

	private String jsonValue(Object value) {
		if (value instanceof Number || value instanceof Boolean) {
			return String.valueOf(value);
		}
		return "\"" + escapeJson(value == null ? "" : String.valueOf(value)) + "\"";
	}

	private String escapeJson(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private boolean boolValue(Object value) {
		return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value))
				|| "1".equals(String.valueOf(value));
	}

	private record NoticePayload(String title, String content, String scopeType, String campus, boolean popupEnabled,
			String status, LocalDateTime publishedAt) {
	}
}
