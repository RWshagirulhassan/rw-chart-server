package com.readywealth.trading.trade_engine.marketdata.application;

public interface UpstreamSession {
	void applyMutation(SubscriptionRegistry.MutationOutcome outcome);
}
