package com.example.Second_hand.trading.platform.service;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {
	private final JdbcTemplate jdbcTemplate;
	private final ItemService itemService;

	public UserService(JdbcTemplate jdbcTemplate, ItemService itemService) {
		this.jdbcTemplate = jdbcTemplate;
		this.itemService = itemService;
	}

	public Map<String, Object> currentUser(Long userId) {
		return userId == null ? Map.of() : userById(userId);
	}

	@Transactional
	public Map<String, Object> updateMe(Long userId, Map<String, Object> body) {
		if (userId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please login first");
		}
		requireUser(userId);

		String email = optionalText(body, "email");
		if (StringUtils.hasText(email)) {
			Long duplicated = jdbcTemplate.queryForObject("""
					SELECT COUNT(*)
					FROM users
					WHERE deleted = 0 AND email = ? AND id <> ?
					""", Long.class, email, userId);
			if (duplicated != null && duplicated > 0) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already used");
			}
		}

		jdbcTemplate.update("""
				UPDATE users
				SET nickname = COALESCE(NULLIF(?, ''), nickname),
				  real_name = COALESCE(NULLIF(?, ''), real_name),
				  department = COALESCE(NULLIF(?, ''), department),
				  enrollment_year = COALESCE(?, enrollment_year),
				  campus = COALESCE(NULLIF(?, ''), campus),
				  email = COALESCE(NULLIF(?, ''), email),
				  phone = COALESCE(NULLIF(?, ''), phone),
				  avatar_url = COALESCE(NULLIF(?, ''), avatar_url)
				WHERE id = ? AND deleted = 0
				""",
				optionalText(body, "nickname"),
				optionalText(body, "realName", "real_name"),
				optionalText(body, "department"),
				optionalInteger(body.get("enrollmentYear")),
				optionalText(body, "campus"),
				email,
				optionalText(body, "phone"),
				optionalText(body, "avatarUrl", "avatar_url"),
				userId);

		if (hasAnyKey(body, "phoneVisible", "wechatVisible", "qq", "wechat")) {
			jdbcTemplate.update("""
					INSERT INTO user_privacy (user_id, phone_visible, wechat_visible, qq, wechat)
					VALUES (?, ?, ?, ?, ?)
					ON DUPLICATE KEY UPDATE
					  phone_visible = VALUES(phone_visible),
					  wechat_visible = VALUES(wechat_visible),
					  qq = COALESCE(NULLIF(VALUES(qq), ''), qq),
					  wechat = COALESCE(NULLIF(VALUES(wechat), ''), wechat)
					""",
					userId,
					boolValue(body.get("phoneVisible")) ? 1 : 0,
					boolValue(body.get("wechatVisible")) ? 1 : 0,
					optionalText(body, "qq"),
					optionalText(body, "wechat"));
		}

		return userById(userId);
	}

	public List<Map<String, Object>> users() {
		return jdbcTemplate.queryForList("""
				SELECT id AS userId, student_no AS studentNo, nickname, phone, status,
				  real_name AS realName, department, campus, verified_status AS verifiedStatus,
				  credit_score AS creditScore, created_at AS createdAt
				FROM users
				WHERE deleted = 0
				ORDER BY created_at DESC
				LIMIT 100
				""");
	}

	@Transactional
	public boolean setUserStatus(Integer userId, String status) {
		if (userId == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User id is required");
		}
		int updated = jdbcTemplate.update("""
				UPDATE users
				SET status = ?
				WHERE id = ? AND deleted = 0
				""", status, userId);
		if (updated == 0) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
		}
		return true;
	}

	@Transactional
	public boolean verifyUser(Integer userId) {
		if (userId == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User id is required");
		}
		int updated = jdbcTemplate.update("""
				UPDATE users
				SET verified_status = 'VERIFIED'
				WHERE id = ? AND deleted = 0
				""", userId);
		if (updated == 0) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
		}
		return true;
	}

	@Transactional
	public int verifyPendingUsers() {
		return jdbcTemplate.update("""
				UPDATE users
				SET verified_status = 'VERIFIED'
				WHERE deleted = 0 AND verified_status <> 'VERIFIED'
				""");
	}

	public List<Map<String, Object>> myItems(Long userId) {
		return itemService.itemsBySeller(userId);
	}

	public List<Map<String, Object>> myFavorites(Long userId) {
		return itemService.favoriteItems(userId);
	}

	public List<Map<String, Object>> notifications(Long userId) {
		if (userId == null) {
			return List.of();
		}
		return jdbcTemplate.queryForList("""
				SELECT id AS notificationId, type, title, content, read_at AS readAt, created_at AS createdAt
				FROM notifications
				WHERE user_id = ?
				ORDER BY created_at DESC
				LIMIT 100
				""", userId);
	}

	public void updateCreditScore(Long userId, int rating) {
		if (userId == null) {
			return;
		}
		int delta = rating >= 4 ? 5 : rating == 3 ? 0 : -10;
		jdbcTemplate.update("""
				UPDATE users
				SET credit_score = GREATEST(0, LEAST(credit_score + ?, 200))
				WHERE id = ? AND deleted = 0
				""", delta, userId);
	}

	public Integer getUserCreditScore(Long userId) {
		if (userId == null) {
			return 100;
		}
		List<Integer> scores = jdbcTemplate.queryForList("""
				SELECT credit_score
				FROM users
				WHERE id = ? AND deleted = 0
				LIMIT 1
				""", Integer.class, userId);
		return scores.isEmpty() ? 100 : scores.get(0);
	}

	public List<Map<String, Object>> reviews(Integer userId) {
		if (userId == null) {
			return List.of();
		}
		return jdbcTemplate.queryForList("""
				SELECT c.id AS reviewId, c.id AS commentId, 'SENT' AS relation,
				  c.item_id AS itemId, i.title AS itemTitle,
				  (SELECT image_url FROM item_images img WHERE img.item_id = c.item_id ORDER BY sort_order, id LIMIT 1) AS coverUrl,
				  c.user_id AS userId, commenter.nickname AS userName,
				  i.seller_id AS sellerId, seller.nickname AS sellerName,
				  c.content, c.created_at AS createdAt
				FROM item_comments c
				LEFT JOIN items i ON i.id = c.item_id
				LEFT JOIN users commenter ON commenter.id = c.user_id
				LEFT JOIN users seller ON seller.id = i.seller_id
				WHERE c.deleted = 0 AND c.user_id = ? AND (i.deleted = 0 OR i.deleted IS NULL)
				UNION ALL
				SELECT c.id AS reviewId, c.id AS commentId, 'RECEIVED' AS relation,
				  c.item_id AS itemId, i.title AS itemTitle,
				  (SELECT image_url FROM item_images img WHERE img.item_id = c.item_id ORDER BY sort_order, id LIMIT 1) AS coverUrl,
				  c.user_id AS userId, commenter.nickname AS userName,
				  i.seller_id AS sellerId, seller.nickname AS sellerName,
				  c.content, c.created_at AS createdAt
				FROM item_comments c
				LEFT JOIN items i ON i.id = c.item_id
				LEFT JOIN users commenter ON commenter.id = c.user_id
				LEFT JOIN users seller ON seller.id = i.seller_id
				WHERE c.deleted = 0 AND i.seller_id = ? AND c.user_id <> ? AND i.deleted = 0
				ORDER BY createdAt DESC
				LIMIT 100
				""", userId, userId, userId);
	}

	private Map<String, Object> userById(Long userId) {
		List<Map<String, Object>> users = jdbcTemplate.queryForList("""
				SELECT users.id AS userId, student_no AS studentNo, nickname, real_name AS realName,
				  phone, email, avatar_url AS avatarUrl, campus, department,
				  enrollment_year AS enrollmentYear, verified_status AS verifiedStatus,
				  credit_score AS creditScore, status,
				  COALESCE(p.phone_visible, 0) AS phoneVisible,
				  COALESCE(p.wechat_visible, 0) AS wechatVisible,
				  p.qq, p.wechat,
				  users.created_at AS createdAt
				FROM users
				LEFT JOIN user_privacy p ON p.user_id = users.id
				WHERE users.deleted = 0 AND users.id = ?
				LIMIT 1
				""", userId);
		return users.isEmpty() ? Map.of() : users.get(0);
	}

	private void requireUser(Long userId) {
		Long count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM users
				WHERE id = ? AND deleted = 0
				""", Long.class, userId);
		if (count == null || count == 0) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
		}
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

	private Integer optionalInteger(Object value) {
		if (value == null || !StringUtils.hasText(String.valueOf(value))) {
			return null;
		}
		return value instanceof Number number ? number.intValue() : Integer.valueOf(String.valueOf(value));
	}

	private boolean boolValue(Object value) {
		return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value))
				|| "1".equals(String.valueOf(value));
	}

	private boolean hasAnyKey(Map<String, Object> body, String... keys) {
		if (body == null) {
			return false;
		}
		for (String key : keys) {
			if (body.containsKey(key)) {
				return true;
			}
		}
		return false;
	}
}
