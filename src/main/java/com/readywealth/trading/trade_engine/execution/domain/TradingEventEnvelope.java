package com.readywealth.trading.trade_engine.execution.domain;

public record TradingEventEnvelope(
        String eventId,
        String accountId,
        TradingEventType type,
        long tsEpochMs,
        TradingEventSource source,
        Object payload) {
}
