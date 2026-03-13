package com.readywealth.trading.trade_engine.engine.application.runtime.model;

public record TradeIntent(
        long timestampEpochMs,
        String scriptInstanceId,
        long instrumentToken,
        String side,
        String reason,
        double price,
        long barIndex) {
}
