package com.example.Second_hand.trading.platform.config;

import java.security.Principal;
import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.example.Second_hand.trading.platform.service.JwtService;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
	private static final List<String> ALLOWED_SUBSCRIPTIONS = List.of(
			"/user/queue/notifications",
			"/user/queue/messages",
			"/topic/broadcast");

	private final JwtService jwtService;

	public WebSocketConfig(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws")
				.setAllowedOriginPatterns("http://localhost:5173", "http://127.0.0.1:5173", "http://*:5173")
				.withSockJS();
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic", "/queue");
		registry.setApplicationDestinationPrefixes("/app");
		registry.setUserDestinationPrefix("/user");
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(new ChannelInterceptor() {
			@Override
			public Message<?> preSend(Message<?> message, MessageChannel channel) {
				StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
				if (accessor == null || accessor.getCommand() == null) {
					return message;
				}

				StompCommand command = accessor.getCommand();
				if (StompCommand.CONNECT.equals(command)) {
					authenticate(accessor);
				} else if (StompCommand.SUBSCRIBE.equals(command)) {
					validateSubscription(accessor);
				} else if (StompCommand.SEND.equals(command)) {
					throw new IllegalArgumentException("WebSocket SEND is disabled; use REST APIs");
				}
				return message;
			}
		});
	}

	private void authenticate(StompHeaderAccessor accessor) {
		String authorization = accessor.getFirstNativeHeader("Authorization");
		if (authorization == null) {
			authorization = accessor.getFirstNativeHeader("authorization");
		}
		JwtService.JwtClaims claims = jwtService.requireAuthorization(authorization);
		if (!claims.isUser()) {
			throw new IllegalArgumentException("Only user tokens can connect to WebSocket");
		}
		accessor.setUser(new StompUserPrincipal(claims.id()));
	}

	private void validateSubscription(StompHeaderAccessor accessor) {
		Principal user = accessor.getUser();
		String destination = accessor.getDestination();
		if (user == null) {
			throw new IllegalArgumentException("WebSocket subscription requires authentication");
		}
		if (!ALLOWED_SUBSCRIPTIONS.contains(destination)) {
			throw new IllegalArgumentException("WebSocket subscription is not allowed");
		}
	}

	private record StompUserPrincipal(Long userId) implements Principal {
		@Override
		public String getName() {
			return String.valueOf(userId);
		}
	}
}
