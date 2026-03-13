package com.readywealth.trading.trade_engine.session.api.http.dto;

public record ScriptSnapshotAckResponse(
        boolean activated,
        long replayedEvents,
        Long fromSeq,
        Long toSeq) {
}
