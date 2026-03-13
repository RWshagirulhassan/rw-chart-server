package com.readywealth.trading.trade_engine.execution.domain;

public enum OrderTimelineStatus {
    PENDING,
    ARMED,
    TRIGGERED,
    OPEN,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED_OCO,
    REJECTED,
    REARM_REQUIRED,
    CANCELLED;

    public boolean terminal() {
        return this == FILLED
                || this == REJECTED
                || this == REARM_REQUIRED
                || this == CANCELLED
                || this == CANCELLED_OCO;
    }
}
