package com.example.Second_hand.trading.platform.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
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
import com.example.Second_hand.trading.platform.dto.PageResponse;
import com.example.Second_hand.trading.platform.service.AdminService;
import com.example.Second_hand.trading.platform.service.AgentInsightsService;
import com.example.Second_hand.trading.platform.service.AuthService;
import com.example.Second_hand.trading.platform.service.ItemService;
import com.example.Second_hand.trading.platform.service.TradeWorkflowService;
import com.example.Second_hand.trading.platform.service.UserService;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
	private final AdminService adminService;
	private final AuthService authService;
	private final UserService userService;
	private final ItemService itemService;
	private final TradeWorkflowService tradeWorkflowService;
	private final AgentInsightsService agentInsightsService;

	public AdminController(AdminService adminService, AuthService authService, UserService userService, ItemService itemService,
			TradeWorkflowService tradeWorkflowService, AgentInsightsService agentInsightsService) {
		this.adminService = adminService;
		this.authService = authService;
		this.userService = userService;
		this.itemService = itemService;
		this.tradeWorkflowService = tradeWorkflowService;
		this.agentInsightsService = agentInsightsService;
	}

	@GetMapping("/dashboard")
	public ApiResponse<Map<String, Object>> dashboard() {
		return ApiResponse.success(adminService.dashboard());
	}

	@GetMapping("/agent-operations")
	public ApiResponse<Map<String, Object>> agentOperations() {
		return ApiResponse.success(agentInsightsService.operationsInsights());
	}

	@PatchMapping("/password")
	public ApiResponse<Boolean> changePassword(
			@RequestAttribute("authId") Long authId,
			@RequestBody Map<String, Object> body) {
		return ApiResponse.success(authService.changeAdminPassword(authId, body));
	}

	@GetMapping("/users")
	public ApiResponse<PageResponse<Map<String, Object>>> users() {
		return ApiResponse.success(PageResponse.of(userService.users(), 1, 10));
	}

	@PatchMapping("/users/{userId}/disable")
	public ApiResponse<Boolean> disableUser(@PathVariable Integer userId) {
		return ApiResponse.success(userService.setUserStatus(userId, "DISABLED"));
	}

	@PatchMapping("/users/{userId}/enable")
	public ApiResponse<Boolean> enableUser(@PathVariable Integer userId) {
		return ApiResponse.success(userService.setUserStatus(userId, "NORMAL"));
	}

	@PatchMapping("/users/{userId}/verify")
	public ApiResponse<Boolean> verifyUser(@PathVariable Integer userId) {
		return ApiResponse.success(userService.verifyUser(userId));
	}

	@PatchMapping("/users/verify-pending")
	public ApiResponse<Map<String, Object>> verifyPendingUsers() {
		return ApiResponse.success(Map.of("updated", userService.verifyPendingUsers()));
	}

	@GetMapping("/items")
	public ApiResponse<PageResponse<Map<String, Object>>> items() {
		return ApiResponse.success(PageResponse.of(itemService.items(), 1, 10));
	}

	@PostMapping("/items")
	public ApiResponse<Map<String, Object>> createItem(@RequestBody Map<String, Object> body) {
		return ApiResponse.success(itemService.adminCreateItem(body));
	}

	@PatchMapping("/items/{itemId}/remove")
	public ApiResponse<Boolean> removeItem(@PathVariable Integer itemId, @RequestBody(required = false) Map<String, Object> body) {
		return ApiResponse.success(itemService.adminOffShelfItem(itemId));
	}

	@PatchMapping("/items/{itemId}/off-shelf")
	public ApiResponse<Boolean> offShelfItem(@PathVariable Integer itemId, @RequestBody(required = false) Map<String, Object> body) {
		return ApiResponse.success(itemService.adminOffShelfItem(itemId));
	}

	@PatchMapping("/items/{itemId}/on-shelf")
	public ApiResponse<Boolean> onShelfItem(@PathVariable Integer itemId, @RequestBody(required = false) Map<String, Object> body) {
		return ApiResponse.success(itemService.adminOnShelfItem(itemId));
	}

	@DeleteMapping("/items/{itemId}")
	public ApiResponse<Boolean> deleteItem(@PathVariable Integer itemId) {
		return ApiResponse.success(itemService.adminSoftDeleteItem(itemId));
	}

	@GetMapping("/categories")
	public ApiResponse<List<Map<String, Object>>> categories() {
		return ApiResponse.success(adminService.categories());
	}

	@PostMapping("/categories")
	public ApiResponse<Map<String, Object>> createCategory(@RequestBody Map<String, Object> body) {
		return ApiResponse.success(adminService.createCategory(body));
	}

	@PutMapping("/categories/{categoryId}")
	public ApiResponse<Boolean> updateCategory(@PathVariable Integer categoryId, @RequestBody Map<String, Object> body) {
		return ApiResponse.success(adminService.updateCategory(categoryId, body));
	}

	@DeleteMapping("/categories/{categoryId}")
	public ApiResponse<Boolean> deleteCategory(@PathVariable Integer categoryId) {
		return ApiResponse.success(adminService.deleteCategory(categoryId));
	}

	@GetMapping("/orders")
	public ApiResponse<PageResponse<Map<String, Object>>> orders() {
		return ApiResponse.success(PageResponse.of(tradeWorkflowService.orders(), 1, 10));
	}

	@GetMapping("/disputes")
	public ApiResponse<PageResponse<Map<String, Object>>> disputes() {
		return ApiResponse.success(PageResponse.of(adminService.disputes(), 1, 10));
	}

	@PatchMapping("/disputes/{disputeId}/resolve")
	public ApiResponse<Boolean> resolveDispute(
			@RequestAttribute("authId") Long authId,
			@PathVariable Integer disputeId,
			@RequestBody Map<String, Object> body) {
		return ApiResponse.success(adminService.resolveDispute(authId, disputeId, body));
	}

	@GetMapping("/reports")
	public ApiResponse<PageResponse<Map<String, Object>>> reports() {
		return ApiResponse.success(PageResponse.of(adminService.reports(), 1, 10));
	}

	@PatchMapping("/reports/{reportId}/approve")
	public ApiResponse<Boolean> approveReport(
			@RequestAttribute("authId") Long authId,
			@PathVariable Integer reportId,
			@RequestBody(required = false) Map<String, Object> body) {
		return ApiResponse.success(adminService.handleReport(authId, reportId, true, body));
	}

	@PatchMapping("/reports/{reportId}/reject")
	public ApiResponse<Boolean> rejectReport(
			@RequestAttribute("authId") Long authId,
			@PathVariable Integer reportId,
			@RequestBody(required = false) Map<String, Object> body) {
		return ApiResponse.success(adminService.handleReport(authId, reportId, false, body));
	}

	@GetMapping("/settings")
	public ApiResponse<Map<String, Object>> settings() {
		return ApiResponse.success(adminService.settings());
	}

	@PutMapping("/settings")
	public ApiResponse<Boolean> updateSettings(
			@RequestAttribute("authId") Long authId,
			@RequestBody Map<String, Object> body) {
		return ApiResponse.success(adminService.updateSettings(authId, body));
	}

	@GetMapping("/notices")
	public ApiResponse<PageResponse<Map<String, Object>>> notices() {
		return ApiResponse.success(PageResponse.of(adminService.notices(), 1, 10));
	}

	@PostMapping("/notices")
	public ApiResponse<Map<String, Object>> createNotice(
			@RequestAttribute("authId") Long authId,
			@RequestBody Map<String, Object> body) {
		return ApiResponse.success(adminService.createNotice(authId, body));
	}

	@PutMapping("/notices/{noticeId}")
	public ApiResponse<Boolean> updateNotice(@PathVariable Integer noticeId, @RequestBody Map<String, Object> body) {
		return ApiResponse.success(adminService.updateNotice(noticeId, body));
	}

	@DeleteMapping("/notices/{noticeId}")
	public ApiResponse<Boolean> deleteNotice(@PathVariable Integer noticeId) {
		return ApiResponse.success(adminService.deleteNotice(noticeId));
	}
}
