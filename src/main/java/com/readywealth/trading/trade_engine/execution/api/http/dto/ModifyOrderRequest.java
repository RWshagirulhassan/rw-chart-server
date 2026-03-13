package com.readywealth.trading.trade_engine.execution.api.http.dto;

public record ModifyOrderRequest(
        Integer qty,
        Double price,
        Double triggerPrice) {
}
