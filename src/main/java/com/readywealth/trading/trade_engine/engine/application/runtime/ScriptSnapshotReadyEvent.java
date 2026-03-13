package com.readywealth.trading.trade_engine.engine.application.runtime;

public record ScriptSnapshotReadyEvent(
        String ownerSessionId,
        String seriesKey,
        String scriptInstanceId,
        String bootstrapJobId,
        long snapshotCursorSeq,
        long startedAtEpochMs,
        long completedAtEpochMs,
        ScriptBootstrapStatus status,
        String error) {
}
