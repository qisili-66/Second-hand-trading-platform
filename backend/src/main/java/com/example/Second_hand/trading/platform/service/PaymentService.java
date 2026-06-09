package com.example.Second_hand.trading.platform.service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.Second_hand.trading.platform.config.PaymentProperties;
import com.example.Second_hand.trading.platform.entity.OrderEntity;
import com.example.Second_hand.trading.platform.entity.OrderStatusLogEntity;
import com.example.Second_hand.trading.platform.entity.PaymentEntity;
import com.example.Second_hand.trading.platform.mapper.OrderMapper;
import com.example.Second_hand.trading.platform.mapper.OrderStatusLogMapper;
import com.example.Second_hand.trading.platform.mapper.PaymentMapper;

@Service
public class PaymentService {
	private final PaymentMapper paymentMapper;
	private final OrderMapper orderMapper;
	private final OrderStatusLogMapper orderStatusLogMapper;
	private final PaymentProperties properties;
	private final MessageService messageService;
	private final HttpClient httpClient = HttpClient.newHttpClient();

	public PaymentService(PaymentMapper paymentMapper, OrderMapper orderMapper,
			OrderStatusLogMapper orderStatusLogMapper, PaymentProperties properties, MessageService messageService) {
		this.paymentMapper = paymentMapper;
		this.orderMapper = orderMapper;
		this.orderStatusLogMapper = orderStatusLogMapper;
		this.properties = properties;
		this.messageService = messageService;
	}

	@Transactional
	public Map<String, Object> createPayment(OrderEntity order, String provider) {
		String normalizedProvider = normalizeProvider(provider);
		PaymentEntity payment = new PaymentEntity();
		payment.setPaymentNo("PAY" + System.currentTimeMillis() + randomDigits());
		payment.setOrderId(order.getId());
		payment.setOrderNo(order.getOrderNo());
		payment.setProvider(normalizedProvider);
		payment.setAmount(order.getAmount());
		payment.setStatus("INIT");
		paymentMapper.insert(payment);

		Map<String, Object> providerResult = switch (normalizedProvider) {
			case "ALIPAY" -> createAlipayPayment(order, payment);
			case "WECHAT" -> createWechatPayment(order, payment);
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的支付方式");
		};

		payment.setStatus("CREATED");
		payment.setPaymentUrl(stringValue(providerResult.get("paymentUrl")));
		payment.setQrUrl(stringValue(providerResult.get("qrUrl")));
		payment.setRequestPayload(stringValue(providerResult.get("requestPayload")));
		payment.setResponsePayload(stringValue(providerResult.get("responsePayload")));
		paymentMapper.updateById(payment);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("paymentId", payment.getId());
		result.put("paymentNo", payment.getPaymentNo());
		result.put("orderId", order.getId());
		result.put("orderNo", order.getOrderNo());
		result.put("provider", normalizedProvider);
		result.put("amount", order.getAmount());
		result.put("status", payment.getStatus());
		result.put("paymentUrl", payment.getPaymentUrl());
		result.put("qrUrl", payment.getQrUrl());
		return result;
	}

	public List<Map<String, Object>> payments(Long orderId) {
		return paymentMapper.selectList(Wrappers.lambdaQuery(PaymentEntity.class)
				.eq(PaymentEntity::getOrderId, orderId)
				.orderByDesc(PaymentEntity::getCreatedAt))
				.stream()
				.map(this::paymentRow)
				.toList();
	}

	public String handleAlipayNotify(Map<String, String> params) {
		String status = params.getOrDefault("trade_status", "");
		String paymentNo = params.getOrDefault("out_trade_no", "");
		if (List.of("TRADE_SUCCESS", "TRADE_FINISHED").contains(status) && StringUtils.hasText(paymentNo)) {
			markPaid(paymentNo, params.getOrDefault("trade_no", ""), params.toString());
			return "success";
		}
		return "failure";
	}

	public Map<String, String> handleWechatNotify(String body) {
		return Map.of("code", "FAIL", "message", "微信支付回调验签和解密未配置");
	}

