package com.readywealth.trading.trade_engine.execution.api.http.dto;

public record PlaceOrderRequest(
        Long instrumentToken,
        String tradingsymbol,
        String exchange,
        String product,
        String orderType,
        String side,
        Integer qty,
        Double price,
        Double triggerPrice,
        String reason,
        OrderAttachmentsRequest attachments) {
}
