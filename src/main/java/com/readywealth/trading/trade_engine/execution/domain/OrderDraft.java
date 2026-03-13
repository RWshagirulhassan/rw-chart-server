package com.readywealth.trading.trade_engine.execution.domain;

public record OrderDraft(
        String accountId,
        long instrumentToken,
        String tradingsymbol,
        String exchange,
        TradingProduct product,
        TradingOrderType orderType,
        TradingSide side,
        int qty,
        Double price,
        Double triggerPrice,
        TradingEventSource source,
        String scriptId,
        String reason,
        String parentOrderId,
        String exitPlanId,
        String triggerId,
        String ocoGroupId,
        TradingOrderRole role,
        boolean reduceOnly) {

    public OrderDraft(
            String accountId,
            long instrumentToken,
            String tradingsymbol,
            String exchange,
            TradingProduct product,
            TradingOrderType orderType,
            TradingSide side,
            int qty,
            Double price,
            Double triggerPrice,
            TradingEventSource source,
            String scriptId,
            String reason) {
        this(
                accountId,
                instrumentToken,
                tradingsymbol,
                exchange,
                product,
                orderType,
                side,
                qty,
                price,
                triggerPrice,
                source,
                scriptId,
                reason,
                null,
                null,
                null,
                null,
                TradingOrderRole.ENTRY,
                false);
    }
}
