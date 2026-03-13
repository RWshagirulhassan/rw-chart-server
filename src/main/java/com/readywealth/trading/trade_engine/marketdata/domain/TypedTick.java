package com.readywealth.trading.trade_engine.marketdata.domain;

public record TypedTick(
	TickQuote tickQuote,
	IndexQuote indexQuote
) {
}
