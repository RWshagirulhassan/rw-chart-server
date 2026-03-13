package com.readywealth.trading.trade_engine.engine.domain.module.factory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.readywealth.trading.trade_engine.engine.domain.module.spec.ModuleSpec;

public final class ModuleFactoryRegistry {

    private static final ModuleFactoryRegistry DEFAULT = new ModuleFactoryRegistry(
            List.of(new SwingPivotsFactory(), new BosChochFactory()));

    private final Map<Class<?>, ModuleFactory<?, ?>> factoriesBySpecType;

    public ModuleFactoryRegistry(List<ModuleFactory<?, ?>> factories) {
        Objects.requireNonNull(factories, "factories");
        Map<Class<?>, ModuleFactory<?, ?>> map = new HashMap<>();
        for (ModuleFactory<?, ?> factory : factories) {
            ModuleFactory<?, ?> existing = map.putIfAbsent(factory.specType(), factory);
            if (existing != null) {
                throw new IllegalStateException("Duplicate ModuleFactory for spec type: " + factory.specType().getName());
            }
        }
        this.factoriesBySpecType = Map.copyOf(map);
    }

    public static ModuleFactoryRegistry defaultRegistry() {
        return DEFAULT;
    }

    public ModuleFactory<?, ?> getFactory(ModuleSpec<?> spec) {
        ModuleFactory<?, ?> factory = factoriesBySpecType.get(spec.getClass());
        if (factory == null) {
            throw new IllegalStateException("No ModuleFactory registered for spec type: " + spec.getClass().getName());
        }
        return factory;
    }
}
