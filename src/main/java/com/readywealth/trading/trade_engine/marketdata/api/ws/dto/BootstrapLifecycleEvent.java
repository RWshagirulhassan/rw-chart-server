package com.readywealth.trading.trade_engine.marketdata.api.ws.dto;

import java.time.Instant;

public record BootstrapLifecycleEvent(
        String sessionId,
        String seriesKey,
        String status,
        String seriesHealthStatus,
        String seriesHealthReason,
        String seriesReuseDecision,
        Instant cutoffAt,
        long seededBars,
        long replayedTicks,
        long droppedTicks,
        String error,
        long snapshotCursor,
        Instant asOf) {
}
