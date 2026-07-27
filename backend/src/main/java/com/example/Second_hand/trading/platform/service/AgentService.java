package com.example.Second_hand.trading.platform.service;

import java.io.IOException;
import java.net.URI;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import com.example.Second_hand.trading.platform.exception.AgentServiceException;
import com.example.Second_hand.trading.platform.exception.AgentServiceException.Reason;

@Service
public class AgentService {
	private static final Logger log = LoggerFactory.getLogger(AgentService.class);

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final String aiBaseUrl;
	private final Duration timeout;
	private final Semaphore requestSlots;
	private final long queueTimeoutMillis;

	public AgentService(
			ObjectMapper objectMapper,
			@Value("${app.ai.base-url:http://127.0.0.1:8001}") String aiBaseUrl,
			@Value("${app.ai.timeout-seconds:25}") long timeoutSeconds,
			@Value("${app.ai.max-concurrent-requests:8}") int maxConcurrentRequests,
			@Value("${app.ai.queue-timeout-ms:150}") long queueTimeoutMillis) {
		this.objectMapper = objectMapper;
		this.aiBaseUrl = trimTrailingSlash(aiBaseUrl);
		this.timeout = Duration.ofSeconds(Math.max(1, Math.min(timeoutSeconds, 120)));
		this.requestSlots = new Semaphore(Math.max(1, Math.min(maxConcurrentRequests, 64)));
		this.queueTimeoutMillis = Math.max(0, Math.min(queueTimeoutMillis, 5_000));
		this.httpClient = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(Duration.ofSeconds(5))
				.build();
	}

	public Map<String, Object> buyer(Map<String, Object> body) {
		Map<String, Object> normalized = normalizeRequest(body);
		validateRequest(normalized);
		return post("/agents/buyer", normalized);
	}

	public Map<String, Object> seller(Map<String, Object> body) {
		Map<String, Object> normalized = normalizeRequest(body);
		validateRequest(normalized);
		return post("/agents/seller", normalized);
	}

	private Map<String, Object> post(String path, Map<String, Object> body) {
		boolean acquired = false;
		try {
			acquired = requestSlots.tryAcquire(queueTimeoutMillis, TimeUnit.MILLISECONDS);
			if (!acquired) {
				log.warn("agent_proxy_overloaded path={} available_slots={}", path, requestSlots.availablePermits());
				throw new AgentServiceException(Reason.OVERLOADED, "Agent 服务繁忙，请稍后重试");
			}
			String requestBody = objectMapper.writeValueAsString(body);
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(aiBaseUrl + path))
					.version(HttpClient.Version.HTTP_1_1)
					.timeout(timeout)
					.header("Content-Type", "application/json; charset=utf-8")
					.POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
					.build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				log.warn("agent_proxy_upstream_status path={} status={}", path, response.statusCode());
				throw new AgentServiceException(Reason.UNAVAILABLE, "Agent 服务暂时不可用");
			}
			try {
				return objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
			} catch (RuntimeException exception) {
				log.warn("agent_proxy_invalid_response path={} error_type={}", path, exception.getClass().getSimpleName());
				throw new AgentServiceException(Reason.INVALID_RESPONSE, "Agent 服务返回异常，请稍后重试", exception);
			}
		} catch (HttpTimeoutException exception) {
			log.warn("agent_proxy_timeout path={} timeout_seconds={}", path, timeout.toSeconds());
			throw new AgentServiceException(Reason.TIMEOUT, "Agent 响应超时，请稍后重试", exception);
		} catch (ConnectException exception) {
			log.warn("agent_proxy_connection_failed path={}", path);
			throw new AgentServiceException(Reason.UNAVAILABLE, "Agent 服务暂不可用", exception);
		} catch (IOException exception) {
			log.warn("agent_proxy_io_failed path={} error_type={}", path, exception.getClass().getSimpleName());
			throw new AgentServiceException(Reason.UNAVAILABLE, "Agent 服务暂不可用", exception);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AgentServiceException(Reason.UNAVAILABLE, "Agent 请求被中断", exception);
		} finally {
			if (acquired) {
				requestSlots.release();
			}
		}
	}

	private String trimTrailingSlash(String value) {
		if (value == null || value.isBlank()) {
			return "http://127.0.0.1:8001";
		}
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}

	private Map<String, Object> normalizeRequest(Map<String, Object> body) {
		Map<String, Object> normalized = new LinkedHashMap<>();
		if (body != null) {
			normalized.putAll(body);
		}
		Object message = firstPresent(normalized, "message", "prompt", "input", "content", "query", "text", "userMessage");
		if (message != null && !String.valueOf(message).isBlank()) {
			normalized.put("message", String.valueOf(message));
		}
		return normalized;
	}

	private Object firstPresent(Map<String, Object> body, String... keys) {
		for (String key : keys) {
			Object value = body.get(key);
			if (value != null && !String.valueOf(value).isBlank()) {
				return value;
			}
		}
		return null;
	}

	private void validateRequest(Map<String, Object> body) {
		Object message = body.get("message");
		if (message == null || String.valueOf(message).isBlank()) {
			throw new IllegalArgumentException("请先输入 Agent 需求内容");
		}
	}

}
