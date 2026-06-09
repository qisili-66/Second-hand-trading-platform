package com.example.Second_hand.trading.platform.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.Second_hand.trading.platform.dto.ItemSearchCriteria;
import com.example.Second_hand.trading.platform.dto.PageResponse;
import com.example.Second_hand.trading.platform.entity.FavoriteEntity;
import com.example.Second_hand.trading.platform.entity.FileEntity;
import com.example.Second_hand.trading.platform.entity.ItemCommentEntity;
import com.example.Second_hand.trading.platform.entity.ItemEntity;
import com.example.Second_hand.trading.platform.entity.ItemImageEntity;
import com.example.Second_hand.trading.platform.mapper.FavoriteMapper;
import com.example.Second_hand.trading.platform.mapper.FileMapper;
import com.example.Second_hand.trading.platform.mapper.ItemCommentMapper;
import com.example.Second_hand.trading.platform.mapper.ItemImageMapper;
import com.example.Second_hand.trading.platform.mapper.ItemMapper;

@Service
public class ItemService {
	private static final int MAX_PAGE_SIZE = 100;

	private final JdbcTemplate jdbcTemplate;
	private final ItemMapper itemMapper;
	private final ItemImageMapper itemImageMapper;
	private final FileMapper fileMapper;
	private final FavoriteMapper favoriteMapper;
	private final ItemCommentMapper itemCommentMapper;
	private final MessageService messageService;

	public ItemService(JdbcTemplate jdbcTemplate, ItemMapper itemMapper, ItemImageMapper itemImageMapper,
			FileMapper fileMapper, FavoriteMapper favoriteMapper, ItemCommentMapper itemCommentMapper,
			MessageService messageService) {
		this.jdbcTemplate = jdbcTemplate;
		this.itemMapper = itemMapper;
		this.itemImageMapper = itemImageMapper;
		this.fileMapper = fileMapper;
		this.favoriteMapper = favoriteMapper;
		this.itemCommentMapper = itemCommentMapper;
		this.messageService = messageService;
	}

	public List<Map<String, Object>> categories() {
		return jdbcTemplate.queryForList(
				"SELECT id AS categoryId, name FROM categories WHERE enabled = 1 ORDER BY sort_order, id");
	}

	public List<Map<String, Object>> items() {
		LambdaQueryWrapper<ItemEntity> wrapper = Wrappers.lambdaQuery(ItemEntity.class)
				.eq(ItemEntity::getDeleted, 0)
				.orderByDesc(ItemEntity::getCreatedAt);
		return itemMapper.selectList(wrapper).stream().map(this::itemRow).toList();
	}

	public PageResponse<Map<String, Object>> items(ItemSearchCriteria criteria) {
		int safePage = Math.max(1, criteria.page());
		int safePageSize = Math.max(1, Math.min(criteria.pageSize(), MAX_PAGE_SIZE));
		Page<ItemEntity> page = itemMapper.selectPage(Page.of(safePage, safePageSize), itemWrapper(criteria, true));
		List<Map<String, Object>> rows = page.getRecords().stream().map(this::itemRow).toList();
		return PageResponse.of(rows, (int) page.getCurrent(), (int) page.getSize(), page.getTotal());
	}

	public Map<String, Object> itemDetail(Integer itemId) {
		ItemEntity item = itemMapper.selectOne(Wrappers.lambdaQuery(ItemEntity.class)
				.eq(ItemEntity::getId, itemId)
				.eq(ItemEntity::getDeleted, 0)
				.eq(ItemEntity::getStatus, "ON_SALE")
				.last("LIMIT 1"));

		if (item == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
		}

		return itemRow(item);
	}

