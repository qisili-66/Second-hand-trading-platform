package com.example.Second_hand.trading.platform.service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
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
import com.example.Second_hand.trading.platform.entity.ChatEntity;
import com.example.Second_hand.trading.platform.entity.ChatMessageEntity;
import com.example.Second_hand.trading.platform.entity.OrderEntity;
import com.example.Second_hand.trading.platform.entity.OrderStatusLogEntity;
import com.example.Second_hand.trading.platform.mapper.ChatMapper;
import com.example.Second_hand.trading.platform.mapper.ChatMessageMapper;
import com.example.Second_hand.trading.platform.mapper.OrderMapper;
import com.example.Second_hand.trading.platform.mapper.OrderStatusLogMapper;

@Service
public class TradeWorkflowService {
	private final JdbcTemplate jdbcTemplate;
	private final OrderMapper orderMapper;
	private final OrderStatusLogMapper orderStatusLogMapper;
	private final ChatMapper chatMapper;
	private final ChatMessageMapper chatMessageMapper;
	private final PaymentService paymentService;
	private final MessageService messageService;
	private final SecureRandom secureRandom = new SecureRandom();

	public TradeWorkflowService(JdbcTemplate jdbcTemplate, OrderMapper orderMapper,
			OrderStatusLogMapper orderStatusLogMapper, ChatMapper chatMapper,
			ChatMessageMapper chatMessageMapper, PaymentService paymentService, MessageService messageService) {
		this.jdbcTemplate = jdbcTemplate;
		this.orderMapper = orderMapper;
		this.orderStatusLogMapper = orderStatusLogMapper;
		this.chatMapper = chatMapper;
		this.chatMessageMapper = chatMessageMapper;
		this.paymentService = paymentService;
		this.messageService = messageService;
	}

