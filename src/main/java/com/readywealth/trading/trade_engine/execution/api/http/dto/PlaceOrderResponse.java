package com.readywealth.trading.trade_engine.execution.api.http.dto;

public record PlaceOrderResponse(
        OrderResponse order,
        LinkedExitSummaryResponse linkedExit) {
}
