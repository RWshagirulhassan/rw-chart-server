package com.readywealth.trading.trade_engine.marketdata.domain;

import java.time.Instant;

public record HealthzResponse(
	boolean connected,
	boolean tokenPresent,
	boolean tokenExpired,
	int activeInstruments,
	int totalSubscribers,
	Instant lastTokenFetchAt,
	Instant lastWsConnectAt,
	String lastError
) {
}
