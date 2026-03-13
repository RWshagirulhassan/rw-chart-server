package com.readywealth.trading.trade_engine.execution.domain;

public record TradingPosition(
        String accountId,
        long instrumentToken,
        String tradingsymbol,
        TradingProduct product,
        int netQty,
        double avgPrice,
        double realizedPnl,
        double unrealizedPnl,
        double lastPrice,
        long updatedAtEpochMs) {
}
