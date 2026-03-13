package com.readywealth.trading.trade_engine.engine.api.http.dto;

import java.time.Instant;

public record RuntimeHealthResponse(
        String state,
        int queueDepth,
        long lastSeq,
        int scriptCount,
        String lastError,
        boolean formingBarExecutionBlocked,
        String bootstrapStatus,
        String bootstrapError,
        Instant bootstrapStartedAt,
        Instant bootstrapCompletedAt,
        Instant bootstrapCutoffTime,
        long bootstrapSeededBars,
        long bootstrapReplayedTicks,
        long bootstrapDroppedTicks) {
}
