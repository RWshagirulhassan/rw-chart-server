package com.readywealth.trading.trade_engine.execution.domain;

public record TriggerFireResult(
        boolean fired,
        Trigger trigger,
        Trigger cancelledSibling,
        OcoGroup ocoGroup) {

    public static TriggerFireResult noop() {
        return new TriggerFireResult(false, null, null, null);
    }
}
