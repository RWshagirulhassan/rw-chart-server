package com.readywealth.trading.trade_engine.execution.api.http.dto;

public record CreateExitPlanRequest(
        String product,
        String armPolicy,
        ExitLegRequest takeProfit,
        ExitLegRequest stopLoss) {
}
