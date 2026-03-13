package com.readywealth.trading.trade_engine.session.api.http.dto;

public record CandleAppendedEvent(
        String sessionId,
        String seriesKey,
        long seq,
        SeriesBarView bar,
        Meta meta) {
    public record Meta(
            int beginIndex,
            int endIndex,
            int maxBarCount) {
    }
}
