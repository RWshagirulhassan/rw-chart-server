package com.readywealth.trading.trade_engine.execution.domain;

import java.util.Map;

public record MarginCheckResult(
        boolean allowed,
        double requiredMargin,
        double finalMargin,
        Map<String, Double> breakdown,
        String message,
        MarginSource source,
        boolean degraded) {

    public static MarginCheckResult rejected(String message, MarginSource source) {
        return new MarginCheckResult(false, 0.0d, 0.0d, Map.of(), message, source, false);
    }
}
