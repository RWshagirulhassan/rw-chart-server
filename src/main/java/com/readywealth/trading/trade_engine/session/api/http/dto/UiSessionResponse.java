package com.readywealth.trading.trade_engine.session.api.http.dto;

import com.readywealth.trading.trade_engine.session.domain.SessionState;

import java.time.Instant;
import java.util.List;

public record UiSessionResponse(
        String sessionId,
        String userId,
        boolean destroyOnClose,
        List<String> seriesKeys,
        Instant createdAt,
        Instant updatedAt,
        Instant lastSeenAt,
        int connectedClients,
        SessionState state,
        String bootstrapStatus,
        Instant bootstrapStartedAt,
        Instant bootstrapCompletedAt,
        Instant bootstrapCutoffTime,
        String bootstrapError,
        long bootstrapSeededBars,
        long bootstrapReplayedTicks,
        long bootstrapDroppedTicks) {
}
