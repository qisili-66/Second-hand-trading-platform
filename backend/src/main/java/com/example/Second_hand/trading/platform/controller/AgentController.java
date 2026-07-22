package com.example.Second_hand.trading.platform.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Second_hand.trading.platform.dto.ApiResponse;
import com.example.Second_hand.trading.platform.service.AgentService;

@RestController
@RequestMapping("/api/agent")
public class AgentController {
	private final AgentService agentService;

	public AgentController(AgentService agentService) {
		this.agentService = agentService;
	}

	@PostMapping("/buyer")
	public ApiResponse<Map<String, Object>> buyer(@RequestBody(required = false) Map<String, Object> body) {
		return ApiResponse.success(agentService.buyer(body));
	}

	@PostMapping("/seller")
	public ApiResponse<Map<String, Object>> seller(@RequestBody(required = false) Map<String, Object> body) {
		return ApiResponse.success(agentService.seller(body));
	}
}
