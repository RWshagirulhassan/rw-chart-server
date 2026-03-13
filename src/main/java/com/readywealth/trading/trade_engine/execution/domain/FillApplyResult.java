package com.readywealth.trading.trade_engine.execution.domain;

public record FillApplyResult(
        boolean duplicate,
        TradingOrder order,
        TradingPosition position,
        TradingFundsView funds,
        TradingFill fill) {
}
