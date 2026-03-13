package com.readywealth.trading.trade_engine.execution.domain;

public record TradingFill(
        String fillId,
        String orderId,
        String brokerTradeId,
        int qty,
        double price,
        double fees,
        long timestampEpochMs) {
}
