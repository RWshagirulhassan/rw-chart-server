package com.readywealth.trading.trade_engine.strategy.application.port;

import com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey;

public interface SeriesSnapshotPort {
    Object snapshot(SeriesKey key);
}
