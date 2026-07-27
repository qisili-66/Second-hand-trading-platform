package com.example.Second_hand.trading.platform.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import com.example.Second_hand.trading.platform.dto.ApiResponse;
import com.example.Second_hand.trading.platform.exception.AgentServiceException;
import com.example.Second_hand.trading.platform.exception.AgentServiceException.Reason;

@RestControllerAdvice
public class GlobalExceptionHandler {
	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ApiResponse<Object>> handleResponseStatus(ResponseStatusException ex) {
		HttpStatusCode status = ex.getStatusCode();
		String message = ex.getReason() == null || ex.getReason().isBlank()
				? "请求处理失败"
				: ex.getReason();
		return ResponseEntity.status(status).body(ApiResponse.error(errorCode(status.value()), message));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Object>> handleIllegalArgument(IllegalArgumentException ex) {
		String message = ex.getMessage() == null || ex.getMessage().isBlank() ? "参数错误" : ex.getMessage();
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(40001, message));
	}

	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<ApiResponse<Object>> handleIllegalState(IllegalStateException ex) {
		String message = ex.getMessage() == null || ex.getMessage().isBlank() ? "服务暂不可用" : ex.getMessage();
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error(50300, message));
	}

	@ExceptionHandler(AgentServiceException.class)
	public ResponseEntity<ApiResponse<Object>> handleAgentService(AgentServiceException ex) {
		Reason reason = ex.getReason();
		HttpStatus status = switch (reason) {
			case OVERLOADED -> HttpStatus.TOO_MANY_REQUESTS;
			case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
			case INVALID_RESPONSE -> HttpStatus.BAD_GATEWAY;
			case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
		};
		int code = switch (reason) {
			case OVERLOADED -> 42901;
			case TIMEOUT -> 50401;
			case INVALID_RESPONSE -> 50201;
			case UNAVAILABLE -> 50301;
		};
		return ResponseEntity.status(status).body(ApiResponse.error(code, ex.getMessage()));
	}

	@ExceptionHandler({
			MissingServletRequestParameterException.class,
			MethodArgumentTypeMismatchException.class,
			HttpMessageNotReadableException.class
	})
	public ResponseEntity<ApiResponse<Object>> handleBadRequest(Exception ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(40001, "参数错误"));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Object>> handleException(Exception ex) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ApiResponse.error(50000, "服务器内部错误"));
	}

	private int errorCode(int status) {
		return switch (status) {
			case 400 -> 40001;
			case 401 -> 40100;
			case 403 -> 40300;
			case 404 -> 40400;
			case 409 -> 40900;
			default -> status >= 500 ? 50000 : 40001;
		};
	}
}
