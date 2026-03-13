package com.readywealth.trading.trade_engine.marketdata.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ticks")
public class PublisherProperties {
	private String defaultMode = "full";
	private int dispatcherWorkers = 2;
	private long tokenCacheSeconds = 5;
	private long retryInitialDelayMs = 1000;
	private long retryMaxDelayMs = 30000;
	private long logSampleIntervalMs = 2000;

	public String getDefaultMode() {
		return defaultMode;
	}

	public void setDefaultMode(String defaultMode) {
		this.defaultMode = defaultMode;
	}

	public int getDispatcherWorkers() {
		return dispatcherWorkers;
	}

	public void setDispatcherWorkers(int dispatcherWorkers) {
		this.dispatcherWorkers = dispatcherWorkers;
	}

	public long getTokenCacheSeconds() {
		return tokenCacheSeconds;
	}

	public void setTokenCacheSeconds(long tokenCacheSeconds) {
		this.tokenCacheSeconds = tokenCacheSeconds;
	}

	public long getRetryInitialDelayMs() {
		return retryInitialDelayMs;
	}

	public void setRetryInitialDelayMs(long retryInitialDelayMs) {
		this.retryInitialDelayMs = retryInitialDelayMs;
	}

	public long getRetryMaxDelayMs() {
		return retryMaxDelayMs;
	}

	public void setRetryMaxDelayMs(long retryMaxDelayMs) {
		this.retryMaxDelayMs = retryMaxDelayMs;
	}

	public long getLogSampleIntervalMs() {
		return logSampleIntervalMs;
	}

	public void setLogSampleIntervalMs(long logSampleIntervalMs) {
		this.logSampleIntervalMs = logSampleIntervalMs;
	}
}
