package com.readywealth.trading.trade_engine.marketdata.application;

import com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey;

import java.util.List;

public interface BootstrapTickBufferStore {

    record BufferedBootstrapTick(
            long ingestSeq,
            long timestampMs,
            double price,
            double volume,
            long receivedAtMs,
            long runId) {
    }

    void append(SeriesKey key, long runId, long timestampMs, double price, double volume, long receivedAtMs);

    List<BufferedBootstrapTick> readAll(SeriesKey key, long runId);

    long size(SeriesKey key);

    void purge(SeriesKey key);
}
