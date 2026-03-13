package com.readywealth.trading.trade_engine.marketdata.api.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class TicksWebSocketConfig implements WebSocketConfigurer {
	private final TicksWebSocketHandler ticksWebSocketHandler;

	public TicksWebSocketConfig(TicksWebSocketHandler ticksWebSocketHandler) {
		this.ticksWebSocketHandler = ticksWebSocketHandler;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(ticksWebSocketHandler, "/ticks/ws").setAllowedOriginPatterns("*");
	}
}
