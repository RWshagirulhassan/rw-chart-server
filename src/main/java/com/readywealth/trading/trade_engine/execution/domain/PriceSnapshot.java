package com.readywealth.trading.trade_engine.execution.domain;

public record PriceSnapshot(
        long instrumentToken,
        double lastPrice,
        double openPrice,
        double highPrice,
        double lowPrice,
        long asOfEpochMs,
        boolean tickBased) {
}
