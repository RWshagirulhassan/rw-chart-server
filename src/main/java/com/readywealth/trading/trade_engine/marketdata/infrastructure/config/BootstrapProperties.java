package com.readywealth.trading.trade_engine.marketdata.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bootstrap.buffer")
public class BootstrapProperties {
    private int peakTps = 200;
    private int maxSeedLatencySec = 20;
    private int safetyFactor = 3;
    private String store = "memory";
    private Redis redis = new Redis();
    private Guard guard = new Guard();

    public static class Redis {
        private String keyPrefix = "bootstrap:tickbuf";

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }
    }

    public static class Guard {
        private long maxPendingTicksTotal = 10_000_000L;

        public long getMaxPendingTicksTotal() {
            return maxPendingTicksTotal;
        }

        public void setMaxPendingTicksTotal(long maxPendingTicksTotal) {
            this.maxPendingTicksTotal = maxPendingTicksTotal;
        }
    }

    public int getPeakTps() {
        return peakTps;
    }

    public void setPeakTps(int peakTps) {
        this.peakTps = peakTps;
    }

    public int getMaxSeedLatencySec() {
        return maxSeedLatencySec;
    }

    public void setMaxSeedLatencySec(int maxSeedLatencySec) {
        this.maxSeedLatencySec = maxSeedLatencySec;
    }

    public int getSafetyFactor() {
        return safetyFactor;
    }

    public void setSafetyFactor(int safetyFactor) {
        this.safetyFactor = safetyFactor;
    }

    public int capacity() {
        int p = Math.max(1, peakTps);
        int l = Math.max(1, maxSeedLatencySec);
        int s = Math.max(1, safetyFactor);
        return p * l * s;
    }

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public Redis getRedis() {
        return redis;
    }

    public void setRedis(Redis redis) {
        this.redis = redis == null ? new Redis() : redis;
    }

    public Guard getGuard() {
        return guard;
    }

    public void setGuard(Guard guard) {
        this.guard = guard == null ? new Guard() : guard;
    }
}
