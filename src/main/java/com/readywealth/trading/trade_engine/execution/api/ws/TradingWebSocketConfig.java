package com.readywealth.trading.trade_engine.execution.api.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class TradingWebSocketConfig implements WebSocketConfigurer {

    private final TradingWebSocketHandler tradingWebSocketHandler;

    public TradingWebSocketConfig(TradingWebSocketHandler tradingWebSocketHandler) {
        this.tradingWebSocketHandler = tradingWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(tradingWebSocketHandler, "/trading/ws").setAllowedOriginPatterns("*");
    }
}
