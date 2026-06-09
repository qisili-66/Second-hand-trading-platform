package com.example.Second_hand.trading.platform.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.Second_hand.trading.platform.dto.ApiResponse;
import com.example.Second_hand.trading.platform.dto.PageResponse;
import com.example.Second_hand.trading.platform.service.ReviewService;
import com.example.Second_hand.trading.platform.service.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {
	private final UserService userService;
	private final ReviewService reviewService;

	public UserController(UserService userService, ReviewService reviewService) {
		this.userService = userService;
		this.reviewService = reviewService;
	}

	@GetMapping("/me")
	public ApiResponse<Map<String, Object>> me(
			@RequestAttribute("authId") Long authId) {
		return ApiResponse.success(userService.currentUser(authId));
	}

	@PutMapping("/me")
	public ApiResponse<Boolean> updateMe(@RequestBody Map<String, Object> body) {
		return ApiResponse.success(true);
	}

	@GetMapping("/me/items")
	public ApiResponse<PageResponse<Map<String, Object>>> myItems(
			@RequestAttribute("authId") Long authId) {
		return ApiResponse.success(PageResponse.of(userService.myItems(authId), 1, 10));
	}

	@GetMapping("/me/favorites")
	public ApiResponse<PageResponse<Map<String, Object>>> myFavorites(
			@RequestAttribute("authId") Long authId) {
		return ApiResponse.success(PageResponse.of(userService.myFavorites(authId), 1, 10));
	}

	@GetMapping("/me/notifications")
	public ApiResponse<PageResponse<Map<String, Object>>> myNotifications(
			@RequestAttribute("authId") Long authId) {
		return ApiResponse.success(PageResponse.of(userService.notifications(authId), 1, 10));
	}

	@GetMapping("/{userId}/reviews")
	public ApiResponse<PageResponse<Map<String, Object>>> reviews(
			@PathVariable Integer userId,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "10") int pageSize) {
		return ApiResponse.success(PageResponse.page(reviewService.reviewsByTarget(userId.longValue()), page, pageSize));
	}
}
