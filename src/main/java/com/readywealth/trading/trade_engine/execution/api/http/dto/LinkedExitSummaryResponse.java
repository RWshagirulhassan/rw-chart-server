package com.readywealth.trading.trade_engine.execution.api.http.dto;

import com.readywealth.trading.trade_engine.execution.domain.LinkedExitSummary;

public record LinkedExitSummaryResponse(
        String exitPlanId,
        String status,
        String mode,
        String armPolicy) {

    public static LinkedExitSummaryResponse from(LinkedExitSummary summary) {
        if (summary == null) {
            return null;
        }
        return new LinkedExitSummaryResponse(
                summary.exitPlanId(),
                summary.status().name(),
                summary.mode() == null ? null : summary.mode().name(),
                summary.armPolicy() == null ? null : summary.armPolicy().name());
    }
}
