package com.readywealth.trading.trade_engine.engine.api.http.dto;

public record SessionCreateRequest(
        long instrumentToken,
        String timeframe,
        Integer maximumBarCount) {
}
