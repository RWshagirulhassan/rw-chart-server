package com.readywealth.trading.trade_engine.engine.domain.module.factory;

import java.util.List;

import com.readywealth.trading.trade_engine.engine.domain.module.Module;
import com.readywealth.trading.trade_engine.engine.domain.module.spec.ModuleSpec;

public interface ModuleFactory<T extends Module, S extends ModuleSpec<T>> {

    Class<S> specType();

    default List<ModuleSpec<?>> dependencies(S spec) {
        return List.of();
    }

    T create(S spec, DependencyResolver resolver);

    default List<ModuleSpec<?>> dependenciesUnchecked(ModuleSpec<?> spec) {
        return dependencies(specType().cast(spec));
    }

    default Module createUnchecked(ModuleSpec<?> spec, DependencyResolver resolver) {
        return create(specType().cast(spec), resolver);
    }
}
