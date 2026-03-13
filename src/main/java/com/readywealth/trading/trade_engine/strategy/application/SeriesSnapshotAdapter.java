package com.readywealth.trading.trade_engine.strategy.application;

import com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey;
import com.readywealth.trading.trade_engine.engine.application.runtime.SeriesRuntimeManager;
import com.readywealth.trading.trade_engine.engine.application.runtime.registry.RegistryType;
import com.readywealth.trading.trade_engine.strategy.application.port.SeriesSnapshotPort;
import org.springframework.stereotype.Component;

@Component
public class SeriesSnapshotAdapter implements SeriesSnapshotPort {

    private final SeriesRuntimeManager seriesRuntimeManager;

    public SeriesSnapshotAdapter(SeriesRuntimeManager seriesRuntimeManager) {
        this.seriesRuntimeManager = seriesRuntimeManager;
    }

    @Override
    public Object snapshot(SeriesKey key) {
        return seriesRuntimeManager.snapshot(key, RegistryType.PLOT);
    }
}
