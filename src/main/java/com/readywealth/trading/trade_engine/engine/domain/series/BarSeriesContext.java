package com.readywealth.trading.trade_engine.engine.domain.series;

import org.ta4j.core.BarSeries;
import org.ta4j.core.num.NumFactory;

import com.readywealth.trading.trade_engine.engine.domain.module.factory.ModuleFactoryRegistry;
import com.readywealth.trading.trade_engine.engine.domain.module.spec.StrategyModulePlan;

public final class BarSeriesContext {

    private final SeriesKey key;
    private final BarSeries series;
    private final NumFactory numFactory;
    private final SeriesConfig config;
    private volatile Context context;
    private final ModuleFactoryRegistry factoryRegistry;

    public BarSeriesContext(SeriesKey key, BarSeries series, NumFactory numFactory, SeriesConfig config) {
        this(key, series, numFactory, config, StrategyModulePlan.empty(), ModuleFactoryRegistry.defaultRegistry());
    }

    public BarSeriesContext(
            SeriesKey key,
            BarSeries series,
            NumFactory numFactory,
            SeriesConfig config,
            StrategyModulePlan modulePlan) {
        this(key, series, numFactory, config, modulePlan, ModuleFactoryRegistry.defaultRegistry());
    }

    public BarSeriesContext(
            SeriesKey key,
            BarSeries series,
            NumFactory numFactory,
            SeriesConfig config,
            StrategyModulePlan modulePlan,
            ModuleFactoryRegistry factoryRegistry) {
        this.key = key;
        this.series = series;
        this.numFactory = numFactory;
        this.config = config;
        this.factoryRegistry = factoryRegistry;
        this.context = new Context(this, modulePlan, factoryRegistry);
    }

    public SeriesKey key() {
        return key;
    }

    public BarSeries series() {
        return series;
    }

    public NumFactory numFactory() {
        return numFactory;
    }

    public SeriesConfig config() {
        return config;
    }

    public Context context() {
        return context;
    }

    public void rebuildContext(StrategyModulePlan modulePlan) {
        this.context = new Context(this, modulePlan, factoryRegistry);
    }
}
