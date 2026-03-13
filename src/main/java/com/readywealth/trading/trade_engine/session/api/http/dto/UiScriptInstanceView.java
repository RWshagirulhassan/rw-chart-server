package com.readywealth.trading.trade_engine.session.api.http.dto;

public record UiScriptInstanceView(
        String scriptInstanceId,
        String scriptId,
        String kind,
        String executionMode,
        String lifecycleState,
        String ownerSessionId,
        String ownerUserId) {
}
