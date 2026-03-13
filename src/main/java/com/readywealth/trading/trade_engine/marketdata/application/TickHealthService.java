package com.readywealth.trading.trade_engine.marketdata.application;

import com.readywealth.trading.trade_engine.marketdata.domain.HealthzResponse;
import org.springframework.stereotype.Service;

@Service
public class TickHealthService {
	private final SubscriptionService subscriptionService;
	private final AuthTokenProvider authTokenProvider;
	private final KiteSessionManager kiteSessionManager;

	public TickHealthService(
		SubscriptionService subscriptionService,
		AuthTokenProvider authTokenProvider,
		KiteSessionManager kiteSessionManager
	) {
		this.subscriptionService = subscriptionService;
		this.authTokenProvider = authTokenProvider;
		this.kiteSessionManager = kiteSessionManager;
	}

	public HealthzResponse snapshot() {
		AuthTokenProvider.TokenHealth tokenHealth = authTokenProvider.tokenHealth();
		KiteSessionManager.SessionHealth sessionHealth = kiteSessionManager.health();
		return new HealthzResponse(
			sessionHealth.connected(),
			tokenHealth.tokenPresent(),
			tokenHealth.tokenExpired(),
			subscriptionService.activeInstruments(),
			subscriptionService.totalSubscribers(),
			tokenHealth.lastTokenFetchAt(),
			sessionHealth.lastWsConnectAt(),
			sessionHealth.lastError() != null ? sessionHealth.lastError() : tokenHealth.lastError()
		);
	}
}
