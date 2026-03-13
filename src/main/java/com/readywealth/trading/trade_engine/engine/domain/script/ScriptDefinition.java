package com.readywealth.trading.trade_engine.engine.domain.script;

import java.util.List;

import com.readywealth.trading.trade_engine.engine.domain.module.spec.ModuleSpec;

public interface ScriptDefinition {
    ScriptId id();

    default String name() {
        String idValue = id() == null ? "" : id().value();
        if (idValue == null || idValue.isBlank()) {
            return "Unnamed Script";
        }
        int lastDot = idValue.lastIndexOf('.');
        String token = lastDot >= 0 ? idValue.substring(lastDot + 1) : idValue;
        String[] parts = token.replace('-', ' ').replace('_', ' ').trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                out.append(part.substring(1).toLowerCase());
            }
        }
        return out.isEmpty() ? "Unnamed Script" : out.toString();
    }

    ScriptKind kind();

    ScriptParamsBinder<?> paramsBinder();

    default String description() {
        return "";
    }

    default List<ScriptParamMetadata> paramsMetadata() {
        return List.of();
    }

    default List<ModuleSpec<?>> requiredModules(Object typedParams) {
        return List.of();
    }

    ScriptRuntime createRuntime(ScriptInitContext init, Object typedParams);
}
