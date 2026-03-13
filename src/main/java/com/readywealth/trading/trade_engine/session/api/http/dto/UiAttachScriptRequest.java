package com.readywealth.trading.trade_engine.session.api.http.dto;

import java.util.Map;

public record UiAttachScriptRequest(
        String scriptId,
        Map<String, Object> params,
        String executionMode) {
}
