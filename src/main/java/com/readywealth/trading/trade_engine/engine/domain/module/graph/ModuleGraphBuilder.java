package com.readywealth.trading.trade_engine.engine.domain.module.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.readywealth.trading.trade_engine.engine.domain.module.Module;
import com.readywealth.trading.trade_engine.engine.domain.module.factory.ModuleFactory;
import com.readywealth.trading.trade_engine.engine.domain.module.factory.ModuleFactoryRegistry;
import com.readywealth.trading.trade_engine.engine.domain.module.spec.ModuleSpec;

public final class ModuleGraphBuilder {

    private enum VisitState {
        VISITING,
        DONE
    }

    public ModuleGraph build(List<ModuleSpec<?>> rootSpecs, ModuleFactoryRegistry registry) {
        Objects.requireNonNull(rootSpecs, "rootSpecs");
        Objects.requireNonNull(registry, "registry");

        Map<ModuleSpec<?>, VisitState> visitState = new HashMap<>();
        List<ModuleSpec<?>> orderedSpecs = new ArrayList<>();
        for (ModuleSpec<?> root : rootSpecs) {
            visit(root, registry, visitState, orderedSpecs, new ArrayList<>());
        }

        Map<ModuleSpec<?>, Module> instances = new LinkedHashMap<>();
        DependencyResolverImpl resolver = new DependencyResolverImpl(instances);
        for (ModuleSpec<?> spec : orderedSpecs) {
            ModuleFactory<?, ?> factory = registry.getFactory(spec);
            Module module = factory.createUnchecked(spec, resolver);
            instances.put(spec, module);
        }

        return new ModuleGraph(List.copyOf(orderedSpecs), Map.copyOf(instances));
    }

    private void visit(
            ModuleSpec<?> spec,
            ModuleFactoryRegistry registry,
            Map<ModuleSpec<?>, VisitState> visitState,
            List<ModuleSpec<?>> orderedSpecs,
            List<ModuleSpec<?>> stack) {
        VisitState state = visitState.get(spec);
        if (state == VisitState.DONE) {
            return;
        }
        if (state == VisitState.VISITING) {
            throw new IllegalStateException("Cycle detected in module dependency graph: " + stack + " -> " + spec);
        }

        visitState.put(spec, VisitState.VISITING);
        stack.add(spec);

        ModuleFactory<?, ?> factory = registry.getFactory(spec);
        for (ModuleSpec<?> dep : factory.dependenciesUnchecked(spec)) {
            visit(dep, registry, visitState, orderedSpecs, stack);
        }

        stack.remove(stack.size() - 1);
        visitState.put(spec, VisitState.DONE);
        orderedSpecs.add(spec);
    }
}
