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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.Second_hand.trading.platform.dto.ApiResponse;
import com.example.Second_hand.trading.platform.dto.ItemSearchCriteria;
import com.example.Second_hand.trading.platform.dto.PageResponse;
import com.example.Second_hand.trading.platform.service.ItemService;

@RestController
@RequestMapping("/api/items")
public class ItemController {
	private final ItemService itemService;

	public ItemController(ItemService itemService) {
		this.itemService = itemService;
	}

	@GetMapping
	public ApiResponse<PageResponse<Map<String, Object>>> list(
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) String categories,
			@RequestParam(required = false) String conditions,
			@RequestParam(required = false) String campus,
			@RequestParam(required = false) java.math.BigDecimal minPrice,
			@RequestParam(required = false) java.math.BigDecimal maxPrice,
			@RequestParam(defaultValue = "latest") String sort,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "10") int pageSize) {
		return ApiResponse.success(itemService.items(new ItemSearchCriteria(
				keyword, categoryId, categories, conditions, campus, minPrice, maxPrice, sort, page, pageSize)));
	}

	@PostMapping
	public ApiResponse<Map<String, Object>> create(
			@RequestAttribute("authId") Long authId,
			@RequestBody Map<String, Object> body) {
		return ApiResponse.success(itemService.createItem(authId, body));
	}

	@GetMapping("/{itemId}")
	public ApiResponse<Map<String, Object>> detail(@PathVariable Integer itemId) {
		return ApiResponse.success(itemService.itemDetail(itemId));
	}

	@PutMapping("/{itemId}")
	public ApiResponse<Boolean> update(
			@RequestAttribute("authId") Long authId,
			@PathVariable Integer itemId,
			@RequestBody Map<String, Object> body) {
		return ApiResponse.success(itemService.updateItem(authId, itemId, body));
	}

	@PatchMapping("/{itemId}/remove")
	public ApiResponse<Boolean> remove(
			@RequestAttribute("authId") Long authId,
			@PathVariable Integer itemId) {
		return ApiResponse.success(itemService.offShelfItem(authId, itemId));
	}

	@PatchMapping("/{itemId}/off-shelf")
	public ApiResponse<Boolean> offShelf(
			@RequestAttribute("authId") Long authId,
			@PathVariable Integer itemId) {
		return ApiResponse.success(itemService.offShelfItem(authId, itemId));
	}

	@PatchMapping("/{itemId}/on-shelf")
	public ApiResponse<Boolean> onShelf(
			@RequestAttribute("authId") Long authId,
			@PathVariable Integer itemId) {
		return ApiResponse.success(itemService.onShelfItem(authId, itemId));
	}

	@DeleteMapping("/{itemId}")
	public ApiResponse<Boolean> delete(
			@RequestAttribute("authId") Long authId,
			@PathVariable Integer itemId) {
		return ApiResponse.success(itemService.softDeleteItem(authId, itemId));
	}

	@PostMapping("/{itemId}/favorite")
	public ApiResponse<Boolean> favorite(
			@RequestAttribute("authId") Long authId,
			@PathVariable Integer itemId) {
		return ApiResponse.success(itemService.favorite(authId, itemId));
	}

	@DeleteMapping("/{itemId}/favorite")
	public ApiResponse<Boolean> unfavorite(
			@RequestAttribute("authId") Long authId,
			@PathVariable Integer itemId) {
		return ApiResponse.success(itemService.unfavorite(authId, itemId));
	}

	@GetMapping("/{itemId}/comments")
	public ApiResponse<List<Map<String, Object>>> comments(@PathVariable Integer itemId) {
		return ApiResponse.success(itemService.comments(itemId));
	}

	@PostMapping("/{itemId}/comments")
	public ApiResponse<Map<String, Object>> createComment(@PathVariable Integer itemId,
			@RequestAttribute("authId") Long authId,
			@RequestBody Map<String, Object> body) {
		return ApiResponse.success(itemService.createComment(authId, itemId, body));
	}

	@PostMapping("/{itemId}/reports")
	public ApiResponse<Map<String, Object>> reportItem(@PathVariable Integer itemId,
			@RequestAttribute("authId") Long authId,
			@RequestBody Map<String, Object> body) {
		return ApiResponse.success(itemService.createReport(authId, itemId, body));
	}
}
