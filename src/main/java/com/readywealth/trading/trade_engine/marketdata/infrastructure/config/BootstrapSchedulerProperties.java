package com.readywealth.trading.trade_engine.marketdata.infrastructure.config;

import com.readywealth.trading.trade_engine.engine.domain.series.IntervalDescriptor;
import com.readywealth.trading.trade_engine.engine.domain.series.IntervalKind;
import com.readywealth.trading.trade_engine.engine.domain.series.IntervalDescriptors;
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

    public static class HistoricalLookback {
        private int time1m = 3;
        private int time2m = 3;
        private int time3m = 3;
        private int time5m = 10;
        private int time10m = 10;
        private int time15m = 10;
        private int time30m = 60;
        private int time45m = 60;
        private int time1h = 60;
        private int time1d = 600;
        private int time1w = 1500;
        private int time1mo = 2300;

        public int getTime1m() {
            return time1m;
        }

        public void setTime1m(int time1m) {
            this.time1m = time1m;
        }

        public int getTime2m() {
            return time2m;
        }

        public void setTime2m(int time2m) {
            this.time2m = time2m;
        }

        public int getTime3m() {
            return time3m;
        }

        public void setTime3m(int time3m) {
            this.time3m = time3m;
        }

        public int getTime5m() {
            return time5m;
        }

        public void setTime5m(int time5m) {
            this.time5m = time5m;
        }

        public int getTime10m() {
            return time10m;
        }

        public void setTime10m(int time10m) {
            this.time10m = time10m;
        }

        public int getTime15m() {
            return time15m;
        }

        public void setTime15m(int time15m) {
            this.time15m = time15m;
        }

        public int getTime30m() {
            return time30m;
        }

        public void setTime30m(int time30m) {
            this.time30m = time30m;
        }

        public int getTime45m() {
            return time45m;
        }

        public void setTime45m(int time45m) {
            this.time45m = time45m;
        }

        public int getTime1h() {
            return time1h;
        }

        public void setTime1h(int time1h) {
            this.time1h = time1h;
        }

        public int getTime1d() {
            return time1d;
        }

        public void setTime1d(int time1d) {
            this.time1d = time1d;
        }

        public int getTime1w() {
            return time1w;
        }

        public void setTime1w(int time1w) {
            this.time1w = time1w;
        }

        public int getTime1mo() {
            return time1mo;
        }

        public void setTime1mo(int time1mo) {
            this.time1mo = time1mo;
        }

        public int forInterval(IntervalKind kind) {
            return switch (kind) {
                case TIME_1M -> time1m;
                case TIME_2M -> time2m;
                case TIME_3M -> time3m;
                case TIME_5M -> time5m;
                case TIME_10M -> time10m;
                case TIME_15M -> time15m;
                case TIME_30M -> time30m;
                case TIME_45M -> time45m;
                case TIME_1H -> time1h;
                case TIME_1D -> time1d;
                case TIME_1W -> time1w;
                case TIME_1MO -> time1mo;
                default -> 0;
            };
        }
    }

    public static class HistoricalRecentLimit {
        private int time1w = 300;
        private int time1mo = 100;

        public int getTime1w() {
            return time1w;
        }

        public void setTime1w(int time1w) {
            this.time1w = time1w;
        }

        public int getTime1mo() {
            return time1mo;
        }

        public void setTime1mo(int time1mo) {
            this.time1mo = time1mo;
        }

        public int forInterval(IntervalKind kind) {
            return switch (kind) {
                case TIME_1W -> time1w;
                case TIME_1MO -> time1mo;
                default -> 0;
            };
        }
    }

    public static class Historical {
        private int chunkDays = 365;
        private HistoricalLookback lookback = new HistoricalLookback();
        private HistoricalRecentLimit recentLimit = new HistoricalRecentLimit();

        public int getChunkDays() {
            return chunkDays;
        }

        public void setChunkDays(int chunkDays) {
            this.chunkDays = chunkDays;
        }

        public HistoricalLookback getLookback() {
            return lookback;
        }

        public void setLookback(HistoricalLookback lookback) {
            this.lookback = lookback == null ? new HistoricalLookback() : lookback;
        }

        public HistoricalRecentLimit getRecentLimit() {
            return recentLimit;
        }

        public void setRecentLimit(HistoricalRecentLimit recentLimit) {
            this.recentLimit = recentLimit == null ? new HistoricalRecentLimit() : recentLimit;
        }
    }

    private int requestsPerSecond = 2;
    private int burstSize = 1;
    private Retry retry = new Retry();
    private Historical historical = new Historical();

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

    public Historical getHistorical() {
        return historical;
    }

    public void setHistorical(Historical historical) {
        this.historical = historical == null ? new Historical() : historical;
    }

    public int historicalChunkDays() {
        return Math.max(1, historical.getChunkDays());
    }

    public int lookbackBusinessDays(IntervalKind kind) {
        int configured = historical.getLookback().forInterval(kind);
        if (configured > 0) {
            return configured;
        }
        IntervalDescriptor descriptor = IntervalDescriptors.of(kind);
        return descriptor.defaultSourceLookbackBusinessDays();
    }

    public int recentCandleLimit(IntervalKind kind) {
        return Math.max(0, historical.getRecentLimit().forInterval(kind));
    }
}
