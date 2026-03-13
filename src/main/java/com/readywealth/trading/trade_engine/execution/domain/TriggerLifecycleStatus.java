package com.readywealth.trading.trade_engine.execution.domain;

public enum TriggerLifecycleStatus {
    NOOP,
    FIRED,
    FIRED_AND_SIBLING_CANCELLED
}
