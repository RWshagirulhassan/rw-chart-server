package com.readywealth.trading.trade_engine.marketdata.api.ws.dto;

public record ScriptAttachCatchupWsEvent(
        String sessionId,
        String seriesKey,
        String scriptInstanceId,
        Integer completedUpToIndex,
        long ts) {
}
