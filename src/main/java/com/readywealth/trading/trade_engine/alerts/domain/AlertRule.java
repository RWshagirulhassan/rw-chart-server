package com.readywealth.trading.trade_engine.alerts.domain;

public record AlertRule(
        String id,
        String name,
        boolean enabled
) {
}
