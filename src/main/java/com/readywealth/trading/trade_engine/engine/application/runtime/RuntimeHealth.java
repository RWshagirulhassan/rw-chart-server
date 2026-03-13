package com.readywealth.trading.trade_engine.engine.application.runtime;

public record RuntimeHealth(
        SeriesRuntimeState state,
        int queueDepth,
        long lastSeq,
        int scriptCount,
        String lastError,
        boolean formingBarExecutionBlocked) {
}
