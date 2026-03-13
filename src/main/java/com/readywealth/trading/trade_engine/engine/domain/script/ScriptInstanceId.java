package com.readywealth.trading.trade_engine.engine.domain.script;

import java.util.UUID;

public record ScriptInstanceId(String value) {
    public static ScriptInstanceId newId() {
        return new ScriptInstanceId(UUID.randomUUID().toString());
    }
}
