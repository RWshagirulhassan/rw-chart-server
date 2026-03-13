package com.readywealth.trading.trade_engine.marketdata.domain;

import java.util.List;

public class UnsubscribeRequest {
	private String subscriberId;
	private List<Long> instrumentTokens;

	public String getSubscriberId() {
		return subscriberId;
	}

	public void setSubscriberId(String subscriberId) {
		this.subscriberId = subscriberId;
	}

	public List<Long> getInstrumentTokens() {
		return instrumentTokens;
	}

	public void setInstrumentTokens(List<Long> instrumentTokens) {
		this.instrumentTokens = instrumentTokens;
	}
}
