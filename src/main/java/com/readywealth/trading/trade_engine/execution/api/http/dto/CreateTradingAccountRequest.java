package com.readywealth.trading.trade_engine.execution.api.http.dto;

public record CreateTradingAccountRequest(
        String mode,
        String brokerType,
        Boolean autoTradeEnabled,
        Double openingBalance) {
}
