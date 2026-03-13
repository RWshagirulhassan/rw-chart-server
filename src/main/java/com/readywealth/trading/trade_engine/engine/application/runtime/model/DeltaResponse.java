package com.readywealth.trading.trade_engine.engine.application.runtime.model;

import java.util.List;

public record DeltaResponse(
        long nextSeq,
        List<SessionEvent> events) {
}
