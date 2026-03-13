package com.readywealth.trading.trade_engine.engine.api.http.dto;

import java.util.List;

public record ScriptParamView(
        String name,
        String type,
        boolean required,
        Object defaultValue,
        String description,
        List<String> options) {

    public ScriptParamView {
        options = options == null ? List.of() : List.copyOf(options);
    }

    public ScriptParamView(
            String name,
            String type,
            boolean required,
            Object defaultValue,
            String description) {
        this(name, type, required, defaultValue, description, List.of());
    }
}
