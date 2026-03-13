package com.readywealth.trading.trade_engine.engine.api.http.dto;

public record AttachScriptResponse(
        String scriptInstanceId,
        String scriptId,
        String kind,
        String executionMode,
        String lifecycleState,
        boolean snapshotRequired,
        long attachAcceptedAtEpochMs,
        String bootstrapJobId) {

    public AttachScriptResponse(String scriptInstanceId, String scriptId, String kind) {
        this(scriptInstanceId, scriptId, kind, null, null, true, System.currentTimeMillis(), null);
    }
}