	@Transactional
	public Map<String, Object> createItem(Long sellerId, Map<String, Object> body) {
		if (sellerId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
		}

		ItemEntity item = new ItemEntity();
		item.setSellerId(sellerId);
		item.setCategoryId(resolveCategoryId(body));
		item.setTitle(requiredText(body, "title", "商品标题"));
		item.setDescription(requiredText(body, "description", "商品描述", "desc"));
		item.setPrice(requiredMoney(body, "price", "售价"));
		item.setOriginalPrice(optionalMoney(body, "originalPrice"));
		item.setConditionLevel(conditionCode(requiredText(body, "condition", "成色")));
		item.setCampus(requiredText(body, "campus", "校区"));
		item.setDormitory(optionalText(body, "dormitory", "dorm"));
		item.setTradePlace(optionalText(body, "tradePlace"));
		item.setTradeModes(tradeModes(body.get("tradeModes")));
		item.setStatus(statusCode(optionalText(body, "status")));
		item.setSwapSupported(boolValue(body.get("swapSupported")) ? 1 : 0);
		item.setViewCount(0);
		item.setFavoriteCount(0);
		item.setDeleted(0);
		itemMapper.insert(item);

		List<String> imageUrls = imageUrls(body);
		int sortOrder = 0;
		for (String url : imageUrls) {
			ItemImageEntity image = new ItemImageEntity();
			image.setItemId(item.getId());
			image.setImageUrl(url);
			image.setSortOrder(sortOrder);
			itemImageMapper.insert(image);

			FileEntity file = new FileEntity();
			file.setOwnerId(sellerId);
			file.setFileType("IMAGE");
			file.setOriginalName("item-" + item.getId() + "-" + sortOrder + ".jpg");
			file.setStorageKey(url);
			file.setUrl(url);
			file.setSizeBytes(0L);
			file.setContentType("image/jpeg");
			fileMapper.insert(file);
			sortOrder++;
		}

		return itemRow(item);
	}

	@Transactional
	public Map<String, Object> adminCreateItem(Map<String, Object> body) {
		Long sellerId = optionalLong(body.get("sellerId"));
		if (sellerId == null) {
			sellerId = optionalLong(body.get("userId"));
		}
		if (sellerId == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择商品卖家");
		}
		ensureActiveUser(sellerId);
		return createItem(sellerId, body);
	}

	@Transactional
	public boolean offShelfItem(Long sellerId, Integer itemId) {
		ItemEntity item = requireOwnedItem(sellerId, itemId);
		if ("SOLD".equals(item.getStatus())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已售出商品不能下架");
		}
		item.setStatus("REMOVED");
		itemMapper.updateById(item);
		return true;
	}

	@Transactional
	public boolean onShelfItem(Long sellerId, Integer itemId) {
		ItemEntity item = requireOwnedItem(sellerId, itemId);
		if ("SOLD".equals(item.getStatus())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已售出商品不能重新上架");
		}
		item.setStatus("ON_SALE");
		itemMapper.updateById(item);
		return true;
	}

	@Transactional
	public boolean softDeleteItem(Long sellerId, Integer itemId) {
		ItemEntity item = requireOwnedItem(sellerId, itemId);
		ensureNoActiveOrder(item.getId());
		item.setStatus("REMOVED");
		item.setDeleted(1);
		itemMapper.updateById(item);
		return true;
	}

	@Transactional
	public boolean adminOffShelfItem(Integer itemId) {
		ItemEntity item = requireAnyItem(itemId);
		if ("SOLD".equals(item.getStatus())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已售出商品不能下架");
		}
		item.setStatus("REMOVED");
		itemMapper.updateById(item);
		notifySeller(item, "商品已被管理员下架",
				"你的商品「" + item.getTitle() + "」已被管理员下架。如需重新发布，请根据平台规则修改后联系管理员。");
		return true;
	}

	@Transactional
	public boolean adminOnShelfItem(Integer itemId) {
		ItemEntity item = requireAnyItem(itemId);
		if ("SOLD".equals(item.getStatus())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已售出商品不能重新上架");
		}
		item.setStatus("ON_SALE");
		itemMapper.updateById(item);
		notifySeller(item, "商品已被管理员重新上架",
				"你的商品「" + item.getTitle() + "」已由管理员重新上架。");
		return true;
	}

	@Transactional
	public boolean adminSoftDeleteItem(Integer itemId) {
		ItemEntity item = requireAnyItem(itemId);
		ensureNoActiveOrder(item.getId());
		item.setStatus("REMOVED");
		item.setDeleted(1);
		itemMapper.updateById(item);
		notifySeller(item, "商品已被管理员删除",
				"你的商品「" + item.getTitle() + "」已被管理员删除，不再对外展示。");
		return true;
	}

