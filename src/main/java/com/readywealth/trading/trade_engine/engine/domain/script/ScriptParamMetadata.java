package com.readywealth.trading.trade_engine.engine.domain.script;

import java.util.List;

public record ScriptParamMetadata(
        String name,
        String type,
        boolean required,
        Object defaultValue,
        String description,
        List<String> options) {

    public ScriptParamMetadata {
        options = options == null ? List.of() : List.copyOf(options);
    }

    public ScriptParamMetadata(
            String name,
            String type,
            boolean required,
            Object defaultValue,
            String description) {
        this(name, type, required, defaultValue, description, List.of());
    }
}
