package com.readywealth.trading.trade_engine.engine.application.runtime;

public record SeriesAdvanceEvent(
        int beforeBeginIndex,
        int beforeEndIndex,
        int afterBeginIndex,
        int afterEndIndex,
        SeriesMutationType mutationType,
        long eventTimeEpochMs) {
}
