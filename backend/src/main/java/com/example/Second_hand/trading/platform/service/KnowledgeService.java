package com.example.Second_hand.trading.platform.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

/** Durable source-of-truth and transactional outbox for the Qdrant indexer. */
@Service
public class KnowledgeService {
	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;

	public KnowledgeService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	public List<Map<String, Object>> documents() {
		return jdbcTemplate.queryForList("""
				SELECT id AS documentId, document_key AS documentKey, document_type AS documentType, title,
				 content, status, version_no AS versionNo, source_ref AS sourceRef, updated_by AS updatedBy,
				 published_at AS publishedAt, updated_at AS updatedAt
				FROM knowledge_documents ORDER BY updated_at DESC, id DESC
				""");
	}

	@Transactional
	public Map<String, Object> create(Long adminId, Map<String, Object> body) {
		String type = type(body == null ? null : body.get("documentType"));
		String title = required(body == null ? null : body.get("title"), "标题");
		String content = required(body == null ? null : body.get("content"), "内容");
		String key = text(body, "documentKey");
		if (!StringUtils.hasText(key)) key = type.toLowerCase() + ":" + UUID.randomUUID();
		String status = "PUBLISHED".equalsIgnoreCase(text(body, "status")) ? "PUBLISHED" : "DRAFT";
		jdbcTemplate.update("""
				INSERT INTO knowledge_documents (document_key, document_type, title, content, status, version_no, source_ref, updated_by, published_at)
				VALUES (?, ?, ?, ?, ?, 1, ?, ?, CASE WHEN ? = 'PUBLISHED' THEN CURRENT_TIMESTAMP ELSE NULL END)
				""", key, type, title, content, status, text(body, "sourceRef"), adminId, status);
		Long id = jdbcTemplate.queryForObject("SELECT id FROM knowledge_documents WHERE document_key = ?", Long.class, key);
		enqueue("UPSERT", "KNOWLEDGE_DOCUMENT", key, Map.of("documentId", id, "documentKey", key));
		return document(id);
	}

	@Transactional
	public Map<String, Object> update(Long adminId, Long documentId, Map<String, Object> body) {
		Map<String, Object> current = document(documentId);
		String title = text(body, "title");
		String content = text(body, "content");
		String sourceRef = text(body, "sourceRef");
		jdbcTemplate.update("""
				UPDATE knowledge_documents
				SET title = ?, content = ?, source_ref = ?, updated_by = ?, version_no = version_no + 1
				WHERE id = ?
				""", StringUtils.hasText(title) ? title : current.get("title"),
				StringUtils.hasText(content) ? content : current.get("content"),
				StringUtils.hasText(sourceRef) ? sourceRef : current.get("sourceRef"), adminId, documentId);
		enqueue("UPSERT", "KNOWLEDGE_DOCUMENT", String.valueOf(current.get("documentKey")),
				Map.of("documentId", documentId, "documentKey", current.get("documentKey")));
		return document(documentId);
	}

	@Transactional
	public Map<String, Object> publish(Long adminId, Long documentId) {
		Map<String, Object> current = document(documentId);
		jdbcTemplate.update("""
				UPDATE knowledge_documents SET status = 'PUBLISHED', updated_by = ?, published_at = CURRENT_TIMESTAMP,
				 version_no = version_no + 1 WHERE id = ?
				""", adminId, documentId);
		enqueue("UPSERT", "KNOWLEDGE_DOCUMENT", String.valueOf(current.get("documentKey")),
				Map.of("documentId", documentId, "documentKey", current.get("documentKey")));
		return document(documentId);
	}

