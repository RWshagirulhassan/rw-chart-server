package com.readywealth.trading.trade_engine.execution.api.http.dto;

import com.readywealth.trading.trade_engine.execution.domain.MarginCheckResult;

import java.util.Map;

public record MarginCheckResponse(
        boolean allowed,
        double requiredMargin,
        double finalMargin,
        Map<String, Double> breakdown,
        String message,
        String source,
        boolean degraded) {

    public static MarginCheckResponse from(MarginCheckResult result) {
        return new MarginCheckResponse(
                result.allowed(),
                result.requiredMargin(),
                result.finalMargin(),
                result.breakdown(),
                result.message(),
                result.source().name(),
                result.degraded());
    }
}
