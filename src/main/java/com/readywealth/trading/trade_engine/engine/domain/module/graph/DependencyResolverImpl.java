package com.readywealth.trading.trade_engine.engine.domain.module.graph;

import java.util.Map;

import com.readywealth.trading.trade_engine.engine.domain.module.Module;
import com.readywealth.trading.trade_engine.engine.domain.module.factory.DependencyResolver;
import com.readywealth.trading.trade_engine.engine.domain.module.spec.ModuleSpec;

final class DependencyResolverImpl implements DependencyResolver {

    private final Map<ModuleSpec<?>, Module> instances;

    DependencyResolverImpl(Map<ModuleSpec<?>, Module> instances) {
        this.instances = instances;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <M extends Module> M require(ModuleSpec<M> spec) {
        Module module = instances.get(spec);
        if (module == null) {
            throw new IllegalStateException("Dependency module is not initialized: " + spec);
        }
        return (M) module;
    }
}
