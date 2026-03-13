package com.readywealth.trading.trade_engine.engine.domain.module.graph;

import java.util.List;
import java.util.Map;

import com.readywealth.trading.trade_engine.engine.domain.module.Module;
import com.readywealth.trading.trade_engine.engine.domain.module.spec.ModuleSpec;

public record ModuleGraph(
        List<ModuleSpec<?>> executionOrder,
        Map<ModuleSpec<?>, Module> instances) {
}
