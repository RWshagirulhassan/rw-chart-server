package com.readywealth.trading.trade_engine.execution.api.http.dto;

public record ExitLegRequest(
        Boolean enabled,
        Double triggerPrice,
        Double limitOffset) {
}
