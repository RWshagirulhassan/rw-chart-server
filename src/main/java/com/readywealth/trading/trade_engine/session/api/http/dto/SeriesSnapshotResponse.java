package com.readywealth.trading.trade_engine.session.api.http.dto;

import java.util.List;

public record SeriesSnapshotResponse(
        String seriesKey,
        int beginIndex,
        int endIndex,
        int maxBarCount,
        int refCount,
        long lastSeq,
        long snapshotCursor,
        java.time.Instant asOf,
        String state,
        List<SeriesBarView> bars,
        String bootstrapStatus,
        java.time.Instant bootstrapStartedAt,
        java.time.Instant bootstrapCompletedAt,
        java.time.Instant bootstrapCutoffTime,
        String bootstrapError,
        long bootstrapSeededBars,
        long bootstrapReplayedTicks,
        long bootstrapDroppedTicks) {
}