	@Transactional
	public Map<String, Object> createOrder(Long buyerId, Map<String, Object> body) {
		Long itemId = requiredLong(body.get("itemId"), "商品 ID");
		Map<String, Object> item = itemForOrder(itemId);
		Long sellerId = toLong(item.get("sellerId"));
		if (sellerId.equals(buyerId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能购买自己发布的商品");
		}
		if (!"ON_SALE".equals(String.valueOf(item.get("status")))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品不是在售状态");
		}

		OrderEntity order = new OrderEntity();
		order.setOrderNo("OD" + System.currentTimeMillis() + randomDigits());
		order.setItemId(itemId);
		order.setBuyerId(buyerId);
		order.setSellerId(sellerId);
		order.setAmount((BigDecimal) item.get("price"));
		order.setStatus("PENDING");
		order.setTradeMode(optionalText(body.get("tradeMode"), "OFFLINE"));
		order.setTradeCode("TC" + randomDigits() + randomDigits());
		order.setBuyerMessage(optionalText(body.get("message"), ""));
		orderMapper.insert(order);
		writeOrderLog(order.getId(), null, "PENDING", buyerId, "USER", "创建订单");
		messageService.createNotification(sellerId, "ORDER", "收到新的商品预约",
				displayName(buyerId) + " 预约了你的商品「" + item.get("title") + "」，请在个人中心的我的订单里处理。");
		return orderDetailForUser(order.getId(), buyerId);
	}

	public List<Map<String, Object>> orders(Long userId) {
		return jdbcTemplate.query("""
				SELECT o.*, i.title AS item_title,
				  (SELECT image_url FROM item_images img WHERE img.item_id = o.item_id ORDER BY sort_order, id LIMIT 1) AS cover_url
				FROM orders o
				LEFT JOIN items i ON i.id = o.item_id
				WHERE o.buyer_id = ? OR o.seller_id = ?
				ORDER BY o.created_at DESC
				LIMIT 100
				""", (rs, rowNum) -> orderRow(rs), userId, userId);
	}

	public List<Map<String, Object>> orders() {
		return jdbcTemplate.query("""
				SELECT o.*, i.title AS item_title,
				  (SELECT image_url FROM item_images img WHERE img.item_id = o.item_id ORDER BY sort_order, id LIMIT 1) AS cover_url
				FROM orders o
				LEFT JOIN items i ON i.id = o.item_id
				ORDER BY o.created_at DESC
				LIMIT 100
				""", (rs, rowNum) -> orderRow(rs));
	}

	public Map<String, Object> orderDetailForUser(Long orderId, Long userId) {
		OrderEntity order = requireOrder(orderId);
		ensureOrderParticipant(order, userId);
		return orderDetail(orderId.intValue());
	}

	public Map<String, Object> orderDetail(Integer orderId) {
		List<Map<String, Object>> rows = jdbcTemplate.query("""
				SELECT o.*, i.title AS item_title,
				  (SELECT image_url FROM item_images img WHERE img.item_id = o.item_id ORDER BY sort_order, id LIMIT 1) AS cover_url
				FROM orders o
				LEFT JOIN items i ON i.id = o.item_id
				WHERE o.id = ?
				""", (rs, rowNum) -> orderRow(rs), orderId);

		if (rows.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
		}

		Map<String, Object> row = new LinkedHashMap<>(rows.get(0));
		row.put("statusLogs", orderLogs(orderId.longValue()));
		row.put("payments", paymentService.payments(orderId.longValue()));
		return row;
	}

	@Transactional
	public boolean acceptOrder(Long sellerId, Integer orderId) {
		OrderEntity order = requireOrder(orderId.longValue());
		if (!order.getSellerId().equals(sellerId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有卖家可以接单");
		}
		transition(order, "ACCEPTED", sellerId, "卖家接单", "PENDING");
		jdbcTemplate.update("UPDATE items SET status = 'RESERVED' WHERE id = ? AND status = 'ON_SALE'", order.getItemId());
		messageService.pushOrderNotification(order, "订单已接单", "订单 " + order.getOrderNo() + " 已由卖家接单。");
		return true;
	}

	@Transactional
	public boolean cancelOrder(Long userId, Integer orderId, Map<String, Object> body) {
		OrderEntity order = requireOrder(orderId.longValue());
		ensureOrderParticipant(order, userId);
		if ("COMPLETED".equals(order.getStatus())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已完成订单不能取消");
		}
		if ("CANCELLED".equals(order.getStatus())) {
			return true;
		}
		String fromStatus = order.getStatus();
		order.setCancelReason(optionalText(body == null ? null : body.get("reason"), "用户取消"));
		order.setStatus("CANCELLED");
		orderMapper.updateById(order);
		writeOrderLog(order.getId(), fromStatus, "CANCELLED", userId, "USER", order.getCancelReason());
		jdbcTemplate.update("UPDATE items SET status = 'ON_SALE' WHERE id = ? AND status <> 'SOLD'", order.getItemId());
		messageService.pushOrderNotification(order, "订单已取消", "订单 " + order.getOrderNo() + " 已取消：" + order.getCancelReason());
		return true;
	}

	@Transactional
	public boolean completeOrder(Long userId, Integer orderId) {
		OrderEntity order = requireOrder(orderId.longValue());
		ensureOrderParticipant(order, userId);
		if (!List.of("ACCEPTED", "PAID").contains(order.getStatus())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前订单状态不能完成");
		}
		String fromStatus = order.getStatus();
		order.setStatus("COMPLETED");
		order.setCompletedAt(LocalDateTime.now());
		orderMapper.updateById(order);
		writeOrderLog(order.getId(), fromStatus, "COMPLETED", userId, "USER", "完成交易");
		jdbcTemplate.update("UPDATE items SET status = 'SOLD' WHERE id = ?", order.getItemId());
		messageService.pushOrderNotification(order, "订单已完成", "订单 " + order.getOrderNo() + " 已完成，买家可对卖家评价。");
		return true;
	}

	@Transactional
	public Map<String, Object> createPayment(Long userId, Integer orderId, Map<String, Object> body) {
		OrderEntity order = requireOrder(orderId.longValue());
		if (!order.getBuyerId().equals(userId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有买家可以支付");
		}
		if (!List.of("PENDING", "ACCEPTED", "PAYING").contains(order.getStatus())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前订单状态不能支付");
		}
		String provider = optionalText(body == null ? null : body.get("provider"), "ALIPAY");
		Map<String, Object> payment = paymentService.createPayment(order, provider);
		if (!"PAYING".equals(order.getStatus())) {
			transition(order, "PAYING", userId, "创建支付单", "PENDING", "ACCEPTED");
		}
		messageService.pushOrderNotification(order, "订单进入支付中", "订单 " + order.getOrderNo() + " 已创建支付单。");
		return payment;
	}

	public List<Map<String, Object>> chats(Long userId) {
		return jdbcTemplate.query("""
				SELECT c.*, i.title AS item_title,
				  (SELECT image_url FROM item_images img WHERE img.item_id = c.item_id ORDER BY sort_order, id LIMIT 1) AS cover_url
				FROM chats c
				LEFT JOIN items i ON i.id = c.item_id
				WHERE c.buyer_id = ? OR c.seller_id = ?
				ORDER BY c.updated_at DESC
				LIMIT 100
				""", (rs, rowNum) -> chatRow(rs), userId, userId);
	}

	@Transactional
	public Map<String, Object> createOrGetChat(Long userId, Map<String, Object> body) {
		Long itemId = requiredLong(body.get("itemId"), "商品 ID");
		Map<String, Object> item = itemForOrder(itemId);
		Long sellerId = toLong(item.get("sellerId"));
		if (sellerId.equals(userId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能和自己创建会话");
		}

		ChatEntity chat = chatMapper.selectOne(Wrappers.lambdaQuery(ChatEntity.class)
				.eq(ChatEntity::getItemId, itemId)
				.eq(ChatEntity::getBuyerId, userId)
				.eq(ChatEntity::getSellerId, sellerId)
				.last("LIMIT 1"));
		if (chat == null) {
			chat = new ChatEntity();
			chat.setItemId(itemId);
			chat.setBuyerId(userId);
			chat.setSellerId(sellerId);
			chat.setLastMessage("");
			chatMapper.insert(chat);
		}
		return chatDetail(chat.getId(), userId);
	}

	public Map<String, Object> chatDetail(Long chatId, Long userId) {
		ChatEntity chat = requireChat(chatId);
		ensureChatParticipant(chat, userId);
		List<Map<String, Object>> rows = jdbcTemplate.query("""
				SELECT c.*, i.title AS item_title,
				  (SELECT image_url FROM item_images img WHERE img.item_id = c.item_id ORDER BY sort_order, id LIMIT 1) AS cover_url
				FROM chats c
				LEFT JOIN items i ON i.id = c.item_id
				WHERE c.id = ?
				""", (rs, rowNum) -> chatRow(rs), chatId);
		return rows.isEmpty() ? Map.of() : rows.get(0);
	}

	public List<Map<String, Object>> messages(Long userId, Integer chatId) {
		ChatEntity chat = requireChat(chatId.longValue());
		ensureChatParticipant(chat, userId);
		return jdbcTemplate.queryForList("""
				SELECT id AS messageId, chat_id AS chatId, sender_id AS senderId, message_type AS messageType,
				  content, image_url AS imageUrl, item_id AS itemId, filtered, read_at AS readAt, created_at AS createdAt
				FROM chat_messages
				WHERE chat_id = ?
				ORDER BY created_at ASC
				LIMIT 100
				""", chatId);
	}

	@Transactional
	public Map<String, Object> sendMessage(Long userId, Integer chatId, Map<String, Object> body) {
		ChatEntity chat = requireChat(chatId.longValue());
		ensureChatParticipant(chat, userId);
		String content = optionalText(body.get("content"), "");
		String imageUrl = optionalText(body.get("imageUrl"), "");
		Long itemId = body.get("itemId") == null ? null : requiredLong(body.get("itemId"), "商品 ID");
		String messageType = optionalText(body.get("messageType"), StringUtils.hasText(imageUrl) ? "IMAGE" : "TEXT");
		if (!StringUtils.hasText(content) && !StringUtils.hasText(imageUrl) && itemId == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "消息内容不能为空");
		}

		ChatMessageEntity message = new ChatMessageEntity();
		message.setChatId(chat.getId());
		message.setSenderId(userId);
		message.setMessageType(messageType);
		message.setContent(content);
		message.setImageUrl(imageUrl);
		message.setItemId(itemId);
		message.setFiltered(containsSensitive(content) ? 1 : 0);
		chatMessageMapper.insert(message);

		chat.setLastMessage(lastMessageText(message));
		chat.setLastMessageAt(LocalDateTime.now());
		chatMapper.updateById(chat);
		Map<String, Object> row = messageRow(message);
		messageService.pushChatMessage(chat.getBuyerId(), row);
		messageService.pushChatMessage(chat.getSellerId(), row);
		return row;
	}

	public List<Map<String, Object>> wantedPosts() {
		return jdbcTemplate.queryForList("""
				SELECT w.id AS postId, w.user_id AS userId, w.title, w.description,
				  w.category_id AS categoryId, c.name AS categoryName, w.campus,
				  w.budget_min AS budgetMin, w.budget_max AS budgetMax,
				  w.status, w.created_at AS createdAt, w.updated_at AS updatedAt
				FROM wanted_posts w
				LEFT JOIN categories c ON c.id = w.category_id
				ORDER BY w.created_at DESC
				LIMIT 100
				""");
	}

	public List<Map<String, Object>> swapRequests() {
		return jdbcTemplate.queryForList("""
				SELECT s.id AS swapRequestId, s.request_no AS requestNo, s.requester_id AS requesterId,
				  s.target_item_id AS targetItemId, target_item.title AS targetItemTitle,
				  s.offered_item_id AS offeredItemId, offered_item.title AS offeredItemTitle,
				  s.owner_id AS ownerId, s.status, s.message, s.handled_at AS handledAt,
				  s.created_at AS createdAt, s.updated_at AS updatedAt
				FROM swap_requests s
				LEFT JOIN items target_item ON target_item.id = s.target_item_id
				LEFT JOIN items offered_item ON offered_item.id = s.offered_item_id
				ORDER BY s.created_at DESC
				LIMIT 100
				""");
	}

	private void transition(OrderEntity order, String toStatus, Long userId, String remark, String... allowedFrom) {
		String fromStatus = order.getStatus();
		if (allowedFrom.length > 0 && !List.of(allowedFrom).contains(fromStatus)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前订单状态不能执行该操作");
		}
		order.setStatus(toStatus);
		orderMapper.updateById(order);
		writeOrderLog(order.getId(), fromStatus, toStatus, userId, "USER", remark);
	}

	private void writeOrderLog(Long orderId, String fromStatus, String toStatus, Long operatorId, String operatorType, String remark) {
		OrderStatusLogEntity log = new OrderStatusLogEntity();
		log.setOrderId(orderId);
		log.setFromStatus(fromStatus);
		log.setToStatus(toStatus);
		log.setOperatorId(operatorId);
		log.setOperatorType(operatorType);
		log.setRemark(remark);
		orderStatusLogMapper.insert(log);
	}

	private List<Map<String, Object>> orderLogs(Long orderId) {
		return jdbcTemplate.queryForList("""
				SELECT id AS logId, order_id AS orderId, from_status AS fromStatus, to_status AS toStatus,
				  operator_id AS operatorId, operator_type AS operatorType, remark, created_at AS createdAt
				FROM order_status_logs
				WHERE order_id = ?
				ORDER BY created_at ASC, id ASC
				""", orderId);
	}

	private Map<String, Object> itemForOrder(Long itemId) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
				SELECT id AS itemId, seller_id AS sellerId, title, price, status
				FROM items
				WHERE id = ? AND deleted = 0
				LIMIT 1
				""", itemId);
		if (rows.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
		}
		return rows.get(0);
	}

	private OrderEntity requireOrder(Long orderId) {
		OrderEntity order = orderMapper.selectById(orderId);
		if (order == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
		}
		return order;
	}

	private ChatEntity requireChat(Long chatId) {
		ChatEntity chat = chatMapper.selectById(chatId);
		if (chat == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
		}
		return chat;
	}

	private void ensureOrderParticipant(OrderEntity order, Long userId) {
		if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有该订单权限");
		}
	}

	private void ensureChatParticipant(ChatEntity chat, Long userId) {
		if (!chat.getBuyerId().equals(userId) && !chat.getSellerId().equals(userId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有该会话权限");
		}
	}

	private Map<String, Object> orderRow(ResultSet rs) throws SQLException {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("orderId", rs.getLong("id"));
		row.put("orderNo", rs.getString("order_no"));
		row.put("orderStatus", rs.getString("status"));
		row.put("message", rs.getString("buyer_message"));
		row.put("tradeMode", rs.getString("trade_mode"));
		row.put("tradeCode", rs.getString("trade_code"));
		row.put("tradeQrUrl", rs.getString("trade_qr_url"));
		row.put("cancelReason", rs.getString("cancel_reason"));
		row.put("completedAt", rs.getTimestamp("completed_at") == null ? "" : rs.getTimestamp("completed_at").toLocalDateTime().toString());
		row.put("item", Map.of(
				"itemId", rs.getLong("item_id"),
				"title", rs.getString("item_title") == null ? "" : rs.getString("item_title"),
				"price", rs.getBigDecimal("amount"),
				"coverUrl", rs.getString("cover_url") == null ? "" : rs.getString("cover_url")));
		row.put("buyer", userRow(rs.getLong("buyer_id")));
		row.put("seller", userRow(rs.getLong("seller_id")));
		row.put("reviewedByBuyer", hasReview(rs.getLong("id"), rs.getLong("buyer_id")));
		row.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime().toString());
		row.put("updatedAt", rs.getTimestamp("updated_at").toLocalDateTime().toString());
		return row;
	}

	private Map<String, Object> chatRow(ResultSet rs) throws SQLException {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("chatId", rs.getLong("id"));
		row.put("itemId", rs.getLong("item_id"));
		row.put("buyer", userRow(rs.getLong("buyer_id")));
		row.put("seller", userRow(rs.getLong("seller_id")));
		row.put("lastMessage", rs.getString("last_message") == null ? "" : rs.getString("last_message"));
		row.put("lastMessageAt", rs.getTimestamp("last_message_at") == null ? "" : rs.getTimestamp("last_message_at").toLocalDateTime().toString());
		row.put("updatedAt", rs.getTimestamp("updated_at").toLocalDateTime().toString());
		row.put("item", Map.of(
				"itemId", rs.getLong("item_id"),
				"title", rs.getString("item_title") == null ? "" : rs.getString("item_title"),
				"coverUrl", rs.getString("cover_url") == null ? "" : rs.getString("cover_url")));
		return row;
	}

	private Map<String, Object> messageRow(ChatMessageEntity message) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("messageId", message.getId());
		row.put("chatId", message.getChatId());
		row.put("senderId", message.getSenderId());
		row.put("messageType", message.getMessageType());
		row.put("content", message.getContent());
		row.put("imageUrl", message.getImageUrl());
		row.put("itemId", message.getItemId());
		row.put("filtered", Integer.valueOf(1).equals(message.getFiltered()));
		row.put("createdAt", message.getCreatedAt() == null ? "" : message.getCreatedAt().toString());
		return row;
	}

	private Map<String, Object> userRow(Long userId) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
				SELECT id AS userId, nickname, avatar_url AS avatarUrl, campus
				FROM users
				WHERE id = ? AND deleted = 0
				LIMIT 1
				""", userId);
		if (rows.isEmpty()) {
			return Map.of("userId", userId, "nickname", "用户" + userId, "avatarUrl", "", "campus", "");
		}
		return rows.get(0);
	}

	private String displayName(Long userId) {
		Object nickname = userRow(userId).get("nickname");
		return nickname != null && StringUtils.hasText(String.valueOf(nickname)) ? String.valueOf(nickname) : "用户" + userId;
	}

	private boolean hasReview(Long orderId, Long reviewerId) {
		Long count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM reviews
				WHERE order_id = ? AND reviewer_id = ?
				""", Long.class, orderId, reviewerId);
		return count != null && count > 0;
	}

	private boolean containsSensitive(String content) {
		if (!StringUtils.hasText(content)) {
			return false;
		}
		return List.of("私下转账", "押金", "先付款", "脱离平台").stream().anyMatch(content::contains);
	}

	private String lastMessageText(ChatMessageEntity message) {
		if ("IMAGE".equals(message.getMessageType())) {
			return "[图片]";
		}
		if ("ITEM".equals(message.getMessageType())) {
			return "[商品卡片]";
		}
		return message.getContent();
	}

	private Long requiredLong(Object value, String label) {
		if (value == null || !StringUtils.hasText(String.valueOf(value))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写" + label);
		}
		if (value instanceof Number number) {
			return number.longValue();
		}
		return Long.valueOf(String.valueOf(value));
	}

	private Long toLong(Object value) {
		if (value instanceof Number number) {
			return number.longValue();
		}
		return Long.valueOf(String.valueOf(value));
	}

	private String optionalText(Object value, String fallback) {
		return value == null || !StringUtils.hasText(String.valueOf(value)) ? fallback : String.valueOf(value).trim();
	}

	private String randomDigits() {
		return String.valueOf(secureRandom.nextInt(9000) + 1000);
	}
}
