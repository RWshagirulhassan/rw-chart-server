package com.readywealth.trading.trade_engine.engine.api.http.dto;

public record TradeIngestRequest(
        long timeEpochMs,
        double price,
        double volume) {
}
