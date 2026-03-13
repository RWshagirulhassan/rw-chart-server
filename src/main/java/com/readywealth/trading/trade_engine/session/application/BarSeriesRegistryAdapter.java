package com.readywealth.trading.trade_engine.session.application;

import com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey;
import com.readywealth.trading.trade_engine.session.infrastructure.memory.InMemoryConcurrentBarSeriesRegistry;
import com.readywealth.trading.trade_engine.session.application.port.BarSeriesRegistryPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class BarSeriesRegistryAdapter implements BarSeriesRegistryPort {

    private final InMemoryConcurrentBarSeriesRegistry registry;

    public BarSeriesRegistryAdapter(InMemoryConcurrentBarSeriesRegistry registry) {
        this.registry = registry;
    }

    @Override
    public RegistryAcquireResult acquire(SeriesKey key, int maxBarCount) {
        InMemoryConcurrentBarSeriesRegistry.AcquireResult result = registry.acquire(key, maxBarCount);
        return new RegistryAcquireResult(result.created(), result.entry().seriesRefCount().get());
    }

    @Override
    public RegistryReleaseResult release(SeriesKey key) {
        InMemoryConcurrentBarSeriesRegistry.ReleaseResult result = registry.release(key);
        return new RegistryReleaseResult(result.removed(), result.remainingRefs());
    }

    @Override
    public Optional<?> get(SeriesKey key) {
        return registry.get(key);
    }
}
