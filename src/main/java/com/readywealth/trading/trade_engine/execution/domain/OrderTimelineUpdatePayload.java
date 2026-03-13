package com.readywealth.trading.trade_engine.execution.domain;

import java.util.List;

public record OrderTimelineUpdatePayload(
        List<String> rowIds,
        List<String> parentOrderIds,
        String reason,
        boolean fullRefresh,
        long tsEpochMs) {
}
