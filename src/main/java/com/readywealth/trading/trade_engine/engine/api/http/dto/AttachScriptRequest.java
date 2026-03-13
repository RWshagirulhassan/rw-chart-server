package com.readywealth.trading.trade_engine.engine.api.http.dto;

import java.util.Map;

public record AttachScriptRequest(
        String scriptId,
        Map<String, Object> params,
        String executionMode) {
}
