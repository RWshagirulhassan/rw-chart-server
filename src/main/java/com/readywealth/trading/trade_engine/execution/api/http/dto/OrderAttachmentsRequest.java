package com.readywealth.trading.trade_engine.execution.api.http.dto;

public record OrderAttachmentsRequest(
        String armPolicy,
        ExitLegRequest takeProfit,
        ExitLegRequest stopLoss) {
}
