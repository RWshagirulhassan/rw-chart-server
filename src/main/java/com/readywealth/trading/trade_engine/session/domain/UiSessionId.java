package com.readywealth.trading.trade_engine.session.domain;

import java.util.UUID;

public record UiSessionId(String value) {
    public static UiSessionId newId() {
        return new UiSessionId(UUID.randomUUID().toString());
    }
}
