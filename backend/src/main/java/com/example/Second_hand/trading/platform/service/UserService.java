package com.example.Second_hand.trading.platform.service;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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

	public List<Map<String, Object>> users() {
		return jdbcTemplate.queryForList("""
				SELECT id AS userId, student_no AS studentNo, nickname, phone, status,
				  credit_score AS creditScore, created_at AS createdAt
				FROM users
				WHERE deleted = 0
				ORDER BY created_at DESC
				LIMIT 100
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
				SELECT id AS userId, student_no AS studentNo, nickname, real_name AS realName,
				  phone, email, avatar_url AS avatarUrl, campus, department,
				  enrollment_year AS enrollmentYear, credit_score AS creditScore,
				  created_at AS createdAt
				FROM users
				WHERE deleted = 0 AND id = ?
				LIMIT 1
				""", userId);
		return users.isEmpty() ? Map.of() : users.get(0);
	}
}
