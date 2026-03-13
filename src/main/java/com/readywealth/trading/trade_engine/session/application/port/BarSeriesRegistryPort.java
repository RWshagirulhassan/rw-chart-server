package com.readywealth.trading.trade_engine.session.application.port;

import com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey;

import java.util.Optional;

public interface BarSeriesRegistryPort {

    RegistryAcquireResult acquire(SeriesKey key, int maxBarCount);

    RegistryReleaseResult release(SeriesKey key);

    Optional<?> get(SeriesKey key);

    record RegistryAcquireResult(boolean created, int refCount) {
    }

    record RegistryReleaseResult(boolean removed, int remainingRefs) {
    }
}
