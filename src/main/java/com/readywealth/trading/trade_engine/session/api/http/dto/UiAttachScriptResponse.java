package com.readywealth.trading.trade_engine.session.api.http.dto;

public record UiAttachScriptResponse(
        String scriptInstanceId,
        String scriptId,
        String kind,
        String executionMode,
        String sessionId,
        String seriesKey,
        String lifecycleState,
        boolean snapshotRequired,
        long attachAcceptedAtEpochMs,
        String bootstrapJobId) {
}
