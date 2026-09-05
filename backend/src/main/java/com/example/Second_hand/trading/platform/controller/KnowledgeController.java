package com.example.Second_hand.trading.platform.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Second_hand.trading.platform.dto.ApiResponse;
import com.example.Second_hand.trading.platform.service.KnowledgeService;

@RestController
@RequestMapping("/api/admin/knowledge-documents")
public class KnowledgeController {
	private final KnowledgeService knowledgeService;

	public KnowledgeController(KnowledgeService knowledgeService) { this.knowledgeService = knowledgeService; }

	@GetMapping
	public ApiResponse<List<Map<String, Object>>> list() { return ApiResponse.success(knowledgeService.documents()); }

	@PostMapping
	public ApiResponse<Map<String, Object>> create(@RequestAttribute("authId") Long adminId, @RequestBody Map<String, Object> body) {
		return ApiResponse.success(knowledgeService.create(adminId, body));
	}

	@PutMapping("/{documentId}")
	public ApiResponse<Map<String, Object>> update(@RequestAttribute("authId") Long adminId,
			@PathVariable Long documentId, @RequestBody Map<String, Object> body) {
		return ApiResponse.success(knowledgeService.update(adminId, documentId, body));
	}

	@PatchMapping("/{documentId}/publish")
	public ApiResponse<Map<String, Object>> publish(@RequestAttribute("authId") Long adminId, @PathVariable Long documentId) {
		return ApiResponse.success(knowledgeService.publish(adminId, documentId));
	}

	@PostMapping("/reindex")
	public ApiResponse<Map<String, Object>> reindex(@RequestAttribute("authId") Long adminId) {
		return ApiResponse.success(Map.of("queued", knowledgeService.reindexPublished(adminId)));
	}
}
