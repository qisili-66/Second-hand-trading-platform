package com.example.Second_hand.trading.platform.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Second_hand.trading.platform.dto.ApiResponse;
import com.example.Second_hand.trading.platform.dto.PageResponse;
import com.example.Second_hand.trading.platform.service.ReviewService;
import com.example.Second_hand.trading.platform.service.TradeWorkflowService;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
	private final TradeWorkflowService tradeWorkflowService;
	private final ReviewService reviewService;

	public OrderController(TradeWorkflowService tradeWorkflowService, ReviewService reviewService) {
		this.tradeWorkflowService = tradeWorkflowService;
		this.reviewService = reviewService;
	}

	@PostMapping
	public ApiResponse<Map<String, Object>> create(
			@RequestAttribute("authId") Long authId,
			@RequestBody Map<String, Object> body) {
		return ApiResponse.success(tradeWorkflowService.createOrder(authId, body));
	}

	@GetMapping
	public ApiResponse<PageResponse<Map<String, Object>>> list(@RequestAttribute("authId") Long authId) {
		return ApiResponse.success(PageResponse.of(tradeWorkflowService.orders(authId), 1, 10));
	}

	@GetMapping("/{orderId}")
	public ApiResponse<Map<String, Object>> detail(
			@RequestAttribute("authId") Long authId,
			@PathVariable Integer orderId) {
		return ApiResponse.success(tradeWorkflowService.orderDetailForUser(orderId.longValue(), authId));
	}

	@PatchMapping("/{orderId}/accept")
	public ApiResponse<Boolean> accept(
			@RequestAttribute("authId") Long authId,
			@PathVariable Integer orderId) {
		return ApiResponse.success(tradeWorkflowService.acceptOrder(authId, orderId));
	}

	@PatchMapping("/{orderId}/cancel")
	public ApiResponse<Boolean> cancel(
			@RequestAttribute("authId") Long authId,
			@PathVariable Integer orderId,
			@RequestBody(required = false) Map<String, Object> body) {
		return ApiResponse.success(tradeWorkflowService.cancelOrder(authId, orderId, body));
	}

	@PatchMapping("/{orderId}/complete")
	public ApiResponse<Boolean> complete(
			@RequestAttribute("authId") Long authId,
			@PathVariable Integer orderId) {
		return ApiResponse.success(tradeWorkflowService.completeOrder(authId, orderId));
	}

	@PostMapping("/{orderId}/pay")
	public ApiResponse<Map<String, Object>> pay(
			@RequestAttribute("authId") Long authId,
			@PathVariable Integer orderId,
			@RequestBody Map<String, Object> body) {
		return ApiResponse.success(tradeWorkflowService.createPayment(authId, orderId, body));
	}

	@PostMapping("/{orderId}/reviews")
	public ApiResponse<Map<String, Object>> review(
			@RequestAttribute("authId") Long authId,
			@PathVariable Integer orderId,
			@RequestBody Map<String, Object> body) {
		return ApiResponse.success(reviewService.createOrderReview(authId, orderId, body));
	}
}
