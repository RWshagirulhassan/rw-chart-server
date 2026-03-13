package com.readywealth.trading.trade_engine.engine.domain.script;

import com.readywealth.trading.trade_engine.engine.domain.series.BarSeriesContext;
import com.readywealth.trading.trade_engine.engine.application.runtime.model.TradeIntent;
import com.readywealth.trading.trade_engine.engine.application.runtime.registry.RegistryHub;

public final class ScriptExecutionContext {

    private final BarSeriesContext seriesContext;
    private final RegistryHub.Writer registryWriter;

    public ScriptExecutionContext(BarSeriesContext seriesContext, RegistryHub.Writer registryWriter) {
        this.seriesContext = seriesContext;
        this.registryWriter = registryWriter;
    }

    public BarSeriesContext seriesContext() {
        return seriesContext;
    }

    public RegistryHub.Writer registries() {
        return registryWriter;
    }

    public void emitIntent(TradeIntent intent) {
        registryWriter.emitIntent(intent);
    }
}
