package com.readywealth.trading.trade_engine.execution.domain;

public record StrategyExecutionSettings(
        String accountId,
        String scriptId,
        boolean enabled,
        int fixedQty,
        TradingProduct product,
        long updatedAtEpochMs) {
}
