package com.example.Second_hand.trading.platform.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
	private final JdbcTemplate jdbcTemplate;

	public AuthService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Map<String, Object> registerUser(Map<String, Object> body) {
		String studentNo = required(body, "studentNo", "学号");
		String password = required(body, "password", "密码");
		String realName = optional(body, "realName");
		String nickname = optional(body, "nickname");
		String department = optional(body, "department");
		String enrollmentYear = optional(body, "enrollmentYear");
		String email = optional(body, "email");

		if (password.length() < 8) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码至少 8 位");
		}

		Long duplicated = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM users
				WHERE deleted = 0 AND (student_no = ? OR (? <> '' AND email = ?))
				""", Long.class, studentNo, email, email);
		if (duplicated != null && duplicated > 0) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "学号或邮箱已注册");
		}

		String displayName = !nickname.isBlank() ? nickname : (!realName.isBlank() ? realName : studentNo);
		Integer year = enrollmentYear.isBlank() ? null : Integer.valueOf(enrollmentYear);

		jdbcTemplate.update("""
				INSERT INTO users (
				  student_no, password_hash, nickname, real_name, department,
				  enrollment_year, email, verified_status
				)
				VALUES (?, ?, ?, ?, ?, ?, NULLIF(?, ''), 'VERIFIED')
				""", studentNo, hashPassword(password), displayName, realName, department, year, email);

		Map<String, Object> user = userByStudentNo(studentNo);
		Long userId = toLong(user.get("userId"));
		jdbcTemplate.update("""
				INSERT INTO user_privacy (user_id, phone_visible, wechat_visible)
				VALUES (?, ?, ?)
				ON DUPLICATE KEY UPDATE phone_visible = VALUES(phone_visible), wechat_visible = VALUES(wechat_visible)
				""", userId, boolValue(body.get("phoneVisible")) ? 1 : 0, boolValue(body.get("wechatVisible")) ? 1 : 0);

		return user;
	}

	public Map<String, Object> loginUser(Map<String, Object> body) {
		String account = required(body, "account", "账号");
		String password = required(body, "password", "密码");
		List<Map<String, Object>> users = jdbcTemplate.queryForList("""
				SELECT id AS userId, password_hash AS passwordHash
				FROM users
				WHERE deleted = 0 AND status = 'NORMAL'
				  AND (student_no = ? OR email = ? OR nickname = ? OR real_name = ?)
				ORDER BY id
				LIMIT 1
				""", account, account, account, account);

		if (users.isEmpty() || !passwordMatches(password, String.valueOf(users.get(0).get("passwordHash")))) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
		}

		Long userId = toLong(users.get(0).get("userId"));
		jdbcTemplate.update("UPDATE users SET last_login_at = CURRENT_TIMESTAMP WHERE id = ?", userId);
		return userById(userId);
	}

	public Map<String, Object> loginAdmin(Map<String, Object> body) {
		String account = required(body, "account", "管理员账号");
		String password = required(body, "password", "管理员密码");
		List<Map<String, Object>> admins = jdbcTemplate.queryForList("""
				SELECT id AS adminId, username, password_hash AS passwordHash, role, status
				FROM admin_users
				WHERE username = ? AND status = 'NORMAL'
				LIMIT 1
				""", account);

		if (admins.isEmpty() || !passwordMatches(password, String.valueOf(admins.get(0).get("passwordHash")))) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "管理员账号或密码错误");
		}

		Map<String, Object> admin = new LinkedHashMap<>(admins.get(0));
		admin.remove("passwordHash");
		jdbcTemplate.update("UPDATE admin_users SET last_login_at = CURRENT_TIMESTAMP WHERE id = ?", admin.get("adminId"));
		return admin;
	}

	public boolean changeAdminPassword(Long adminId, Map<String, Object> body) {
		if (adminId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录管理员账号");
		}
		String oldPassword = required(body, "oldPassword", "当前密码");
		String newPassword = required(body, "newPassword", "新密码");
		if (newPassword.length() < 8) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码至少 8 位");
		}
		List<String> passwordHashes = jdbcTemplate.queryForList("""
				SELECT password_hash
				FROM admin_users
				WHERE id = ? AND status = 'NORMAL'
				LIMIT 1
				""", String.class, adminId);
		if (passwordHashes.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "管理员账号不存在或已停用");
		}
		if (!passwordMatches(oldPassword, passwordHashes.get(0))) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "当前密码错误");
		}
		jdbcTemplate.update("UPDATE admin_users SET password_hash = ? WHERE id = ?", hashPassword(newPassword), adminId);
		return true;
	}

	private Map<String, Object> userByStudentNo(String studentNo) {
		List<Map<String, Object>> users = jdbcTemplate.queryForList("""
				SELECT id AS userId, student_no AS studentNo, nickname, real_name AS realName,
				  phone, email, avatar_url AS avatarUrl, campus, department,
				  enrollment_year AS enrollmentYear, credit_score AS creditScore,
				  created_at AS createdAt
				FROM users
				WHERE deleted = 0 AND student_no = ?
				LIMIT 1
				""", studentNo);
		return users.isEmpty() ? Map.of() : users.get(0);
	}

	private Map<String, Object> userById(Long userId) {
		List<Map<String, Object>> users = jdbcTemplate.queryForList("""
				SELECT id AS userId, student_no AS studentNo, nickname, real_name AS realName,
				  phone, email, avatar_url AS avatarUrl, campus, department,
				  enrollment_year AS enrollmentYear, credit_score AS creditScore,
				  created_at AS createdAt
				FROM users
				WHERE deleted = 0 AND id = ?
				LIMIT 1
				""", userId);
		return users.isEmpty() ? Map.of() : users.get(0);
	}

	private String required(Map<String, Object> body, String key, String label) {
		String value = optional(body, key);
		if (value.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写" + label);
		}
		return value;
	}

	private String optional(Map<String, Object> body, String key) {
		Object value = body.get(key);
		return value == null ? "" : String.valueOf(value).trim();
	}

	private boolean boolValue(Object value) {
		return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
	}

	private String hashPassword(String password) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
			return "sha256$" + HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is unavailable", ex);
		}
	}

	private boolean passwordMatches(String rawPassword, String storedPassword) {
		if (storedPassword == null || storedPassword.isBlank()) {
			return false;
		}

		if (storedPassword.startsWith("sha256$")) {
			return hashPassword(rawPassword).equals(storedPassword);
		}

		return storedPassword.equals(rawPassword);
	}

	private Long toLong(Object value) {
		if (value instanceof Number number) {
			return number.longValue();
		}
		return Long.valueOf(String.valueOf(value));
	}
}