	@Transactional
	public void markPaid(String paymentNo, String providerTradeNo, String responsePayload) {
		PaymentEntity payment = paymentMapper.selectOne(Wrappers.lambdaQuery(PaymentEntity.class)
				.eq(PaymentEntity::getPaymentNo, paymentNo)
				.last("LIMIT 1"));
		if (payment == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "支付单不存在");
		}
		payment.setStatus("PAID");
		payment.setProviderTradeNo(providerTradeNo);
		payment.setResponsePayload(responsePayload);
		payment.setPaidAt(LocalDateTime.now());
		paymentMapper.updateById(payment);

		OrderEntity order = orderMapper.selectById(payment.getOrderId());
		if (order != null && !"PAID".equals(order.getStatus()) && !"COMPLETED".equals(order.getStatus())) {
			String fromStatus = order.getStatus();
			order.setStatus("PAID");
			orderMapper.updateById(order);
			OrderStatusLogEntity log = new OrderStatusLogEntity();
			log.setOrderId(order.getId());
			log.setFromStatus(fromStatus);
			log.setToStatus("PAID");
			log.setOperatorId(order.getBuyerId());
			log.setOperatorType("PAYMENT");
			log.setRemark("支付回调确认");
			orderStatusLogMapper.insert(log);
			messageService.pushOrderNotification(order, "订单已支付", "订单 " + order.getOrderNo() + " 支付已确认。");
		}
	}

	private Map<String, Object> createAlipayPayment(OrderEntity order, PaymentEntity payment) {
		PaymentProperties.Alipay alipay = properties.getAlipay();
		if (!alipay.isEnabled() || !StringUtils.hasText(alipay.getAppId())
				|| !StringUtils.hasText(alipay.getPrivateKey())) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "支付宝支付未配置");
		}

		String notifyUrl = firstText(alipay.getNotifyUrl(), properties.getReturnUrl());
		String returnUrl = firstText(alipay.getReturnUrl(), properties.getReturnUrl());
		String bizContent = "{"
				+ "\"out_trade_no\":\"" + escapeJson(payment.getPaymentNo()) + "\","
				+ "\"total_amount\":\"" + order.getAmount().setScale(2) + "\","
				+ "\"subject\":\"校园二手订单 " + escapeJson(order.getOrderNo()) + "\","
				+ "\"product_code\":\"FAST_INSTANT_TRADE_PAY\""
				+ "}";

		Map<String, String> params = new LinkedHashMap<>();
		params.put("app_id", alipay.getAppId());
		params.put("method", "alipay.trade.page.pay");
		params.put("charset", "UTF-8");
		params.put("sign_type", "RSA2");
		params.put("timestamp", java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
				.format(java.time.LocalDateTime.now()));
		params.put("version", "1.0");
		params.put("notify_url", notifyUrl);
		params.put("return_url", returnUrl);
		params.put("biz_content", bizContent);

		String signContent = signContent(params);
		params.put("sign", sign(signContent, alipay.getPrivateKey()));
		String paymentUrl = alipay.getGateway() + "?" + encodeQuery(params);
		return Map.of(
				"paymentUrl", paymentUrl,
				"qrUrl", "",
				"requestPayload", signContent,
				"responsePayload", "");
	}

	private Map<String, Object> createWechatPayment(OrderEntity order, PaymentEntity payment) {
		PaymentProperties.Wechat wechat = properties.getWechat();
		if (!wechat.isEnabled() || !StringUtils.hasText(wechat.getAppId())
				|| !StringUtils.hasText(wechat.getMchId())
				|| !StringUtils.hasText(wechat.getMerchantSerialNo())
				|| !StringUtils.hasText(wechat.getPrivateKey())
				|| !StringUtils.hasText(wechat.getNotifyUrl())) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微信支付未配置");
		}

		String path = "/v3/pay/transactions/native";
		String body = "{"
				+ "\"appid\":\"" + escapeJson(wechat.getAppId()) + "\","
				+ "\"mchid\":\"" + escapeJson(wechat.getMchId()) + "\","
				+ "\"description\":\"校园二手订单 " + escapeJson(order.getOrderNo()) + "\","
				+ "\"out_trade_no\":\"" + escapeJson(payment.getPaymentNo()) + "\","
				+ "\"notify_url\":\"" + escapeJson(wechat.getNotifyUrl()) + "\","
				+ "\"amount\":{\"total\":" + amountFen(order.getAmount()) + ",\"currency\":\"CNY\"}"
				+ "}";

		try {
			String authorization = wechatAuthorization("POST", path, body, wechat);
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(wechat.getGateway() + path))
					.header("Accept", "application/json")
					.header("Content-Type", "application/json")
					.header("Authorization", authorization)
					.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
					.build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "微信支付下单失败");
			}
			String codeUrl = extractJsonString(response.body(), "code_url");
			return Map.of(
					"paymentUrl", "",
					"qrUrl", codeUrl,
					"requestPayload", body,
					"responsePayload", response.body());
		} catch (ResponseStatusException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "微信支付请求失败");
		}
	}

	private String wechatAuthorization(String method, String path, String body, PaymentProperties.Wechat wechat) {
		long timestamp = Instant.now().getEpochSecond();
		String nonce = UUID.randomUUID().toString().replace("-", "");
		String message = method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + body + "\n";
		String signature = sign(message, wechat.getPrivateKey());
		return "WECHATPAY2-SHA256-RSA2048 "
				+ "mchid=\"" + wechat.getMchId() + "\","
				+ "nonce_str=\"" + nonce + "\","
				+ "timestamp=\"" + timestamp + "\","
				+ "serial_no=\"" + wechat.getMerchantSerialNo() + "\","
				+ "signature=\"" + signature + "\"";
	}

	private String sign(String content, String privateKeyText) {
		try {
			Signature signature = Signature.getInstance("SHA256withRSA");
			signature.initSign(privateKey(privateKeyText));
			signature.update(content.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(signature.sign());
		} catch (Exception ex) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "支付签名失败");
		}
	}

	private PrivateKey privateKey(String privateKeyText) throws Exception {
		String normalized = privateKeyText
				.replace("-----BEGIN PRIVATE KEY-----", "")
				.replace("-----END PRIVATE KEY-----", "")
				.replace("\\n", "")
				.replace("\r", "")
				.replace("\n", "")
				.replace(" ", "");
		byte[] keyBytes = Base64.getDecoder().decode(normalized);
		return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
	}

	private String signContent(Map<String, String> params) {
		return params.entrySet().stream()
				.filter(entry -> StringUtils.hasText(entry.getValue()))
				.sorted(Map.Entry.comparingByKey())
				.map(entry -> entry.getKey() + "=" + entry.getValue())
				.reduce((left, right) -> left + "&" + right)
				.orElse("");
	}

	private String encodeQuery(Map<String, String> params) {
		return params.entrySet().stream()
				.filter(entry -> StringUtils.hasText(entry.getValue()))
				.map(entry -> url(entry.getKey()) + "=" + url(entry.getValue()))
				.reduce((left, right) -> left + "&" + right)
				.orElse("");
	}

	private String url(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private int amountFen(BigDecimal amount) {
		return amount.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).intValueExact();
	}

	private String extractJsonString(String json, String field) {
		Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
		if (!matcher.find()) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "支付响应缺少字段: " + field);
		}
		return matcher.group(1);
	}

	private String normalizeProvider(String provider) {
		String value = provider == null ? "" : provider.trim().toUpperCase();
		if ("ALIPAY".equals(value) || "WECHAT".equals(value)) {
			return value;
		}
		throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "支付方式只能是 ALIPAY 或 WECHAT");
	}

	private Map<String, Object> paymentRow(PaymentEntity payment) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("paymentId", payment.getId());
		row.put("paymentNo", payment.getPaymentNo());
		row.put("orderId", payment.getOrderId());
		row.put("orderNo", payment.getOrderNo());
		row.put("provider", payment.getProvider());
		row.put("amount", payment.getAmount());
		row.put("status", payment.getStatus());
		row.put("providerTradeNo", payment.getProviderTradeNo());
		row.put("paymentUrl", payment.getPaymentUrl());
		row.put("qrUrl", payment.getQrUrl());
		row.put("paidAt", payment.getPaidAt() == null ? "" : payment.getPaidAt().toString());
		row.put("createdAt", payment.getCreatedAt() == null ? "" : payment.getCreatedAt().toString());
		return row;
	}

	private String escapeJson(String value) {
		return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private String firstText(String first, String second) {
		return StringUtils.hasText(first) ? first : (StringUtils.hasText(second) ? second : "");
	}

	private String stringValue(Object value) {
		return value == null ? "" : String.valueOf(value);
	}

	private String randomDigits() {
		return String.valueOf((int) (Math.random() * 9000) + 1000);
	}
}
