package com.readywealth.trading.trade_engine.execution.domain;

public record FillApplyCommand(
        TradingAccount account,
        TradingOrder order,
        TradingFill fill,
        double markPrice,
        int dedupeTtlSeconds) {
}
