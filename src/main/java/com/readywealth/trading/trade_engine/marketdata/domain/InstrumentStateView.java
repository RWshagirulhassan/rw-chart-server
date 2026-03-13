package com.readywealth.trading.trade_engine.marketdata.domain;

import java.time.Instant;
import java.util.Set;

public record InstrumentStateView(
	long instrumentToken,
	int refCount,
	Set<String> subscriberIds,
	TickMode mode,
	Instant subscribedAt
) {
}
