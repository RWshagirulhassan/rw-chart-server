package com.readywealth.trading.trade_engine.session.api.http.dto;

import java.util.Map;

public record UiReplaceScriptRequest(
        Map<String, Object> params,
        String executionMode) {
}
