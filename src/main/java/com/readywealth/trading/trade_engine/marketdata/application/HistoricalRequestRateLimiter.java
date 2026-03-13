package com.readywealth.trading.trade_engine.marketdata.application;

import com.readywealth.trading.trade_engine.marketdata.infrastructure.config.BootstrapSchedulerProperties;
import org.springframework.stereotype.Component;

@Component
public class HistoricalRequestRateLimiter {

    private final BootstrapSchedulerProperties properties;
    private double tokens;
    private long lastRefillNs;

    public HistoricalRequestRateLimiter(BootstrapSchedulerProperties properties) {
        this.properties = properties;
        this.tokens = Math.max(1, properties.getBurstSize());
        this.lastRefillNs = System.nanoTime();
    }

    public void acquirePermit() {
        while (true) {
            long sleepMs;
            synchronized (this) {
                refill();
                if (tokens >= 1d) {
                    tokens -= 1d;
                    return;
                }
                int rps = Math.max(1, properties.getRequestsPerSecond());
                sleepMs = Math.max(1L, Math.round(1000d / rps));
            }
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void refill() {
        int rps = Math.max(1, properties.getRequestsPerSecond());
        int burst = Math.max(1, properties.getBurstSize());
        long nowNs = System.nanoTime();
        double elapsedSeconds = Math.max(0d, (nowNs - lastRefillNs) / 1_000_000_000d);
        if (elapsedSeconds <= 0d) {
            return;
        }
        tokens = Math.min(burst, tokens + elapsedSeconds * rps);
        lastRefillNs = nowNs;
    }
}
