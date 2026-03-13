package com.readywealth.trading.trade_engine.execution.domain;

public record Trigger(
        String triggerId,
        String accountId,
        String exitPlanId,
        long instrumentToken,
        String tradingsymbol,
        String exchange,
        TradingProduct product,
        ExitLegType legType,
        TriggerConditionOp conditionOp,
        double triggerValue,
        double limitOffset,
        TradingSide orderSide,
        int plannedQty,
        TriggerStatus status,
        Double lastObservedLtp,
        Long firedAtEpochMs,
        String brokerTriggerId,
        String ocoGroupId,
        String linkedOrderId,
        String rejectionReason,
        long createdAtEpochMs,
        long updatedAtEpochMs) {

    public Trigger withStatus(TriggerStatus next, Long firedAtEpochMs, String rejectionReason, long updatedAtEpochMs) {
        return new Trigger(
                triggerId,
                accountId,
                exitPlanId,
                instrumentToken,
                tradingsymbol,
                exchange,
                product,
                legType,
                conditionOp,
                triggerValue,
                limitOffset,
                orderSide,
                plannedQty,
                next,
                lastObservedLtp,
                firedAtEpochMs,
                brokerTriggerId,
                ocoGroupId,
                linkedOrderId,
                rejectionReason,
                createdAtEpochMs,
                updatedAtEpochMs);
    }

    public Trigger withLastObservedLtp(Double lastObservedLtp, long updatedAtEpochMs) {
        return new Trigger(
                triggerId,
                accountId,
                exitPlanId,
                instrumentToken,
                tradingsymbol,
                exchange,
                product,
                legType,
                conditionOp,
                triggerValue,
                limitOffset,
                orderSide,
                plannedQty,
                status,
                lastObservedLtp,
                firedAtEpochMs,
                brokerTriggerId,
                ocoGroupId,
                linkedOrderId,
                rejectionReason,
                createdAtEpochMs,
                updatedAtEpochMs);
    }

    public Trigger withLinkedOrder(String linkedOrderId, TriggerStatus status, String rejectionReason, long updatedAtEpochMs) {
        return new Trigger(
                triggerId,
                accountId,
                exitPlanId,
                instrumentToken,
                tradingsymbol,
                exchange,
                product,
                legType,
                conditionOp,
                triggerValue,
                limitOffset,
                orderSide,
                plannedQty,
                status,
                lastObservedLtp,
                firedAtEpochMs,
                brokerTriggerId,
                ocoGroupId,
                linkedOrderId,
                rejectionReason,
                createdAtEpochMs,
                updatedAtEpochMs);
    }
}
