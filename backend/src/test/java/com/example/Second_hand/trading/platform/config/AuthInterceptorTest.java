package com.example.Second_hand.trading.platform.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.Second_hand.trading.platform.service.JwtService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class AuthInterceptorTest {
	private static final String JWT_SECRET = "this-is-a-test-secret-with-more-than-thirty-two-characters";

	@Test
	void injectsAuthenticatedUserIdForAgentRequests() {
		JwtService jwtService = new JwtService(JWT_SECRET, 120);
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/api/agent/buyer/runs");
		when(request.getMethod()).thenReturn("POST");
		when(request.getHeader("Authorization")).thenReturn("Bearer " + jwtService.createUserToken(101L, "student-101"));

		boolean allowed = new AuthInterceptor(jwtService, "internal-token")
				.preHandle(request, mock(HttpServletResponse.class), new Object());

		assertEquals(true, allowed);
		verify(request).setAttribute("authId", 101L);
	}

	@Test
	void rejectsInternalToolRequestWithoutMatchingServiceToken() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getRequestURI()).thenReturn("/api/internal/agent-tools/order-status");
		when(request.getHeader("X-Agent-Service-Token")).thenReturn("wrong-token");

		ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
				new AuthInterceptor(new JwtService(JWT_SECRET, 120), "internal-token")
						.preHandle(request, mock(HttpServletResponse.class), new Object()));

		assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
	}
}
