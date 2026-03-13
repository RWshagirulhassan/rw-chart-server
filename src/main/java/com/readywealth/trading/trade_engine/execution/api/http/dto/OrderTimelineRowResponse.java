package com.readywealth.trading.trade_engine.execution.api.http.dto;

import com.readywealth.trading.trade_engine.execution.domain.ExitLegType;
import com.readywealth.trading.trade_engine.execution.domain.OrderTimelineRow;

public record OrderTimelineRowResponse(
        String rowId,
        String rowType,
        String parentOrderId,
        String entryOrderId,
        String exitPlanId,
        String triggerId,
        String linkedOrderId,
        String legType,
        long instrumentToken,
        String tradingsymbol,
        String exchange,
        String side,
        int qty,
        String orderType,
        Double price,
        Double triggerPrice,
        Double limitOffset,
        String status,
        String statusReason,
        long createdAtEpochMs,
        long updatedAtEpochMs,
        long sortTsEpochMs) {

    public static OrderTimelineRowResponse from(OrderTimelineRow row) {
        return new OrderTimelineRowResponse(
                row.rowId(),
                row.rowType().name(),
                row.parentOrderId(),
                row.entryOrderId(),
                row.exitPlanId(),
                row.triggerId(),
                row.linkedOrderId(),
                mapLegType(row.legType()),
                row.instrumentToken(),
                row.tradingsymbol(),
                row.exchange(),
                row.side() == null ? null : row.side().name(),
                row.qty(),
                row.orderType() == null ? null : row.orderType().name(),
                row.price(),
                row.triggerPrice(),
                row.limitOffset(),
                row.status().name(),
                row.statusReason(),
                row.createdAtEpochMs(),
                row.updatedAtEpochMs(),
                row.sortTsEpochMs());
    }

    private static String mapLegType(ExitLegType legType) {
        if (legType == null) {
            return null;
        }
        return legType == ExitLegType.TAKE_PROFIT ? "TP" : "SL";
    }
}
