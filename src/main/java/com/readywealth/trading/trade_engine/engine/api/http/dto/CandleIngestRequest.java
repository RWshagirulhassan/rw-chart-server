package com.readywealth.trading.trade_engine.engine.api.http.dto;

import java.util.List;

public record CandleIngestRequest(
        List<CandleInput> bars) {

    public record CandleInput(
            long startTimeEpochMs,
            long endTimeEpochMs,
            double open,
            double high,
            double low,
            double close,
            long volume,
            long tickCount,
            int barVersion,
            String cause) {
    }
}
