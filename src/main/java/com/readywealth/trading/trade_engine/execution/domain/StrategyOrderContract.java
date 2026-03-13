package com.readywealth.trading.trade_engine.execution.domain;

public record StrategyOrderContract(
        String scriptId,
        TradingOrderType requiredOrderType,
        boolean autoTradable,
        String notes) {
}
