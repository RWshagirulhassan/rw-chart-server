package com.readywealth.trading.trade_engine.engine.application.runtime;

import com.readywealth.trading.trade_engine.engine.application.runtime.model.DeltaResponse;

public record ScriptSnapshotAckResult(
        boolean activated,
        long replayedEvents,
        Long fromSeq,
        Long toSeq,
        DeltaResponse replayDeltas) {
}
