package com.example.Second_hand.trading.platform.exception;

public class AgentServiceException extends RuntimeException {
	public enum Reason {
		TIMEOUT,
		UNAVAILABLE,
		OVERLOADED,
		INVALID_RESPONSE
	}

	private final Reason reason;

	public AgentServiceException(Reason reason, String message, Throwable cause) {
		super(message, cause);
		this.reason = reason;
	}

	public AgentServiceException(Reason reason, String message) {
		this(reason, message, null);
	}

	public Reason getReason() {
		return reason;
	}
}
