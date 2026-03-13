package com.readywealth.trading.trade_engine.marketdata.api.http.dto;

public record ClientCandleView(
        String startTime,
        String endTime,
        double open,
        double high,
        double low,
        double close,
        long volume,
        long tickCount,
        int barVersion,
        String cause) {
}
