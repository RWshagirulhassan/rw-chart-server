package com.readywealth.trading.trade_engine.execution.api.http.dto;

import com.readywealth.trading.trade_engine.execution.domain.Trigger;

public record TriggerResponse(
        String triggerId,
        String accountId,
        String exitPlanId,
        long instrumentToken,
        String tradingsymbol,
        String exchange,
        String product,
        String legType,
        String conditionOp,
        double triggerValue,
        double limitOffset,
        String orderSide,
        int plannedQty,
        String status,
        Double lastObservedLtp,
        Long firedAtEpochMs,
        String brokerTriggerId,
        String ocoGroupId,
        String linkedOrderId,
        String rejectionReason,
        long createdAtEpochMs,
        long updatedAtEpochMs) {

    public static TriggerResponse from(Trigger trigger) {
        return new TriggerResponse(
                trigger.triggerId(),
                trigger.accountId(),
                trigger.exitPlanId(),
                trigger.instrumentToken(),
                trigger.tradingsymbol(),
                trigger.exchange(),
                trigger.product().name(),
                trigger.legType().name(),
                trigger.conditionOp().name(),
                trigger.triggerValue(),
                trigger.limitOffset(),
                trigger.orderSide().name(),
                trigger.plannedQty(),
                trigger.status().name(),
                trigger.lastObservedLtp(),
                trigger.firedAtEpochMs(),
                trigger.brokerTriggerId(),
                trigger.ocoGroupId(),
                trigger.linkedOrderId(),
                trigger.rejectionReason(),
                trigger.createdAtEpochMs(),
                trigger.updatedAtEpochMs());
    }
}
