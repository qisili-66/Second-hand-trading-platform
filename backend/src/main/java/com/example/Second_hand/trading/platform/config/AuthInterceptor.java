package com.example.Second_hand.trading.platform.config;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import com.example.Second_hand.trading.platform.service.JwtService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AuthInterceptor implements HandlerInterceptor {
	private final JwtService jwtService;

	public AuthInterceptor(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || isPublicApi(request)) {
			return true;
		}

		JwtService.JwtClaims claims = jwtService.requireAuthorization(request.getHeader("Authorization"));
		boolean adminPath = isAdminPath(request.getRequestURI());
		if (adminPath && !claims.isAdmin()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有管理员权限");
		}
		if (!adminPath && !claims.isUser()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有用户权限");
		}

		request.setAttribute("authType", claims.type());
		request.setAttribute("authId", claims.id());
		request.setAttribute("authAccount", claims.account());
		request.setAttribute("authRole", claims.role());
		return true;
	}

	private boolean isPublicApi(HttpServletRequest request) {
		String method = request.getMethod();
		String path = request.getRequestURI();

		if ("POST".equals(method) && (
				"/api/auth/register".equals(path)
						|| "/api/auth/login".equals(path)
						|| "/api/auth/admin/login".equals(path)
						|| "/api/payments/alipay/notify".equals(path)
						|| "/api/payments/wechat/notify".equals(path))) {
			return true;
		}

		if ("GET".equals(method) && (
				"/api/health".equals(path)
						|| "/api/categories".equals(path)
						|| "/api/items".equals(path)
						|| path.matches("^/api/items/\\d+$")
						|| path.matches("^/api/users/\\d+$")
						|| path.matches("^/api/users/\\d+/items$")
						|| path.matches("^/api/users/\\d+/reviews$")
						|| path.matches("^/api/reviews/user/\\d+$")
						|| path.matches("^/api/reviews/user/\\d+/stats$")
						|| path.matches("^/api/items/\\d+/comments$")
						|| path.matches("^/api/files/images/[^/]+$")
						|| "/api/purchases".equals(path)
						|| path.matches("^/api/purchases/\\d+/matches$")
						|| "/api/exchanges".equals(path)
						|| path.matches("^/api/exchanges/\\d+/matches$")
						|| "/api/wanted-posts".equals(path)
						|| "/api/swap-requests".equals(path))) {
			return true;
		}

		return false;
	}

	private boolean isAdminPath(String path) {
		return "/api/admin".equals(path) || path.startsWith("/api/admin/");
	}
}
