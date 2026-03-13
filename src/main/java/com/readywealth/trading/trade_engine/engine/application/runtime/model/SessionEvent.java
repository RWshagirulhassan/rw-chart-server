package com.readywealth.trading.trade_engine.engine.application.runtime.model;

public record SessionEvent(
        long seq,
        long timestampEpochMs,
        String type,
        Object payload) {
}
