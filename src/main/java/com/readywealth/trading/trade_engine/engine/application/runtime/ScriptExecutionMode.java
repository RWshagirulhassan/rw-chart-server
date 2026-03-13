package com.readywealth.trading.trade_engine.engine.application.runtime;

public enum ScriptExecutionMode {
    ON_TICK,
    ON_CANDLE_CLOSE;

    public static ScriptExecutionMode fromNullable(String raw, ScriptExecutionMode fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return ScriptExecutionMode.valueOf(raw.trim().toUpperCase());
    }
}
