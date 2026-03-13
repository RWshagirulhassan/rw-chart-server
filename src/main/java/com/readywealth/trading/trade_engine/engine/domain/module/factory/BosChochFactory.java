package com.readywealth.trading.trade_engine.engine.domain.module.factory;

import java.util.List;

import com.readywealth.trading.trade_engine.engine.domain.module.boschoch.BosChochModule;
import com.readywealth.trading.trade_engine.engine.domain.module.swingpivots.SwingPivotsModule;
import com.readywealth.trading.trade_engine.engine.domain.module.spec.BosChochSpec;
import com.readywealth.trading.trade_engine.engine.domain.module.spec.ModuleSpec;
import com.readywealth.trading.trade_engine.engine.domain.module.spec.SwingPivotsSpec;

public final class BosChochFactory implements ModuleFactory<BosChochModule, BosChochSpec> {

    @Override
    public Class<BosChochSpec> specType() {
        return BosChochSpec.class;
    }

    @Override
    public List<ModuleSpec<?>> dependencies(BosChochSpec spec) {
        return List.of(new SwingPivotsSpec(spec.len()));
    }

    @Override
    public BosChochModule create(BosChochSpec spec, DependencyResolver resolver) {
        SwingPivotsModule swings = resolver.require(new SwingPivotsSpec(spec.len()));
        return new BosChochModule(spec.len(), swings);
    }
}
