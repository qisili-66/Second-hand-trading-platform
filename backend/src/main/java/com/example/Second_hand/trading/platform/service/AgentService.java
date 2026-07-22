package com.example.Second_hand.trading.platform.service;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class AgentService {
	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final String aiBaseUrl;
	private final Duration timeout;

	public AgentService(
			ObjectMapper objectMapper,
			@Value("${app.ai.base-url:http://127.0.0.1:8001}") String aiBaseUrl,
			@Value("${app.ai.timeout-seconds:25}") long timeoutSeconds) {
		this.objectMapper = objectMapper;
		this.aiBaseUrl = trimTrailingSlash(aiBaseUrl);
		this.timeout = Duration.ofSeconds(timeoutSeconds);
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
		try {
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
				throw new IllegalStateException("AI 服务响应异常：" + response.statusCode() + responseDetail(response.body()));
			}
			return objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
		} catch (IOException exception) {
			throw new IllegalStateException("AI 服务暂不可用，请确认 Python AI 服务已启动", exception);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("AI 服务请求被中断", exception);
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

	private String responseDetail(String responseBody) {
		if (responseBody == null || responseBody.isBlank()) {
			return "";
		}
		String compact = responseBody.replaceAll("\\s+", " ").trim();
		if (compact.length() > 300) {
			compact = compact.substring(0, 300) + "...";
		}
		return "，响应：" + compact;
	}
}
