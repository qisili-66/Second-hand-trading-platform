package com.example.Second_hand.trading.platform.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.Second_hand.trading.platform.entity.OrderEntity;
import com.example.Second_hand.trading.platform.entity.ReviewEntity;
import com.example.Second_hand.trading.platform.mapper.OrderMapper;
import com.example.Second_hand.trading.platform.mapper.ReviewMapper;

@Service
public class ReviewService {
	private final JdbcTemplate jdbcTemplate;
	private final ReviewMapper reviewMapper;
	private final OrderMapper orderMapper;
	private final UserService userService;
	private final MessageService messageService;

	public ReviewService(JdbcTemplate jdbcTemplate, ReviewMapper reviewMapper, OrderMapper orderMapper,
			UserService userService, MessageService messageService) {
		this.jdbcTemplate = jdbcTemplate;
		this.reviewMapper = reviewMapper;
		this.orderMapper = orderMapper;
		this.userService = userService;
		this.messageService = messageService;
	}

	@Transactional
	public Map<String, Object> createReview(Long reviewerId, Map<String, Object> body) {
		Long orderId = requiredLong(body == null ? null : body.get("orderId"), "订单 ID");
		return createOrderReview(reviewerId, orderId.intValue(), body);
	}

	@Transactional
	public Map<String, Object> createOrderReview(Long reviewerId, Integer orderId, Map<String, Object> body) {
		if (reviewerId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
		}
		OrderEntity order = orderMapper.selectById(orderId);
		if (order == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
		}
		if (!order.getBuyerId().equals(reviewerId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有该订单买家可以评价");
		}
		if (!"COMPLETED".equals(order.getStatus())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只有已完成订单可以评价");
		}
		Long existing = reviewMapper.selectCount(Wrappers.lambdaQuery(ReviewEntity.class)
				.eq(ReviewEntity::getOrderId, order.getId())
				.eq(ReviewEntity::getReviewerId, reviewerId));
		if (existing != null && existing > 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该订单已评价");
		}

		int rating = rating(body == null ? null : body.get("rating"));
		String content = optionalText(body == null ? null : body.get("content"));
		if (content.length() > 500) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评价内容不能超过 500 字");
		}

		ReviewEntity review = new ReviewEntity();
		review.setOrderId(order.getId());
		review.setReviewerId(reviewerId);
		review.setTargetUserId(order.getSellerId());
		review.setRating(rating);
		review.setContent(content);
		reviewMapper.insert(review);

		userService.updateCreditScore(order.getSellerId(), rating);
		messageService.createNotification(order.getSellerId(), "SYSTEM", "收到新的交易评价",
				displayName(reviewerId) + " 已评价订单 " + order.getOrderNo() + "，评分 " + rating + " 星。");
		return reviewById(review.getId());
	}

	public List<Map<String, Object>> reviewsByTarget(Long userId) {
		if (userId == null) {
			return List.of();
		}
		return jdbcTemplate.query("""
				SELECT r.id AS reviewId, r.order_id AS orderId, r.reviewer_id AS reviewerId,
				  r.target_user_id AS targetUserId, r.rating, r.content, r.created_at AS createdAt,
				  o.order_no AS orderNo, o.item_id AS itemId, i.title AS itemTitle,
				  (SELECT image_url FROM item_images img WHERE img.item_id = o.item_id ORDER BY sort_order, id LIMIT 1) AS coverUrl,
				  reviewer.nickname AS reviewerName, target_user.nickname AS targetUserName
				FROM reviews r
				LEFT JOIN orders o ON o.id = r.order_id
				LEFT JOIN items i ON i.id = o.item_id
				LEFT JOIN users reviewer ON reviewer.id = r.reviewer_id
				LEFT JOIN users target_user ON target_user.id = r.target_user_id
				WHERE r.target_user_id = ?
				ORDER BY r.created_at DESC, r.id DESC
				LIMIT 100
				""", (rs, rowNum) -> reviewRow(rs), userId);
	}

