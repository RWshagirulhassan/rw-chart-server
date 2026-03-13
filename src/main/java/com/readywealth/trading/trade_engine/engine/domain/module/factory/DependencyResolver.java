package com.readywealth.trading.trade_engine.engine.domain.module.factory;

import com.readywealth.trading.trade_engine.engine.domain.module.Module;
import com.readywealth.trading.trade_engine.engine.domain.module.spec.ModuleSpec;

public interface DependencyResolver {
    <M extends Module> M require(ModuleSpec<M> spec);
}