	@Transactional
	public int reindexPublished(Long adminId) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
				SELECT id AS documentId, document_key AS documentKey
				FROM knowledge_documents WHERE status = 'PUBLISHED'
				""");
		for (Map<String, Object> row : rows) {
			enqueue("UPSERT", "KNOWLEDGE_DOCUMENT", String.valueOf(row.get("documentKey")),
					Map.of("documentId", row.get("documentId"), "documentKey", row.get("documentKey"), "requestedBy", adminId));
		}
		return rows.size();
	}

	public void enqueueItem(Long itemId, String eventType) {
		if (itemId != null) enqueue(eventType, "ITEM", String.valueOf(itemId), Map.of("itemId", itemId));
	}

	public void enqueueReview(Long reviewId, Long itemId) {
		if (reviewId != null) enqueue("UPSERT", "REVIEW", String.valueOf(reviewId), Map.of("reviewId", reviewId, "itemId", itemId));
	}

	@Transactional
	public List<Map<String, Object>> claimOutbox(int requestedLimit) {
		int limit = Math.max(1, Math.min(requestedLimit, 50));
		// Recover claims left behind by a crashed or unauthorized worker.
		jdbcTemplate.update("UPDATE knowledge_outbox SET status = 'PENDING' WHERE status = 'PROCESSING' AND available_at <= CURRENT_TIMESTAMP AND attempts < 5");
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
				SELECT id, event_type AS eventType, aggregate_type AS aggregateType, aggregate_id AS aggregateId,
				 payload_json AS payloadJson, attempts
				FROM knowledge_outbox WHERE status = 'PENDING' AND available_at <= CURRENT_TIMESTAMP
				ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED
				""", limit);
		for (Map<String, Object> row : rows) {
			jdbcTemplate.update("UPDATE knowledge_outbox SET status = 'PROCESSING', attempts = attempts + 1, available_at = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 10 MINUTE) WHERE id = ?", row.get("id"));
		}
		return rows;
	}

	@Transactional
	public void completeOutbox(Long id, boolean success, String error) {
		if (id == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 Outbox ID");
		if (success) {
			jdbcTemplate.update("UPDATE knowledge_outbox SET status = 'SUCCEEDED', processed_at = CURRENT_TIMESTAMP, last_error = NULL WHERE id = ?", id);
			return;
		}
		jdbcTemplate.update("""
				UPDATE knowledge_outbox SET status = CASE WHEN attempts >= 5 THEN 'FAILED' ELSE 'PENDING' END,
				 available_at = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 5 MINUTE), last_error = ? WHERE id = ?
				""", truncate(error, 500), id);
	}

	public Map<String, Object> sourceForOutbox(Map<String, Object> event) {
		String aggregateType = String.valueOf(event.get("aggregateType"));
		String aggregateId = String.valueOf(event.get("aggregateId"));
		if ("KNOWLEDGE_DOCUMENT".equals(aggregateType)) {
			return sourceDocument(aggregateId);
		}
		if ("ITEM".equals(aggregateType)) {
			return sourceItem(aggregateId);
		}
		if ("REVIEW".equals(aggregateType)) {
			return sourceReview(aggregateId);
		}
		return Map.of("deleted", true);
	}

	private Map<String, Object> sourceDocument(String key) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
				SELECT document_key AS sourceId, document_type AS sourceType, title, content, version_no AS versionNo,
				 status, source_ref AS sourceRef FROM knowledge_documents WHERE document_key = ? LIMIT 1
				""", key);
		return rows.isEmpty() ? Map.of("deleted", true) : rows.get(0);
	}

	private Map<String, Object> sourceItem(String id) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
				SELECT CAST(i.id AS CHAR) AS sourceId, 'ITEM' AS sourceType, i.title,
				 CONCAT(i.description, '\n成色：', i.condition_level, '\n校区：', i.campus, '\n交易方式：', i.trade_modes) AS content,
				 UNIX_TIMESTAMP(i.updated_at) AS versionNo, i.status
				FROM items i WHERE i.id = ? AND i.deleted = 0 AND i.status = 'ON_SALE' LIMIT 1
				""", id);
		return rows.isEmpty() ? Map.of("deleted", true) : rows.get(0);
	}

	private Map<String, Object> sourceReview(String id) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
				SELECT CAST(r.id AS CHAR) AS sourceId, 'REVIEW' AS sourceType, CONCAT('商品评价：', i.title) AS title,
				 r.content, UNIX_TIMESTAMP(r.created_at) AS versionNo, 'PUBLISHED' AS status, CAST(o.item_id AS CHAR) AS sourceRef
				FROM reviews r JOIN orders o ON o.id = r.order_id JOIN items i ON i.id = o.item_id
				WHERE r.id = ? AND i.deleted = 0 AND i.status = 'ON_SALE' LIMIT 1
				""", id);
		return rows.isEmpty() ? Map.of("deleted", true) : rows.get(0);
	}

	private Map<String, Object> document(Long id) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
				SELECT id AS documentId, document_key AS documentKey, document_type AS documentType, title, content,
				 status, version_no AS versionNo, source_ref AS sourceRef, published_at AS publishedAt, updated_at AS updatedAt
				FROM knowledge_documents WHERE id = ? LIMIT 1
				""", id);
		if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "知识文档不存在");
		return rows.get(0);
	}

	private void enqueue(String eventType, String aggregateType, String aggregateId, Map<String, Object> payload) {
		jdbcTemplate.update("""
				INSERT INTO knowledge_outbox (event_type, aggregate_type, aggregate_id, payload_json)
				VALUES (?, ?, ?, ?)
				""", eventType, aggregateType, aggregateId, json(payload));
	}

	private String type(Object value) {
		String type = String.valueOf(value == null ? "POLICY" : value).trim().toUpperCase();
		return List.of("POLICY", "FAQ").contains(type) ? type : "POLICY";
	}

	private String text(Map<String, Object> body, String key) {
		return body == null || body.get(key) == null ? "" : String.valueOf(body.get(key)).trim();
	}

	private String required(Object value, String label) {
		String text = value == null ? "" : String.valueOf(value).trim();
		if (!StringUtils.hasText(text)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写" + label);
		return text;
	}

	private String json(Object value) {
		try { return objectMapper.writeValueAsString(value); } catch (Exception ignored) { return "{}"; }
	}

	private String truncate(String value, int max) {
		String text = value == null ? "" : value;
		return text.length() <= max ? text : text.substring(0, max);
	}
}
