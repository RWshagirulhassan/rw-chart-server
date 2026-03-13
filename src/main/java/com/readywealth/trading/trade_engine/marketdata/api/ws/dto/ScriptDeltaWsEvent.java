package com.readywealth.trading.trade_engine.marketdata.api.ws.dto;

public record ScriptDeltaWsEvent(
        String sessionId,
        String seriesKey,
        long seq,
        String eventType,
        String scriptInstanceId,
        Object payload,
        String registryType) {
}
