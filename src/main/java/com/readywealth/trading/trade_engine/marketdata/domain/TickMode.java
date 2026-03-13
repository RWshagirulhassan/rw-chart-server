package com.readywealth.trading.trade_engine.marketdata.domain;

public enum TickMode {
	LTP(1, "ltp"),
	QUOTE(2, "quote"),
	FULL(3, "full");

	private final int priority;
	private final String kiteMode;

	TickMode(int priority, String kiteMode) {
		this.priority = priority;
		this.kiteMode = kiteMode;
	}

	public int getPriority() {
		return priority;
	}

	public String getKiteMode() {
		return kiteMode;
	}

	public static TickMode max(TickMode a, TickMode b) {
		return a.priority >= b.priority ? a : b;
	}
}
