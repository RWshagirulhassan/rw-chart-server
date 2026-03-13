package com.readywealth.trading.trade_engine.marketdata.api.ws;

import com.readywealth.trading.trade_engine.marketdata.application.TickHealthService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TicksHealthPublisher {
	private final TickHealthService tickHealthService;
	private final TicksWebSocketBroadcaster webSocketBroadcaster;

	public TicksHealthPublisher(TickHealthService tickHealthService, TicksWebSocketBroadcaster webSocketBroadcaster) {
		this.tickHealthService = tickHealthService;
		this.webSocketBroadcaster = webSocketBroadcaster;
	}

	@Scheduled(fixedDelay = 5000)
	public void publishHealth() {
		webSocketBroadcaster.broadcastHealth(tickHealthService.snapshot());
	}
}
