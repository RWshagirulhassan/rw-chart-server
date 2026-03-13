package com.readywealth.trading.trade_engine.execution.domain;

public record OcoGroup(
        String ocoGroupId,
        String accountId,
        String exitPlanId,
        String triggerAId,
        String triggerBId,
        String triggeredTriggerId,
        OcoGroupStatus status,
        long createdAtEpochMs,
        long updatedAtEpochMs) {

    public OcoGroup withStatus(OcoGroupStatus next, String triggeredTriggerId, long updatedAtEpochMs) {
        return new OcoGroup(
                ocoGroupId,
                accountId,
                exitPlanId,
                triggerAId,
                triggerBId,
                triggeredTriggerId,
                next,
                createdAtEpochMs,
                updatedAtEpochMs);
    }
}