	public List<Map<String, Object>> itemsBySeller(Long sellerId) {
		if (sellerId == null) {
			return List.of();
		}

		return itemMapper.selectList(Wrappers.lambdaQuery(ItemEntity.class)
				.eq(ItemEntity::getSellerId, sellerId)
				.eq(ItemEntity::getDeleted, 0)
				.orderByDesc(ItemEntity::getCreatedAt))
				.stream()
				.map(this::itemRow)
				.toList();
	}

	public List<Map<String, Object>> favoriteItems(Long userId) {
		if (userId == null) {
			return List.of();
		}

		List<FavoriteEntity> favorites = favoriteMapper.selectList(Wrappers.lambdaQuery(FavoriteEntity.class)
				.eq(FavoriteEntity::getUserId, userId)
				.orderByDesc(FavoriteEntity::getCreatedAt));
		if (favorites.isEmpty()) {
			return List.of();
		}

		List<Long> itemIds = favorites.stream().map(FavoriteEntity::getItemId).toList();
		Map<Long, ItemEntity> itemsById = itemMapper.selectList(Wrappers.lambdaQuery(ItemEntity.class)
				.in(ItemEntity::getId, itemIds)
				.eq(ItemEntity::getDeleted, 0))
				.stream()
				.collect(Collectors.toMap(ItemEntity::getId, item -> item));

		return favorites.stream()
				.map(favorite -> itemsById.get(favorite.getItemId()))
				.filter(item -> item != null)
				.map(this::itemRow)
				.toList();
	}

	@Transactional
	public boolean favorite(Long userId, Integer itemId) {
		if (userId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
		}
		requireItem(itemId);

		Long count = favoriteMapper.selectCount(Wrappers.lambdaQuery(FavoriteEntity.class)
				.eq(FavoriteEntity::getUserId, userId)
				.eq(FavoriteEntity::getItemId, itemId.longValue()));
		if (count == null || count == 0) {
			FavoriteEntity favorite = new FavoriteEntity();
			favorite.setUserId(userId);
			favorite.setItemId(itemId.longValue());
			favoriteMapper.insert(favorite);
			jdbcTemplate.update("UPDATE items SET favorite_count = favorite_count + 1 WHERE id = ?", itemId);
		}
		return true;
	}

	@Transactional
	public boolean unfavorite(Long userId, Integer itemId) {
		if (userId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
		}

		int deleted = favoriteMapper.delete(Wrappers.lambdaQuery(FavoriteEntity.class)
				.eq(FavoriteEntity::getUserId, userId)
				.eq(FavoriteEntity::getItemId, itemId.longValue()));
		if (deleted > 0) {
			jdbcTemplate.update("UPDATE items SET favorite_count = GREATEST(favorite_count - 1, 0) WHERE id = ?", itemId);
		}
		return true;
	}

	public List<Map<String, Object>> comments(Integer itemId) {
		requireItem(itemId);
		return itemCommentMapper.selectList(Wrappers.lambdaQuery(ItemCommentEntity.class)
				.eq(ItemCommentEntity::getItemId, itemId.longValue())
				.eq(ItemCommentEntity::getDeleted, 0)
				.orderByAsc(ItemCommentEntity::getCreatedAt))
				.stream()
				.map(this::commentRow)
				.toList();
	}

