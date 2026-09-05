package com.example.Second_hand.trading.platform.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.ResponseEntity;

import com.example.Second_hand.trading.platform.dto.ApiResponse;
import com.example.Second_hand.trading.platform.exception.AgentServiceException;
import com.example.Second_hand.trading.platform.exception.AgentServiceException.Reason;

class GlobalExceptionHandlerTest {
	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void mapsAgentFailuresToStableHttpResponses() {
		assertAgentFailure(Reason.OVERLOADED, 429, 42901);
		assertAgentFailure(Reason.TIMEOUT, 504, 50401);
		assertAgentFailure(Reason.INVALID_RESPONSE, 502, 50201);
		assertAgentFailure(Reason.UNAVAILABLE, 503, 50301);
	}

	@Test
	void mapsDatabaseFailuresToSafeServiceUnavailableResponse() {
		ResponseEntity<ApiResponse<Object>> response = handler.handleDataAccess(
				new DataAccessResourceFailureException("database connection refused"));

		assertEquals(503, response.getStatusCode().value());
		assertEquals(50300, response.getBody().code());
		assertEquals("数据库暂不可用，请稍后重试", response.getBody().message());
	}

	private void assertAgentFailure(Reason reason, int expectedStatus, int expectedCode) {
		ResponseEntity<ApiResponse<Object>> response = handler.handleAgentService(
				new AgentServiceException(reason, "test"));

		assertEquals(expectedStatus, response.getStatusCode().value());
		assertEquals(expectedCode, response.getBody().code());
	}
}
