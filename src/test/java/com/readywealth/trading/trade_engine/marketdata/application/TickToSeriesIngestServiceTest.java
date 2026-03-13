package com.readywealth.trading.trade_engine.marketdata.application;

import com.readywealth.trading.trade_engine.engine.domain.series.IntervalKind;
import com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey;
import com.readywealth.trading.trade_engine.session.application.SessionSeriesCoordinator;
import com.readywealth.trading.trade_engine.session.domain.UiSessionId;
import com.readywealth.trading.trade_engine.session.infrastructure.memory.InMemoryConcurrentBarSeriesRegistry;
import com.readywealth.trading.trade_engine.marketdata.domain.TickQuote;
import com.readywealth.trading.trade_engine.marketdata.domain.TypedTick;
import com.readywealth.trading.trade_engine.marketdata.infrastructure.config.BootstrapProperties;
import com.readywealth.trading.trade_engine.marketdata.api.ws.TicksWebSocketBroadcaster;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickToSeriesIngestServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Test
    void emitsCandleAppendedOnBoundaryCross() {
        InMemoryConcurrentBarSeriesRegistry registry = new InMemoryConcurrentBarSeriesRegistry();
        SeriesKey seriesKey = new SeriesKey(256265L, IntervalKind.TIME_1M);
        registry.acquire(seriesKey, 5000);

        SessionSeriesCoordinator coordinator = Mockito.mock(SessionSeriesCoordinator.class);
        Mockito.when(coordinator.sessionsForSeries(seriesKey)).thenReturn(java.util.Set.of(new UiSessionId("s1")));
        @SuppressWarnings("unchecked")
        ObjectProvider<SessionSeriesCoordinator> coordinatorProvider = Mockito.mock(ObjectProvider.class);
        Mockito.when(coordinatorProvider.getIfAvailable()).thenReturn(coordinator);
        TicksWebSocketBroadcaster broadcaster = Mockito.mock(TicksWebSocketBroadcaster.class);
        TickToSeriesIngestService service = new TickToSeriesIngestService(
                registry,
                coordinatorProvider,
                broadcaster,
                new BootstrapProperties(),
                new SimpleMeterRegistry());

        service.ingest(null, typed(256265L, 100.0, 1739863200000L, 1, null));
        service.ingest(null, typed(256265L, 101.0, 1739863210000L, 1, null));
        service.ingest(null, typed(256265L, 102.0, 1739863261000L, 1, null));

        Mockito.verify(broadcaster, atLeast(1)).broadcastCandleAppendedToSession(any(), any());
    }

    @Test
    void dropsOutOfOrderTicks() {
        InMemoryConcurrentBarSeriesRegistry registry = new InMemoryConcurrentBarSeriesRegistry();
        SeriesKey seriesKey = new SeriesKey(256265L, IntervalKind.TIME_1M);
        registry.acquire(seriesKey, 5000);

        SessionSeriesCoordinator coordinator = Mockito.mock(SessionSeriesCoordinator.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<SessionSeriesCoordinator> coordinatorProvider = Mockito.mock(ObjectProvider.class);
        Mockito.when(coordinatorProvider.getIfAvailable()).thenReturn(coordinator);
        TicksWebSocketBroadcaster broadcaster = Mockito.mock(TicksWebSocketBroadcaster.class);
        TickToSeriesIngestService service = new TickToSeriesIngestService(
                registry, coordinatorProvider, broadcaster, new BootstrapProperties(), new SimpleMeterRegistry());

        service.ingest(null, typed(256265L, 100.0, 1739863200000L, 1, null));
        service.ingest(null, typed(256265L, 200.0, 1739863199000L, 1, null)); // stale

        var entry = registry.get(seriesKey).orElseThrow();
        assertEquals(1739863200000L, entry.lastIngestTimestampMs().get());
        var series = entry.series();
        assertEquals(0, series.getEndIndex());
        assertEquals("100.0", series.getBar(series.getEndIndex()).getClosePrice().toString());
    }

    @Test
    void dropsMissingTimestampTicks() {
        InMemoryConcurrentBarSeriesRegistry registry = new InMemoryConcurrentBarSeriesRegistry();
        SeriesKey seriesKey = new SeriesKey(256265L, IntervalKind.TIME_1M);
        registry.acquire(seriesKey, 5000);

        @SuppressWarnings("unchecked")
        ObjectProvider<SessionSeriesCoordinator> coordinatorProvider = Mockito.mock(ObjectProvider.class);
        TicksWebSocketBroadcaster broadcaster = Mockito.mock(TicksWebSocketBroadcaster.class);
        TickToSeriesIngestService service = new TickToSeriesIngestService(
                registry, coordinatorProvider, broadcaster, new BootstrapProperties(), new SimpleMeterRegistry());

        service.ingest(null, typed(256265L, 100.0, null, 2, null));

        var entry = registry.get(seriesKey).orElseThrow();
        assertEquals(Long.MIN_VALUE, entry.lastIngestTimestampMs().get());
        assertTrue(entry.series().isEmpty());
    }

    @Test
    void resolvesVolumeFromCumulativeDeltaThenLtqThenZero() {
        InMemoryConcurrentBarSeriesRegistry registry = new InMemoryConcurrentBarSeriesRegistry();
        SeriesKey seriesKey = new SeriesKey(256265L, IntervalKind.TIME_1M);
        registry.acquire(seriesKey, 5000);

        @SuppressWarnings("unchecked")
        ObjectProvider<SessionSeriesCoordinator> coordinatorProvider = Mockito.mock(ObjectProvider.class);
        TicksWebSocketBroadcaster broadcaster = Mockito.mock(TicksWebSocketBroadcaster.class);
        TickToSeriesIngestService service = new TickToSeriesIngestService(
                registry, coordinatorProvider, broadcaster, new BootstrapProperties(), new SimpleMeterRegistry());

        service.ingest(null, typed(256265L, 100.0, 1739863200000L, null, 100)); // first cumulative -> 0
        service.ingest(null, typed(256265L, 101.0, 1739863201000L, null, 103)); // delta 3
        service.ingest(null, typed(256265L, 102.0, 1739863202000L, 4, null));    // ltq 4
        service.ingest(null, typed(256265L, 103.0, 1739863203000L, null, null)); // 0

        var series = registry.get(seriesKey).orElseThrow().series();
        assertEquals(0, series.getEndIndex());
        assertEquals("103.0", series.getBar(series.getEndIndex()).getClosePrice().toString());
        assertEquals("7.0", series.getBar(series.getEndIndex()).getVolume().toString());
    }

    @Test
    void buffersToRedisStoreWhenBootstrapV2IsRunning() {
        InMemoryConcurrentBarSeriesRegistry registry = new InMemoryConcurrentBarSeriesRegistry();
        SeriesKey seriesKey = new SeriesKey(256265L, IntervalKind.TIME_1M);
        InMemoryConcurrentBarSeriesRegistry.AcquireResult acquireResult = registry.acquire(seriesKey, 5000);
        long runId = acquireResult.entry().bootstrapState().reserve(Instant.now().plusSeconds(60), true);

        @SuppressWarnings("unchecked")
        ObjectProvider<SessionSeriesCoordinator> coordinatorProvider = Mockito.mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<BootstrapTickBufferStore> tickBufferStoreProvider = Mockito.mock(ObjectProvider.class);
        BootstrapTickBufferStore tickBufferStore = Mockito.mock(BootstrapTickBufferStore.class);
        Mockito.when(tickBufferStoreProvider.getIfAvailable()).thenReturn(tickBufferStore);
        TicksWebSocketBroadcaster broadcaster = Mockito.mock(TicksWebSocketBroadcaster.class);
        TickToSeriesIngestService service = new TickToSeriesIngestService(
                registry,
                null,
                coordinatorProvider,
                tickBufferStoreProvider,
                broadcaster,
                new BootstrapProperties(),
                new SessionAlignedSeriesIngestor("Asia/Kolkata", "09:15", "15:30"),
                false,
                new SimpleMeterRegistry());

        service.ingest(null, typed(256265L, 101.0, 1739863200000L, 2, null));

        Mockito.verify(tickBufferStore).append(
                eq(seriesKey),
                eq(runId),
                eq(1739863200000L),
                eq(101.0),
                eq(2.0),
                any(Long.class));
        assertTrue(acquireResult.entry().series().isEmpty());
    }

    @Test
    void sessionAlignedLivePathUsesTenMinuteSessionAnchorsWhenEnabled() {
        InMemoryConcurrentBarSeriesRegistry registry = new InMemoryConcurrentBarSeriesRegistry();
        SeriesKey seriesKey = new SeriesKey(408065L, IntervalKind.TIME_10M);
        registry.acquire(seriesKey, 5000);

        @SuppressWarnings("unchecked")
        ObjectProvider<SessionSeriesCoordinator> coordinatorProvider = Mockito.mock(ObjectProvider.class);
        TicksWebSocketBroadcaster broadcaster = Mockito.mock(TicksWebSocketBroadcaster.class);
        TickToSeriesIngestService service = new TickToSeriesIngestService(
                registry,
                null,
                coordinatorProvider,
                null,
                broadcaster,
                new BootstrapProperties(),
                new SessionAlignedSeriesIngestor("Asia/Kolkata", "09:15", "15:30"),
                true,
                new SimpleMeterRegistry());

        service.ingest(null, typed(408065L, 100.0, ts(2026, 3, 2, 9, 16), 1, null));
        service.ingest(null, typed(408065L, 102.0, ts(2026, 3, 2, 9, 24), 1, null));
        service.ingest(null, typed(408065L, 101.0, ts(2026, 3, 2, 9, 25), 1, null));

        var series = registry.get(seriesKey).orElseThrow().series();
        assertEquals(1, series.getEndIndex());
        assertEquals(ts(2026, 3, 2, 9, 15), series.getBar(0).getBeginTime().toEpochMilli());
        assertEquals(ts(2026, 3, 2, 9, 25), series.getBar(1).getBeginTime().toEpochMilli());
    }

    @Test
    void sessionAlignedLivePathStartsThirtyMinuteFirstBucketAtNineFifteen() {
        InMemoryConcurrentBarSeriesRegistry registry = new InMemoryConcurrentBarSeriesRegistry();
        SeriesKey seriesKey = new SeriesKey(408065L, IntervalKind.TIME_30M);
        registry.acquire(seriesKey, 5000);

        @SuppressWarnings("unchecked")
        ObjectProvider<SessionSeriesCoordinator> coordinatorProvider = Mockito.mock(ObjectProvider.class);
        TicksWebSocketBroadcaster broadcaster = Mockito.mock(TicksWebSocketBroadcaster.class);
        TickToSeriesIngestService service = new TickToSeriesIngestService(
                registry,
                null,
                coordinatorProvider,
                null,
                broadcaster,
                new BootstrapProperties(),
                new SessionAlignedSeriesIngestor("Asia/Kolkata", "09:15", "15:30"),
                true,
                new SimpleMeterRegistry());

        service.ingest(null, typed(408065L, 120.0, ts(2026, 3, 2, 9, 29), 1, null));

        var series = registry.get(seriesKey).orElseThrow().series();
        assertEquals(0, series.getEndIndex());
        assertEquals(ts(2026, 3, 2, 9, 15), series.getBar(0).getBeginTime().toEpochMilli());
    }

    private TypedTick typed(long token, double price, Long ts, Integer qty, Integer volumeToday) {
        TickQuote q = new TickQuote();
        q.setInstrumentToken(token);
        q.setLastTradedPrice(price);
        q.setTickTimestamp(ts);
        q.setLastTradedQuantity(qty);
        q.setVolumeTradedToday(volumeToday);
        q.setMode("quote");
        return new TypedTick(q, null);
    }

    private long ts(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, IST).toInstant().toEpochMilli();
    }
}
