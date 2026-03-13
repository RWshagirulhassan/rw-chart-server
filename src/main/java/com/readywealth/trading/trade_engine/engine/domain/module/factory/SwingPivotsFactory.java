package com.readywealth.trading.trade_engine.engine.domain.module.factory;

import com.readywealth.trading.trade_engine.engine.domain.module.swingpivots.SwingPivotsModule;
import com.readywealth.trading.trade_engine.engine.domain.module.spec.SwingPivotsSpec;

public final class SwingPivotsFactory implements ModuleFactory<SwingPivotsModule, SwingPivotsSpec> {

    @Override
    public Class<SwingPivotsSpec> specType() {
        return SwingPivotsSpec.class;
    }

    @Override
    public SwingPivotsModule create(SwingPivotsSpec spec, DependencyResolver resolver) {
        return new SwingPivotsModule(spec.len());
    }
}
