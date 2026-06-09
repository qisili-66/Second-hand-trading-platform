package com.example.Second_hand.trading.platform.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.Second_hand.trading.platform.dto.ApiResponse;
import com.example.Second_hand.trading.platform.dto.PageResponse;
import com.example.Second_hand.trading.platform.service.ReviewService;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
	private final ReviewService reviewService;

	public ReviewController(ReviewService reviewService) {
		this.reviewService = reviewService;
	}

	@PostMapping
	public ApiResponse<Map<String, Object>> create(
			@RequestAttribute("authId") Long authId,
			@RequestBody Map<String, Object> body) {
		return ApiResponse.success(reviewService.createReview(authId, body));
	}

	@GetMapping("/user/{userId}")
	public ApiResponse<PageResponse<Map<String, Object>>> userReviews(
			@PathVariable Integer userId,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "10") int pageSize) {
		return ApiResponse.success(PageResponse.page(reviewService.reviewsByTarget(userId.longValue()), page, pageSize));
	}

	@GetMapping("/user/{userId}/stats")
	public ApiResponse<Map<String, Object>> userReviewStats(@PathVariable Integer userId) {
		return ApiResponse.success(reviewService.stats(userId.longValue()));
	}
}
