package com.readywealth.trading.trade_engine.marketdata.domain;

import java.util.List;

public record BatchSubscriptionResponse(
	String subscriberId,
	String action,
	List<TokenOperationResult> results
) {
}
