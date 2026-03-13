package com.readywealth.trading.trade_engine.engine.domain.script;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class ScriptCatalog {

    private final Map<ScriptId, ScriptDefinition> definitions = new LinkedHashMap<>();

    public ScriptCatalog(List<ScriptDefinition> definitions) {
        for (ScriptDefinition definition : definitions) {
            register(definition);
        }
    }

    public synchronized void register(ScriptDefinition definition) {
        if (definitions.containsKey(definition.id())) {
            throw new IllegalStateException("Duplicate script id: " + definition.id().value());
        }
        definitions.put(definition.id(), definition);
    }

    public synchronized Optional<ScriptDefinition> get(ScriptId scriptId) {
        return Optional.ofNullable(definitions.get(scriptId));
    }

    public synchronized Collection<ScriptDefinition> list() {
        return List.copyOf(definitions.values());
    }
}
