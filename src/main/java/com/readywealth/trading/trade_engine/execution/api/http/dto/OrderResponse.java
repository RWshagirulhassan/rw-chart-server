package com.readywealth.trading.trade_engine.execution.api.http.dto;

import com.readywealth.trading.trade_engine.execution.domain.TradingOrder;

public record OrderResponse(
        String orderId,
        String brokerOrderId,
        String accountId,
        long instrumentToken,
        String tradingsymbol,
        String exchange,
        String product,
        String orderType,
        String side,
        int qty,
        Double price,
        Double triggerPrice,
        String status,
        int filledQty,
        double avgFillPrice,
        long createdAtEpochMs,
        long updatedAtEpochMs,
        String rejectionReason,
        String source,
        String scriptId,
        String reason,
        String parentOrderId,
        String exitPlanId,
        String triggerId,
        String ocoGroupId,
        String role,
        boolean reduceOnly) {

    public static OrderResponse from(TradingOrder order) {
        return new OrderResponse(
                order.orderId(),
                order.brokerOrderId(),
                order.accountId(),
                order.instrumentToken(),
                order.tradingsymbol(),
                order.exchange(),
                order.product().name(),
                order.orderType().name(),
                order.side().name(),
                order.qty(),
                order.price(),
                order.triggerPrice(),
                order.status().name(),
                order.filledQty(),
                order.avgFillPrice(),
                order.createdAtEpochMs(),
                order.updatedAtEpochMs(),
                order.rejectionReason(),
                order.source().name(),
                order.scriptId(),
                order.reason(),
                order.parentOrderId(),
                order.exitPlanId(),
                order.triggerId(),
                order.ocoGroupId(),
                order.role() == null ? null : order.role().name(),
                order.reduceOnly());
    }
}
