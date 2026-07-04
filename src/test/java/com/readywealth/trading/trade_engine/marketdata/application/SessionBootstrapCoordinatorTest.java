package com.readywealth.trading.trade_engine.marketdata.application;

import com.readywealth.trading.trade_engine.engine.domain.series.Candle;
import com.readywealth.trading.trade_engine.engine.domain.series.CandleCause;
import com.readywealth.trading.trade_engine.engine.domain.series.IntervalKind;
import com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey;
import com.readywealth.trading.trade_engine.marketdata.api.ws.TicksWebSocketBroadcaster;
import com.readywealth.trading.trade_engine.marketdata.application.port.HistoricalCandleClient;
import com.readywealth.trading.trade_engine.marketdata.infrastructure.config.BootstrapProperties;
import com.readywealth.trading.trade_engine.marketdata.infrastructure.config.BootstrapSchedulerProperties;
import com.readywealth.trading.trade_engine.session.infrastructure.memory.InMemoryConcurrentBarSeriesRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.ta4j.core.Bar;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionBootstrapCoordinatorTest {

    @Test
    void orderingViolationDegradesBeforeHistoricalSeedInsert() throws Exception {
        HistoricalCandleClient historicalCandleClient = new HistoricalCandleClient() {
            @Override
            public List<Candle> fetchCandlesInRange(long instrumentToken, IntervalKind intervalKind, Instant from, Instant to) {
                return List.of();
            }

            @Override
            public List<Candle> fetchRecentCandles(long instrumentToken, IntervalKind intervalKind) {
                return List.of();
            }

            @Override
            public List<Candle> fetchRecentCandlesForBootstrap(long instrumentToken, IntervalKind intervalKind) {
                return List.of(candle(
                        instrumentToken,
                        intervalKind,
                        Instant.parse("2026-06-11T03:45:00Z").toEpochMilli(),
                        Instant.parse("2026-06-11T03:50:00Z").toEpochMilli(),
                        100.0));
            }
        };
        InMemoryConcurrentBarSeriesRegistry registry = new InMemoryConcurrentBarSeriesRegistry();
        ObjectProvider<SessionBootstrapCoordinator> none = null;
        @SuppressWarnings("unchecked")
        ObjectProvider<com.readywealth.trading.trade_engine.session.application.SessionSeriesCoordinator> sessionProvider =
                Mockito.mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<com.readywealth.trading.trade_engine.engine.application.runtime.SeriesRuntimeManager> runtimeProvider =
                Mockito.mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<BootstrapTickBufferStore> bufferStoreProvider = Mockito.mock(ObjectProvider.class);
        Mockito.when(sessionProvider.getIfAvailable()).thenReturn(null);
        Mockito.when(runtimeProvider.getIfAvailable()).thenReturn(null);
        Mockito.when(bufferStoreProvider.getIfAvailable()).thenReturn(null);

        BootstrapV2ScopeResolver v2ScopeResolver = Mockito.mock(BootstrapV2ScopeResolver.class);
        Mockito.when(v2ScopeResolver.isV2EnabledForSession(Mockito.anyString())).thenReturn(false);

        SessionBootstrapCoordinator coordinator = new SessionBootstrapCoordinator(
                historicalCandleClient,
                registry,
                sessionProvider,
                Mockito.mock(TicksWebSocketBroadcaster.class),
                runtimeProvider,
                bufferStoreProvider,
                v2ScopeResolver,
                new BootstrapProperties(),
                new BootstrapSchedulerProperties(),
                new SessionCandleBoundaryService("Asia/Kolkata", "09:15", "15:30"),
                new SessionAlignedSeriesIngestor("Asia/Kolkata", "09:15", "15:30"),
                true,
                new SimpleMeterRegistry());

        SeriesKey key = new SeriesKey(738561L, IntervalKind.TIME_5M);
        var entry = registry.acquire(key, 1000).entry();
        entry.seriesHealthState().mark(
                InMemoryConcurrentBarSeriesRegistry.SeriesHealthStatus.FRESH_BOOTSTRAPPING,
                "fresh_series_created",
                InMemoryConcurrentBarSeriesRegistry.SeriesReuseDecision.FRESH_CREATE);
        long runId = entry.bootstrapState().reserve(Instant.now(), false);

        Bar liveBar = entry.series().barBuilder()
                .timePeriod(Duration.ofMinutes(5))
                .beginTime(Instant.parse("2026-06-24T06:30:00Z"))
                .endTime(Instant.parse("2026-06-24T06:35:00Z"))
                .openPrice(120.0)
                .highPrice(121.0)
                .lowPrice(119.0)
                .closePrice(120.5)
                .volume(100.0)
                .amount(12050.0)
                .trades(1L)
                .build();
        entry.series().withWriteLock(() -> entry.series().ingestStreamingBar(liveBar));

        coordinator.bootstrapUiSeriesAsync(key);

        long deadline = System.currentTimeMillis() + 3_000L;
        while (System.currentTimeMillis() < deadline
                && entry.bootstrapState().snapshot().status() == InMemoryConcurrentBarSeriesRegistry.BootstrapStatus.RUNNING) {
            Thread.sleep(25L);
        }

        assertEquals(
                InMemoryConcurrentBarSeriesRegistry.BootstrapStatus.DEGRADED,
                entry.bootstrapState().snapshot().status());
        assertEquals(
                "historical_seed_older_than_existing_series",
                entry.bootstrapState().snapshot().error());
        assertEquals(
                InMemoryConcurrentBarSeriesRegistry.SeriesHealthStatus.DEGRADED,
                entry.seriesHealthState().snapshot().status());
        assertEquals(0L, entry.bootstrapState().snapshot().seededBars());
        assertTrue(entry.series().getEndIndex() >= 0);
    }

    private static Candle candle(long instrumentToken, IntervalKind intervalKind, long start, long end, double close) {
        return new Candle(
                instrumentToken,
                intervalKind,
                start,
                end,
                close - 1,
                close + 1,
                close - 2,
                close,
                100,
                0L,
                1,
                CandleCause.BACKFILL);
    }
}
