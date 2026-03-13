package com.readywealth.trading.trade_engine.execution.infrastructure.marketdata;

import com.readywealth.trading.trade_engine.execution.application.port.LastPricePort;
import com.readywealth.trading.trade_engine.execution.domain.PriceSnapshot;
import com.readywealth.trading.trade_engine.session.infrastructure.memory.InMemoryConcurrentBarSeriesRegistry;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class SeriesLastPriceAdapter implements LastPricePort {

    private final InMemoryConcurrentBarSeriesRegistry registry;

    public SeriesLastPriceAdapter(InMemoryConcurrentBarSeriesRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Optional<PriceSnapshot> getPriceSnapshot(long instrumentToken) {
        List<InMemoryConcurrentBarSeriesRegistry.SeriesEntry> entries = registry.entriesForInstrument(instrumentToken);
        if (entries == null || entries.isEmpty()) {
            return Optional.empty();
        }

        InMemoryConcurrentBarSeriesRegistry.SeriesEntry latestEntry = entries.stream()
                .max(Comparator.comparingLong(item -> item.lastIngestTimestampMs().get()))
                .orElse(null);
        if (latestEntry == null) {
            return Optional.empty();
        }

        var series = latestEntry.series();
        int end = series.getEndIndex();
        int begin = series.getBeginIndex();
        if (end < 0 || begin < 0 || end < begin) {
            return Optional.empty();
        }

        var bar = series.getBar(end);
        long asOf = latestEntry.lastIngestTimestampMs().get();
        boolean tickBased = asOf > 0 && (System.currentTimeMillis() - asOf) < 4000L;
        return Optional.of(new PriceSnapshot(
                instrumentToken,
                bar.getClosePrice().doubleValue(),
                bar.getOpenPrice().doubleValue(),
                bar.getHighPrice().doubleValue(),
                bar.getLowPrice().doubleValue(),
                asOf,
                tickBased));
    }
}
