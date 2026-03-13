package com.readywealth.trading.trade_engine.engine.domain.module.spec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class StrategyModulePlan {

    private final List<ModuleSpec<?>> rootSpecs;

    private StrategyModulePlan(List<ModuleSpec<?>> rootSpecs) {
        this.rootSpecs = List.copyOf(rootSpecs);
    }

    public static StrategyModulePlan empty() {
        return new StrategyModulePlan(List.of());
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<ModuleSpec<?>> rootSpecs() {
        return rootSpecs;
    }

    public static final class Builder {
        private final List<ModuleSpec<?>> specs = new ArrayList<>();

        public Builder add(ModuleSpec<?> spec) {
            specs.add(spec);
            return this;
        }

        public Builder addAll(Collection<? extends ModuleSpec<?>> values) {
            specs.addAll(values);
            return this;
        }

        public StrategyModulePlan build() {
            return new StrategyModulePlan(specs);
        }
    }
}
