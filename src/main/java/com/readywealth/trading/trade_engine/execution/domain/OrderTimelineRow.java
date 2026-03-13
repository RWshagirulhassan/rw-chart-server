package com.readywealth.trading.trade_engine.execution.domain;

public record OrderTimelineRow(
        String rowId,
        OrderTimelineRowType rowType,
        String parentOrderId,
        String entryOrderId,
        String exitPlanId,
        String triggerId,
        String linkedOrderId,
        ExitLegType legType,
        long instrumentToken,
        String tradingsymbol,
        String exchange,
        TradingSide side,
        int qty,
        TradingOrderType orderType,
        Double price,
        Double triggerPrice,
        Double limitOffset,
        OrderTimelineStatus status,
        String statusReason,
        long createdAtEpochMs,
        long updatedAtEpochMs,
        long sortTsEpochMs) {
}