	@Transactional
	public Map<String, Object> createComment(Long userId, Integer itemId, Map<String, Object> body) {
		if (userId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
		}
		ItemEntity item = requireAnyItem(itemId);
		String content = requiredText(body, "content", "留言内容");
		if (content.length() > 500) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "留言不能超过 500 字");
		}

		ItemCommentEntity comment = new ItemCommentEntity();
		comment.setItemId(itemId.longValue());
		comment.setUserId(userId);
		comment.setParentId(optionalLong(body.get("parentId")));
		comment.setContent(content);
		comment.setDeleted(0);
		itemCommentMapper.insert(comment);
		if (!userId.equals(item.getSellerId())) {
			notifySeller(item, "COMMENT", "收到新的商品留言",
					displayName(userId) + " 给你的商品「" + item.getTitle() + "」留言：" + content);
		}
		return commentRow(comment);
	}

	private LambdaQueryWrapper<ItemEntity> itemWrapper(ItemSearchCriteria criteria, boolean publicOnly) {
		LambdaQueryWrapper<ItemEntity> wrapper = Wrappers.lambdaQuery(ItemEntity.class)
				.eq(ItemEntity::getDeleted, 0);
		if (publicOnly) {
			wrapper.eq(ItemEntity::getStatus, "ON_SALE");
		}

		if (StringUtils.hasText(criteria.keyword())) {
			String keyword = criteria.keyword().trim();
			wrapper.and(query -> query.like(ItemEntity::getTitle, keyword)
					.or()
					.like(ItemEntity::getDescription, keyword));
		}

		List<Long> categoryIds = resolveCategoryIds(criteria.categoryId(), criteria.categories());
		if (!categoryIds.isEmpty()) {
			wrapper.in(ItemEntity::getCategoryId, categoryIds);
		}

		List<String> conditionCodes = splitValues(criteria.conditions()).stream().map(this::conditionCode).distinct().toList();
		if (!conditionCodes.isEmpty()) {
			wrapper.in(ItemEntity::getConditionLevel, conditionCodes);
		}

		if (StringUtils.hasText(criteria.campus())) {
			wrapper.eq(ItemEntity::getCampus, criteria.campus().trim());
		}
		if (criteria.minPrice() != null) {
			wrapper.ge(ItemEntity::getPrice, criteria.minPrice());
		}
		if (criteria.maxPrice() != null) {
			wrapper.le(ItemEntity::getPrice, criteria.maxPrice());
		}

		String sort = criteria.sort() == null ? "" : criteria.sort().toLowerCase(Locale.ROOT);
		if ("price_asc".equals(sort)) {
			wrapper.orderByAsc(ItemEntity::getPrice).orderByDesc(ItemEntity::getCreatedAt);
		} else if ("price_desc".equals(sort)) {
			wrapper.orderByDesc(ItemEntity::getPrice).orderByDesc(ItemEntity::getCreatedAt);
		} else {
			wrapper.orderByDesc(ItemEntity::getCreatedAt);
		}
		return wrapper;
	}

	private Map<String, Object> itemRow(ItemEntity item) {
		List<String> imageUrls = jdbcTemplate.queryForList("""
				SELECT image_url
				FROM item_images
				WHERE item_id = ?
				ORDER BY sort_order, id
				""", String.class, item.getId());
		String coverUrl = imageUrls.isEmpty() ? "" : imageUrls.get(0);

		Map<String, Object> row = new LinkedHashMap<>();
		row.put("itemId", item.getId());
		row.put("title", item.getTitle());
		row.put("description", item.getDescription());
		row.put("category", categoryRow(item.getCategoryId()));
		row.put("price", item.getPrice());
		row.put("originalPrice", item.getOriginalPrice());
		row.put("condition", item.getConditionLevel());
		row.put("itemStatus", item.getStatus());
		row.put("campus", item.getCampus());
		row.put("dormitory", item.getDormitory());
		row.put("tradePlace", item.getTradePlace());
		row.put("tradeModes", item.getTradeModes());
		row.put("swapSupported", Integer.valueOf(1).equals(item.getSwapSupported()));
		row.put("coverUrl", coverUrl);
		row.put("imageUrls", imageUrls);
		row.put("seller", sellerRow(item.getSellerId(), item.getCampus()));
		row.put("favoriteCount", item.getFavoriteCount() == null ? 0 : item.getFavoriteCount());
		row.put("viewCount", item.getViewCount() == null ? 0 : item.getViewCount());
		row.put("createdAt", timeString(item.getCreatedAt()));
		row.put("updatedAt", timeString(item.getUpdatedAt()));
		return row;
	}

	private Map<String, Object> categoryRow(Long categoryId) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("categoryId", categoryId);
		row.put("name", categoryName(categoryId));
		return row;
	}

	private Map<String, Object> sellerRow(Long sellerId, String fallbackCampus) {
		List<Map<String, Object>> users = jdbcTemplate.queryForList("""
				SELECT id AS userId, nickname, avatar_url AS avatarUrl, campus
				FROM users
				WHERE id = ? AND deleted = 0
				LIMIT 1
				""", sellerId);
		if (!users.isEmpty()) {
			Map<String, Object> user = users.get(0);
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("userId", user.get("userId"));
			row.put("nickname", user.get("nickname"));
			row.put("avatarUrl", user.get("avatarUrl") == null ? "" : user.get("avatarUrl"));
			row.put("campus", user.get("campus") == null ? fallbackCampus : user.get("campus"));
			return row;
		}

		Map<String, Object> row = new LinkedHashMap<>();
		row.put("userId", sellerId);
		row.put("nickname", "用户" + sellerId);
		row.put("avatarUrl", "");
		row.put("campus", fallbackCampus);
		return row;
	}

	private Map<String, Object> commentRow(ItemCommentEntity comment) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("commentId", comment.getId());
		row.put("itemId", comment.getItemId());
		row.put("userId", comment.getUserId());
		row.put("parentId", comment.getParentId());
		row.put("content", comment.getContent());
		row.put("user", sellerRow(comment.getUserId(), ""));
		row.put("createdAt", timeString(comment.getCreatedAt()));
		return row;
	}

	private String categoryName(Long categoryId) {
		List<String> names = jdbcTemplate.queryForList("SELECT name FROM categories WHERE id = ? LIMIT 1",
				String.class, categoryId);
		return names.isEmpty() ? "" : names.get(0);
	}

	private List<Long> resolveCategoryIds(Long categoryId, String categories) {
		List<Long> ids = new ArrayList<>();
		if (categoryId != null) {
			ids.add(categoryId);
		}

		List<String> names = splitValues(categories);
		if (!names.isEmpty()) {
			String placeholders = names.stream().map(name -> "?").collect(Collectors.joining(","));
			ids.addAll(jdbcTemplate.queryForList(
					"SELECT id FROM categories WHERE name IN (" + placeholders + ")",
					Long.class,
					names.toArray()));
			if (ids.isEmpty()) {
				ids.add(-1L);
			}
		}
		return ids.stream().distinct().toList();
	}

	private Long resolveCategoryId(Map<String, Object> body) {
		Long categoryId = optionalLong(body.get("categoryId"));
		if (categoryId != null) {
			return categoryId;
		}

		String category = requiredText(body, "category", "商品分类");
		List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM categories WHERE name = ? LIMIT 1", Long.class, category);
		if (ids.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品分类不存在");
		}
		return ids.get(0);
	}

	private void requireItem(Integer itemId) {
		Long count = itemMapper.selectCount(Wrappers.lambdaQuery(ItemEntity.class)
				.eq(ItemEntity::getId, itemId)
				.eq(ItemEntity::getDeleted, 0));
		if (count == null || count == 0) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
		}
	}

	private ItemEntity requireOwnedItem(Long sellerId, Integer itemId) {
		if (sellerId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
		}
		ItemEntity item = requireAnyItem(itemId);
		if (!sellerId.equals(item.getSellerId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能操作自己发布的商品");
		}
		return item;
	}

	private ItemEntity requireAnyItem(Integer itemId) {
		ItemEntity item = itemMapper.selectOne(Wrappers.lambdaQuery(ItemEntity.class)
				.eq(ItemEntity::getId, itemId)
				.eq(ItemEntity::getDeleted, 0)
				.last("LIMIT 1"));
		if (item == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
		}
		return item;
	}

	private void ensureActiveUser(Long userId) {
		Long count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM users
				WHERE id = ? AND deleted = 0 AND status = 'NORMAL'
				""", Long.class, userId);
		if (count == null || count == 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "卖家账号不存在或已禁用");
		}
	}

	private void ensureNoActiveOrder(Long itemId) {
		Long count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM orders
				WHERE item_id = ?
				  AND status IN ('PENDING', 'ACCEPTED', 'PAYING', 'PAID')
				""", Long.class, itemId);
		if (count != null && count > 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品存在进行中的订单，不能删除");
		}
	}

	private void notifySeller(ItemEntity item, String title, String content) {
		notifySeller(item, "SYSTEM", title, content);
	}

	private void notifySeller(ItemEntity item, String type, String title, String content) {
		if (item == null || item.getSellerId() == null) {
			return;
		}
		messageService.createNotification(item.getSellerId(), type, title, content);
	}

	private String displayName(Long userId) {
		List<String> names = jdbcTemplate.queryForList("""
				SELECT nickname
				FROM users
				WHERE id = ? AND deleted = 0
				LIMIT 1
				""", String.class, userId);
		return names.isEmpty() || !StringUtils.hasText(names.get(0)) ? "用户" + userId : names.get(0);
	}

	private List<String> imageUrls(Map<String, Object> body) {
		List<String> urls = new ArrayList<>();
		collectUrls(urls, body.get("imageUrls"));
		collectUrls(urls, body.get("images"));
		collectUrls(urls, body.get("coverUrl"));
		collectUrls(urls, body.get("imageUrl"));
		return urls.stream().filter(StringUtils::hasText).distinct().toList();
	}

	private void collectUrls(List<String> urls, Object value) {
		if (value instanceof List<?> list) {
			list.forEach(item -> collectUrls(urls, item));
			return;
		}
		if (value instanceof Map<?, ?> map) {
			Object url = map.get("url");
			if (url != null) {
				collectUrls(urls, url);
			}
			return;
		}
		if (value != null && StringUtils.hasText(String.valueOf(value))) {
			urls.add(String.valueOf(value).trim());
		}
	}

	private String requiredText(Map<String, Object> body, String key, String label, String... aliases) {
		String value = optionalText(body, key);
		if (!StringUtils.hasText(value)) {
			for (String alias : aliases) {
				value = optionalText(body, alias);
				if (StringUtils.hasText(value)) {
					break;
				}
			}
		}
		if (!StringUtils.hasText(value)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写" + label);
		}
		return value.trim();
	}

	private String optionalText(Map<String, Object> body, String... keys) {
		for (String key : keys) {
			Object value = body.get(key);
			if (value instanceof List<?> list) {
				String joined = list.stream()
						.filter(item -> item != null && StringUtils.hasText(String.valueOf(item)))
						.map(item -> String.valueOf(item).trim())
						.collect(Collectors.joining("/"));
				if (StringUtils.hasText(joined)) {
					return joined;
				}
			} else if (value != null && StringUtils.hasText(String.valueOf(value))) {
				return String.valueOf(value).trim();
			}
		}
		return "";
	}

	private BigDecimal requiredMoney(Map<String, Object> body, String key, String label) {
		BigDecimal value = optionalMoney(body, key);
		if (value == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写" + label);
		}
		return value;
	}

	private BigDecimal optionalMoney(Map<String, Object> body, String key) {
		Object value = body.get(key);
		if (value == null || !StringUtils.hasText(String.valueOf(value))) {
			return null;
		}
		try {
			return new BigDecimal(String.valueOf(value).trim());
		} catch (NumberFormatException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + "格式错误");
		}
	}

	private Long optionalLong(Object value) {
		if (value == null || !StringUtils.hasText(String.valueOf(value))) {
			return null;
		}
		if (value instanceof Number number) {
			return number.longValue();
		}
		return Long.valueOf(String.valueOf(value).trim());
	}

	private List<String> splitValues(String value) {
		if (!StringUtils.hasText(value)) {
			return List.of();
		}
		return List.of(value.split(",")).stream()
				.map(String::trim)
				.filter(StringUtils::hasText)
				.toList();
	}

	private String conditionCode(String value) {
		if (!StringUtils.hasText(value)) {
			return "";
		}
		String upper = value.trim().toUpperCase(Locale.ROOT);
		if (List.of("NEW", "LIKE_NEW", "GOOD", "FAIR").contains(upper)) {
			return upper;
		}
		if (value.contains("全")) {
			return "NEW";
		}
		if (value.contains("9")) {
			return "LIKE_NEW";
		}
		if (value.contains("8")) {
			return "GOOD";
		}
		return "FAIR";
	}

	private String tradeModes(Object value) {
		String text = value instanceof List<?> list
				? list.stream().map(String::valueOf).collect(Collectors.joining(","))
				: String.valueOf(value == null ? "" : value);
		String upper = text.toUpperCase(Locale.ROOT);
		if (upper.contains("ESCROW") || text.contains("线上")) {
			return "OFFLINE,ESCROW";
		}
		return "OFFLINE";
	}

	private String statusCode(String value) {
		if (!StringUtils.hasText(value)) {
			return "ON_SALE";
		}
		String upper = value.toUpperCase(Locale.ROOT);
		return upper.contains("DRAFT") || value.contains("草") ? "DRAFT" : "ON_SALE";
	}

	private boolean boolValue(Object value) {
		return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value))
				|| "1".equals(String.valueOf(value));
	}

	private String timeString(LocalDateTime time) {
		return time == null ? "" : time.toString();
	}
}
