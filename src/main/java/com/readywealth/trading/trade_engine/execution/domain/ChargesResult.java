package com.readywealth.trading.trade_engine.execution.domain;

import java.util.Map;

public record ChargesResult(
        double totalCharges,
        Map<String, Double> breakdown,
        MarginSource source,
        boolean degraded,
        String message) {
}
