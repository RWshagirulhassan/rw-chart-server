package com.readywealth.trading.trade_engine.marketdata.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kite")
public class KiteSocketProperties {
	private String apiKey;
	private int wsMaxRetries = 50;
	private int wsMaxRetryIntervalSeconds = 60;

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public int getWsMaxRetries() {
		return wsMaxRetries;
	}

	public void setWsMaxRetries(int wsMaxRetries) {
		this.wsMaxRetries = wsMaxRetries;
	}

	public int getWsMaxRetryIntervalSeconds() {
		return wsMaxRetryIntervalSeconds;
	}

	public void setWsMaxRetryIntervalSeconds(int wsMaxRetryIntervalSeconds) {
		this.wsMaxRetryIntervalSeconds = wsMaxRetryIntervalSeconds;
	}
}