	public Map<String, Object> stats(Long userId) {
		if (userId == null) {
			return Map.of("userId", null, "averageRating", BigDecimal.ZERO, "reviewCount", 0, "creditScore", 100);
		}
		Map<String, Object> stats = jdbcTemplate.queryForMap("""
				SELECT COUNT(*) AS reviewCount, COALESCE(AVG(rating), 0) AS averageRating
				FROM reviews
				WHERE target_user_id = ?
				""", userId);
		Map<String, Object> row = new LinkedHashMap<>();
		Object averageValue = stats.get("averageRating");
		BigDecimal average = averageValue instanceof BigDecimal value
				? value
				: BigDecimal.valueOf(averageValue instanceof Number number ? number.doubleValue() : 0);
		row.put("userId", userId);
		row.put("averageRating", average.setScale(1, RoundingMode.HALF_UP));
		row.put("reviewCount", stats.get("reviewCount"));
		row.put("creditScore", userService.getUserCreditScore(userId));
		row.put("rating5", ratingCount(userId, 5));
		row.put("rating4", ratingCount(userId, 4));
		row.put("rating3", ratingCount(userId, 3));
		row.put("rating2", ratingCount(userId, 2));
		row.put("rating1", ratingCount(userId, 1));
		return row;
	}

	private Map<String, Object> reviewById(Long reviewId) {
		List<Map<String, Object>> rows = jdbcTemplate.query("""
				SELECT r.id AS reviewId, r.order_id AS orderId, r.reviewer_id AS reviewerId,
				  r.target_user_id AS targetUserId, r.rating, r.content, r.created_at AS createdAt,
				  o.order_no AS orderNo, o.item_id AS itemId, i.title AS itemTitle,
				  (SELECT image_url FROM item_images img WHERE img.item_id = o.item_id ORDER BY sort_order, id LIMIT 1) AS coverUrl,
				  reviewer.nickname AS reviewerName, target_user.nickname AS targetUserName
				FROM reviews r
				LEFT JOIN orders o ON o.id = r.order_id
				LEFT JOIN items i ON i.id = o.item_id
				LEFT JOIN users reviewer ON reviewer.id = r.reviewer_id
				LEFT JOIN users target_user ON target_user.id = r.target_user_id
				WHERE r.id = ?
				LIMIT 1
				""", (rs, rowNum) -> reviewRow(rs), reviewId);
		if (rows.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评价不存在");
		}
		return rows.get(0);
	}

	private Map<String, Object> reviewRow(ResultSet rs) throws SQLException {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("reviewId", rs.getLong("reviewId"));
		row.put("orderId", rs.getLong("orderId"));
		row.put("orderNo", rs.getString("orderNo") == null ? "" : rs.getString("orderNo"));
		row.put("reviewerId", rs.getLong("reviewerId"));
		row.put("targetUserId", rs.getLong("targetUserId"));
		row.put("rating", rs.getInt("rating"));
		row.put("content", rs.getString("content") == null ? "" : rs.getString("content"));
		row.put("createdAt", rs.getTimestamp("createdAt") == null ? "" : rs.getTimestamp("createdAt").toLocalDateTime().toString());
		row.put("reviewer", Map.of(
				"userId", rs.getLong("reviewerId"),
				"nickname", rs.getString("reviewerName") == null ? "用户" + rs.getLong("reviewerId") : rs.getString("reviewerName")));
		row.put("targetUser", Map.of(
				"userId", rs.getLong("targetUserId"),
				"nickname", rs.getString("targetUserName") == null ? "用户" + rs.getLong("targetUserId") : rs.getString("targetUserName")));
		row.put("item", Map.of(
				"itemId", rs.getLong("itemId"),
				"title", rs.getString("itemTitle") == null ? "" : rs.getString("itemTitle"),
				"coverUrl", rs.getString("coverUrl") == null ? "" : rs.getString("coverUrl")));
		return row;
	}

	private Long ratingCount(Long userId, int rating) {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM reviews WHERE target_user_id = ? AND rating = ?",
				Long.class, userId, rating);
	}

	private int rating(Object value) {
		if (value == null || !StringUtils.hasText(String.valueOf(value))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写评分");
		}
		int rating = value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
		if (rating < 1 || rating > 5) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评分必须在 1-5 星之间");
		}
		return rating;
	}

	private Long requiredLong(Object value, String label) {
		if (value == null || !StringUtils.hasText(String.valueOf(value))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写" + label);
		}
		return value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
	}

	private String optionalText(Object value) {
		return value == null || !StringUtils.hasText(String.valueOf(value)) ? "" : String.valueOf(value).trim();
	}

	private String displayName(Long userId) {
		List<String> names = jdbcTemplate.queryForList("""
				SELECT nickname
				FROM users
				WHERE id = ? AND deleted = 0
				LIMIT 1
				""", String.class, userId);
		return names.isEmpty() || !StringUtils.hasText(names.get(0)) ? "用户" + userId : names.get(0);
	}
}
