package com.readywealth.trading.trade_engine.engine.domain.series;

public record SeriesKey(
        long instrumentToken,
        IntervalKind timeframe) {
    @Override
    public String toString() {
        return instrumentToken + "@" + timeframe;
    }
}
