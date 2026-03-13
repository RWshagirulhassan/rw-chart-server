package com.readywealth.trading.trade_engine.marketdata.domain;

public record TokenOperationResult(
	long instrumentToken,
	String status,
	boolean upstreamSubscribed,
	boolean upstreamUnsubscribed,
	boolean modeUpdated,
	InstrumentStateView state
) {
}
