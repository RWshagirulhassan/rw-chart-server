package com.readywealth.trading.trade_engine.execution.domain;

public record ExitLegSpec(
        ExitLegType legType,
        double triggerPrice,
        double limitOffset,
        TradingSide orderSide,
        int qty) {
}
