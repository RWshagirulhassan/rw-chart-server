package com.readywealth.trading.trade_engine.execution.domain;

public record TriggerOrderAttempt(
        String triggerId,
        String orderId,
        TriggerOrderAttemptStatus status,
        String rejectionReason,
        long createdAtEpochMs,
        long updatedAtEpochMs) {
}
