package com.readywealth.trading.trade_engine.execution.domain;

public enum TradingOrderStatus {
    NEW,
    OPEN,
    PARTIAL,
    FILLED,
    CANCELLED,
    REJECTED;

    public boolean terminal() {
        return this == FILLED || this == CANCELLED || this == REJECTED;
    }
}
