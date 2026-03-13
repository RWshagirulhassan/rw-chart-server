package com.readywealth.trading.trade_engine.execution.api.http.dto;

public record UpdateStrategySettingsRequest(
        Boolean enabled,
        Integer fixedQty,
        String product) {
}
