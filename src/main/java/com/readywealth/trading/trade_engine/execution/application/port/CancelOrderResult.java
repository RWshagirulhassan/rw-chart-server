package com.readywealth.trading.trade_engine.execution.application.port;

import com.readywealth.trading.trade_engine.execution.domain.TradingOrder;

public record CancelOrderResult(
        TradingOrder order,
        String message) {
}
