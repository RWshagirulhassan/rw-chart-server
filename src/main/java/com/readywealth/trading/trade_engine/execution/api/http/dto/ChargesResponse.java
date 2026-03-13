package com.readywealth.trading.trade_engine.execution.api.http.dto;

import com.readywealth.trading.trade_engine.execution.domain.ChargesResult;

import java.util.Map;

public record ChargesResponse(
        double totalCharges,
        Map<String, Double> breakdown,
        String source,
        boolean degraded,
        String message) {

    public static ChargesResponse from(ChargesResult result) {
        return new ChargesResponse(
                result.totalCharges(),
                result.breakdown(),
                result.source().name(),
                result.degraded(),
                result.message());
    }
}
