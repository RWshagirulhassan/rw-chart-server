package com.readywealth.trading.trade_engine.marketdata.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bootstrap.scheduler")
public class BootstrapSchedulerProperties {

    public static class Retry {
        private long initialDelayMs = 500L;
        private long maxDelayMs = 30_000L;
        private double jitterRatio = 0.2d;

        public long getInitialDelayMs() {
            return initialDelayMs;
        }

        public void setInitialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
        }

        public long getMaxDelayMs() {
            return maxDelayMs;
        }

        public void setMaxDelayMs(long maxDelayMs) {
            this.maxDelayMs = maxDelayMs;
        }

        public double getJitterRatio() {
            return jitterRatio;
        }

        public void setJitterRatio(double jitterRatio) {
            this.jitterRatio = jitterRatio;
        }
    }

    private int requestsPerSecond = 2;
    private int burstSize = 1;
    private Retry retry = new Retry();

    public int getRequestsPerSecond() {
        return requestsPerSecond;
    }

    public void setRequestsPerSecond(int requestsPerSecond) {
        this.requestsPerSecond = requestsPerSecond;
    }

    public int getBurstSize() {
        return burstSize;
    }

    public void setBurstSize(int burstSize) {
        this.burstSize = burstSize;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry == null ? new Retry() : retry;
    }
}
