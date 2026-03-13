package com.readywealth.trading.trade_engine.engine.domain.series;

public record SeriesConfig(
        int maximumBarCount // 0 or -1 => unlimited
) {
    public static SeriesConfig liveDefault() {
        return new SeriesConfig(5000);
    }
}
