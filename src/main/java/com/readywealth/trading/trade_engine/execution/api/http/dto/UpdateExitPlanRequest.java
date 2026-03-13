package com.readywealth.trading.trade_engine.execution.api.http.dto;

public record UpdateExitPlanRequest(
        ExitLegRequest takeProfit,
        ExitLegRequest stopLoss) {
}
