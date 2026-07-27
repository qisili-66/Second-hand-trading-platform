package com.example.Second_hand.trading.platform.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JwtService {
	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
	private static final String HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

	private final String secret;
	private final long expireSeconds;

	public JwtService(@Value("${app.jwt.secret}") String secret,
			@Value("${app.jwt.expire-minutes:120}") long expireMinutes) {
		if (secret == null || secret.trim().length() < 32) {
			throw new IllegalStateException("JWT_SECRET 必须配置为至少 32 位的随机字符串");
		}
		this.secret = secret;
		this.expireSeconds = expireMinutes * 60;
	}

	public String createUserToken(Object userId, String account) {
		return createToken("USER", userId, account, "USER");
	}

	public String createAdminToken(Object adminId, String account, String role) {
		return createToken("ADMIN", adminId, account, role);
	}

	public Optional<JwtClaims> parseAuthorization(String authorization) {
		if (authorization == null || authorization.isBlank()) {
			return Optional.empty();
		}

		String token = authorization.replaceFirst("(?i)^Bearer\\s+", "").trim();
		if (token.isBlank()) {
			return Optional.empty();
		}

		return parseToken(token);
	}

	public JwtClaims requireAuthorization(String authorization) {
		return parseAuthorization(authorization)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已过期，请重新登录"));
	}

	private String createToken(String type, Object id, String account, String role) {
		long now = Instant.now().getEpochSecond();
		long exp = now + expireSeconds;
		String payload = "{"
				+ "\"type\":\"" + escapeJson(type) + "\","
				+ "\"id\":\"" + escapeJson(String.valueOf(id)) + "\","
				+ "\"account\":\"" + escapeJson(account == null ? "" : account) + "\","
				+ "\"role\":\"" + escapeJson(role == null ? "" : role) + "\","
				+ "\"iat\":" + now + ","
				+ "\"exp\":" + exp
				+ "}";

		String headerPart = encode(HEADER);
		String payloadPart = encode(payload);
		String signingInput = headerPart + "." + payloadPart;
		return signingInput + "." + sign(signingInput);
	}

	private Optional<JwtClaims> parseToken(String token) {
		String[] parts = token.split("\\.");
		if (parts.length != 3) {
			return Optional.empty();
		}

		String signingInput = parts[0] + "." + parts[1];
		if (!constantTimeEquals(sign(signingInput), parts[2])) {
			return Optional.empty();
		}

		try {
			String payload = new String(URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8);
			long exp = Long.parseLong(extractNumber(payload, "exp"));
			if (exp < Instant.now().getEpochSecond()) {
				return Optional.empty();
			}

			return Optional.of(new JwtClaims(
					extractString(payload, "type"),
					Long.valueOf(extractString(payload, "id")),
					extractString(payload, "account"),
					extractString(payload, "role"),
					exp));
		} catch (Exception ex) {
			return Optional.empty();
		}
	}

	private String encode(String value) {
		return URL_ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	private String sign(String input) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
			return URL_ENCODER.encodeToString(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to sign JWT", ex);
		}
	}

	private boolean constantTimeEquals(String expected, String actual) {
		return MessageDigest.isEqual(
				expected.getBytes(StandardCharsets.UTF_8),
				actual.getBytes(StandardCharsets.UTF_8));
	}

	private String extractString(String json, String field) {
		Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"").matcher(json);
		if (!matcher.find()) {
			throw new IllegalArgumentException("Missing JWT claim: " + field);
		}
		return unescapeJson(matcher.group(1));
	}

	private String extractNumber(String json, String field) {
		Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(\\d+)").matcher(json);
		if (!matcher.find()) {
			throw new IllegalArgumentException("Missing JWT claim: " + field);
		}
		return matcher.group(1);
	}

	private String escapeJson(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private String unescapeJson(String value) {
		return value.replace("\\\"", "\"").replace("\\\\", "\\");
	}

	public record JwtClaims(String type, Long id, String account, String role, long expiresAt) {
		public boolean isUser() {
			return "USER".equals(type);
		}

		public boolean isAdmin() {
			return "ADMIN".equals(type);
		}
	}
}
