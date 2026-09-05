package com.example.Second_hand.trading.platform.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Second_hand.trading.platform.dto.ApiResponse;
import com.example.Second_hand.trading.platform.service.AgentRunService;
import com.example.Second_hand.trading.platform.service.KnowledgeService;

/** Internal, service-token protected read-only tool gateway for the AI service. */
@RestController
@RequestMapping("/api/internal/agent-tools")
public class AgentToolController {
	private final AgentRunService agentRunService;
	private final KnowledgeService knowledgeService;

	public AgentToolController(AgentRunService agentRunService, KnowledgeService knowledgeService) {
		this.agentRunService = agentRunService;
		this.knowledgeService = knowledgeService;
	}

	@PostMapping("/search-items")
	public ApiResponse<Map<String, Object>> searchItems(@RequestBody Map<String, Object> body) {
		return ApiResponse.success(agentRunService.searchItems(body));
	}

	@PostMapping("/item-realtime")
	public ApiResponse<Map<String, Object>> itemRealtime(@RequestBody Map<String, Object> body) {
		return ApiResponse.success(agentRunService.itemRealtime(body));
	}

	@PostMapping("/seller-summary")
	public ApiResponse<Map<String, Object>> sellerSummary(@RequestBody Map<String, Object> body) {
		return ApiResponse.success(agentRunService.sellerSummary(body));
	}

	@PostMapping("/order-status")
	public ApiResponse<Map<String, Object>> orderStatus(@RequestBody Map<String, Object> body) {
		return ApiResponse.success(agentRunService.orderStatus(body));
	}

	@PostMapping("/user-preferences")
	public ApiResponse<Map<String, Object>> userPreferences(@RequestBody Map<String, Object> body) {
		return ApiResponse.success(agentRunService.userPreferences(body));
	}

	@PostMapping("/trade-rules")
	public ApiResponse<Map<String, Object>> tradeRules(@RequestBody Map<String, Object> body) {
		return ApiResponse.success(agentRunService.tradeRules());
	}

	@PostMapping("/knowledge-outbox/claim")
	public ApiResponse<java.util.List<Map<String, Object>>> claimOutbox(@RequestBody(required = false) Map<String, Object> body) {
		Object value = body == null ? null : body.get("limit");
		int limit = value instanceof Number number ? number.intValue() : 20;
		return ApiResponse.success(knowledgeService.claimOutbox(limit));
	}

	@PostMapping("/knowledge-outbox/source")
	public ApiResponse<Map<String, Object>> source(@RequestBody Map<String, Object> body) {
		return ApiResponse.success(knowledgeService.sourceForOutbox(body));
	}

	@PostMapping("/knowledge-outbox/complete")
	public ApiResponse<Boolean> completeOutbox(@RequestBody Map<String, Object> body) {
		Long id = body.get("id") instanceof Number number ? number.longValue() : null;
		knowledgeService.completeOutbox(id, Boolean.TRUE.equals(body.get("success")), String.valueOf(body.getOrDefault("error", "")));
		return ApiResponse.success(true);
	}
}
