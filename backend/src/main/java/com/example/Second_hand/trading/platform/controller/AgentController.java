package com.example.Second_hand.trading.platform.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Second_hand.trading.platform.dto.ApiResponse;
import com.example.Second_hand.trading.platform.service.AgentInsightsService;
import com.example.Second_hand.trading.platform.service.AgentRunService;

@RestController
@RequestMapping("/api/agent")
public class AgentController {
	private final AgentRunService agentRunService;
	private final AgentInsightsService agentInsightsService;

	public AgentController(AgentRunService agentRunService, AgentInsightsService agentInsightsService) {
		this.agentRunService = agentRunService;
		this.agentInsightsService = agentInsightsService;
	}

	@PostMapping("/buyer")
	public ApiResponse<Map<String, Object>> buyer(
			@RequestAttribute("authId") Long authId,
			@RequestBody(required = false) Map<String, Object> body) {
		return ApiResponse.success(agentRunService.createBuyerRun(authId, body));
	}

	@PostMapping("/buyer/runs")
	public ApiResponse<Map<String, Object>> buyerRun(
			@RequestAttribute("authId") Long authId,
			@RequestBody(required = false) Map<String, Object> body) {
		return ApiResponse.success(agentRunService.createBuyerRun(authId, body));
	}

	@GetMapping("/runs")
	public ApiResponse<java.util.List<Map<String, Object>>> runs(@RequestAttribute("authId") Long authId) {
		return ApiResponse.success(agentRunService.runsForUser(authId));
	}

	@GetMapping("/runs/{runId}")
	public ApiResponse<Map<String, Object>> run(
			@RequestAttribute("authId") Long authId, @PathVariable String runId) {
		return ApiResponse.success(agentRunService.runForUser(authId, runId));
	}

	@DeleteMapping("/runs")
	public ApiResponse<Boolean> clearRuns(@RequestAttribute("authId") Long authId) {
		agentRunService.clearRunsForUser(authId);
		return ApiResponse.success(true);
	}

	@GetMapping("/insights/buyer")
	public ApiResponse<Map<String, Object>> buyerInsights(@RequestAttribute("authId") Long authId) {
		return ApiResponse.success(agentInsightsService.buyerInsights(authId));
	}

	@GetMapping("/insights/seller")
	public ApiResponse<Map<String, Object>> sellerInsights(@RequestAttribute("authId") Long authId) {
		return ApiResponse.success(agentInsightsService.sellerInsights(authId));
	}

}
