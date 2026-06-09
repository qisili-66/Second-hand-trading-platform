package com.example.Second_hand.trading.platform.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.Second_hand.trading.platform.entity.NotificationEntity;
import com.example.Second_hand.trading.platform.entity.OrderEntity;
import com.example.Second_hand.trading.platform.mapper.NotificationMapper;

@Service
public class MessageService {
	private final SimpMessagingTemplate messagingTemplate;
	private final NotificationMapper notificationMapper;

	public MessageService(SimpMessagingTemplate messagingTemplate, NotificationMapper notificationMapper) {
		this.messagingTemplate = messagingTemplate;
		this.notificationMapper = notificationMapper;
	}

	public Map<String, Object> createNotification(Long userId, String type, String title, String content) {
		if (userId == null) {
			return Map.of();
		}

		NotificationEntity notification = new NotificationEntity();
		notification.setUserId(userId);
		notification.setType(normalizeType(type));
		notification.setTitle(truncate(title, 150));
		notification.setContent(truncate(content, 1000));
		notification.setCreatedAt(LocalDateTime.now());
		notificationMapper.insert(notification);

		Map<String, Object> payload = notificationRow(notification);
		messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/notifications", payload);
		return payload;
	}

	public void pushOrderNotification(OrderEntity order, String title, String content) {
		if (order == null) {
			return;
		}
		createNotification(order.getBuyerId(), "ORDER", title, content);
		if (!order.getBuyerId().equals(order.getSellerId())) {
			createNotification(order.getSellerId(), "ORDER", title, content);
		}
	}

	public void pushChatMessage(Long userId, Map<String, Object> message) {
		if (userId == null || message == null) {
			return;
		}
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("type", "CHAT");
		payload.put("message", message);
		payload.put("createdAt", LocalDateTime.now().toString());
		messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/messages", payload);
	}

	public void broadcast(String title, String content) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("type", "BROADCAST");
		payload.put("title", title);
		payload.put("content", content);
		payload.put("createdAt", LocalDateTime.now().toString());
		messagingTemplate.convertAndSend("/topic/broadcast", (Object) payload);
	}

	private Map<String, Object> notificationRow(NotificationEntity notification) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("notificationId", notification.getId());
		row.put("id", notification.getId());
		row.put("userId", notification.getUserId());
		row.put("type", notification.getType());
		row.put("title", notification.getTitle());
		row.put("content", notification.getContent());
		row.put("readAt", notification.getReadAt() == null ? "" : notification.getReadAt().toString());
		row.put("createdAt", notification.getCreatedAt() == null ? "" : notification.getCreatedAt().toString());
		return row;
	}

	private String normalizeType(String type) {
		return StringUtils.hasText(type) ? type.trim().toUpperCase() : "SYSTEM";
	}

	private String truncate(String value, int maxLength) {
		if (value == null) {
			return "";
		}
		return value.length() <= maxLength ? value : value.substring(0, maxLength);
	}
}
