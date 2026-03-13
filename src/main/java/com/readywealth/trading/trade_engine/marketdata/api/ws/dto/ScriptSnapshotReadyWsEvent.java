package com.readywealth.trading.trade_engine.marketdata.api.ws.dto;

public record ScriptSnapshotReadyWsEvent(
        String sessionId,
        String seriesKey,
        String scriptInstanceId,
        String bootstrapJobId,
        long snapshotCursorSeq,
        long startedAtEpochMs,
        long completedAtEpochMs,
        String status,
        String error) {
}
