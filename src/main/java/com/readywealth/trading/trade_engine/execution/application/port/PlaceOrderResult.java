package com.readywealth.trading.trade_engine.execution.application.port;

import com.readywealth.trading.trade_engine.execution.domain.TradingFill;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrder;

import java.util.List;

public record PlaceOrderResult(
        TradingOrder order,
        List<TradingFill> fills,
        String message) {
}
