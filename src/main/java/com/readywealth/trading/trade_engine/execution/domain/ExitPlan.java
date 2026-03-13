package com.readywealth.trading.trade_engine.execution.domain;

public record ExitPlan(
        String exitPlanId,
        String accountId,
        String entryOrderId,
        long instrumentToken,
        String tradingsymbol,
        String exchange,
        TradingProduct product,
        TradingSide entrySide,
        int plannedQty,
        ExitPlanStatus status,
        ArmPolicy armPolicy,
        ExitPlanMode mode,
        String ocoGroupId,
        ExitLegSpec takeProfit,
        ExitLegSpec stopLoss,
        Integer rearmRequiredQty,
        String rearmReason,
        long createdAtEpochMs,
        long updatedAtEpochMs) {

    public ExitPlan withStatus(ExitPlanStatus next, Integer rearmRequiredQty, String rearmReason, long updatedAtEpochMs) {
        return new ExitPlan(
                exitPlanId,
                accountId,
                entryOrderId,
                instrumentToken,
                tradingsymbol,
                exchange,
                product,
                entrySide,
                plannedQty,
                next,
                armPolicy,
                mode,
                ocoGroupId,
                takeProfit,
                stopLoss,
                rearmRequiredQty,
                rearmReason,
                createdAtEpochMs,
                updatedAtEpochMs);
    }

    public ExitPlan withOcoGroupId(String ocoGroupId, long updatedAtEpochMs) {
        return new ExitPlan(
                exitPlanId,
                accountId,
                entryOrderId,
                instrumentToken,
                tradingsymbol,
                exchange,
                product,
                entrySide,
                plannedQty,
                status,
                armPolicy,
                mode,
                ocoGroupId,
                takeProfit,
                stopLoss,
                rearmRequiredQty,
                rearmReason,
                createdAtEpochMs,
                updatedAtEpochMs);
    }

    public ExitPlan withLegs(ExitLegSpec takeProfit, ExitLegSpec stopLoss, ExitPlanMode mode, long updatedAtEpochMs) {
        return new ExitPlan(
                exitPlanId,
                accountId,
                entryOrderId,
                instrumentToken,
                tradingsymbol,
                exchange,
                product,
                entrySide,
                plannedQty,
                status,
                armPolicy,
                mode,
                ocoGroupId,
                takeProfit,
                stopLoss,
                rearmRequiredQty,
                rearmReason,
                createdAtEpochMs,
                updatedAtEpochMs);
    }
}
