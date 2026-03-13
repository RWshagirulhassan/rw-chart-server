package com.readywealth.trading.trade_engine.engine.domain.series;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.readywealth.trading.trade_engine.engine.domain.module.Module;
import com.readywealth.trading.trade_engine.engine.domain.module.factory.ModuleFactoryRegistry;
import com.readywealth.trading.trade_engine.engine.domain.module.graph.ModuleGraph;
import com.readywealth.trading.trade_engine.engine.domain.module.graph.ModuleGraphBuilder;
import com.readywealth.trading.trade_engine.engine.domain.module.spec.ModuleSpec;
import com.readywealth.trading.trade_engine.engine.domain.module.spec.StrategyModulePlan;

public final class Context {

    private final BarSeriesContext owner;
    private final Map<ModuleSpec<?>, Module> instances;
    private final List<ModuleSpec<?>> executionOrder;

    private int computedUpTo = Integer.MIN_VALUE;

    public Context(BarSeriesContext owner, StrategyModulePlan plan, ModuleFactoryRegistry registry) {
        this.owner = Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(registry, "registry");

        ModuleGraph graph = new ModuleGraphBuilder().build(plan.rootSpecs(), registry);
        this.instances = graph.instances();
        this.executionOrder = graph.executionOrder();
    }

    public <M extends Module> M require(ModuleSpec<M> spec) {
        Module module = instances.get(spec);
        if (module == null) {
            throw new IllegalStateException("Module not found in this context: " + spec);
        }
        @SuppressWarnings("unchecked")
        M typed = (M) module;
        return typed;
    }

    public void ensureComputedUpTo(int endIndex) {
        if (executionOrder.isEmpty()) {
            computedUpTo = Math.max(computedUpTo, endIndex);
            return;
        }

        if (endIndex <= computedUpTo) {
            return;
        }

        int beginIndex = owner.series().getBeginIndex();
        int start = Math.max(beginIndex, computedUpTo + 1);
        for (int index = start; index <= endIndex; index++) {
            for (ModuleSpec<?> spec : executionOrder) {
                instances.get(spec).onBar(owner, index);
            }
        }
        computedUpTo = endIndex;
    }

    public void onEvict(int newBeginIndex) {
        for (ModuleSpec<?> spec : executionOrder) {
            instances.get(spec).onEvict(owner, newBeginIndex);
        }
    }

    public void invalidateFrom(int index) {
        for (ModuleSpec<?> spec : executionOrder) {
            instances.get(spec).onInvalidateFrom(owner, index);
        }
        computedUpTo = Math.min(computedUpTo, index - 1);
    }
}
