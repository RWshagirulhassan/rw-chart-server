package com.readywealth.trading.trade_engine.session.application;

import com.readywealth.trading.trade_engine.engine.domain.series.IntervalKind;
import com.readywealth.trading.trade_engine.engine.application.runtime.SeriesKeyCodec;
import com.readywealth.trading.trade_engine.session.domain.SessionNotFoundException;
import com.readywealth.trading.trade_engine.session.domain.SessionState;
import com.readywealth.trading.trade_engine.session.domain.UiSession;
import com.readywealth.trading.trade_engine.session.infrastructure.memory.InMemoryConcurrentBarSeriesRegistry;
import com.readywealth.trading.trade_engine.session.infrastructure.memory.SeriesRefManager;
import com.readywealth.trading.trade_engine.marketdata.application.SessionBootstrapCoordinator;
import com.readywealth.trading.trade_engine.marketdata.application.SubscriptionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.mockito.InOrder;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;

class SessionSeriesCoordinatorTest {

    @Test
    void sharesSeriesAcrossSessionsAndDetachesCleanly() {
        InMemoryConcurrentBarSeriesRegistry registry = new InMemoryConcurrentBarSeriesRegistry();
        SeriesRefManager refManager = new SeriesRefManager();
        SubscriptionService subscriptionService = Mockito.mock(SubscriptionService.class);
        SessionBootstrapCoordinator bootstrapCoordinator = Mockito.mock(SessionBootstrapCoordinator.class);

        SessionSeriesCoordinator coordinator = new SessionSeriesCoordinator(
                registry,
                refManager,
                subscriptionService,
                bootstrapCoordinator,
                new SimpleMeterRegistry());

        String key = SeriesKeyCodec.encode(new com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey(256265L, IntervalKind.TIME_1M));

        UiSession s1 = coordinator.createSession("u1", List.of(key), 1000, true);
        UiSession s2 = coordinator.createSession("u1", List.of(key), 1000, true);

        assertEquals(1, registry.size());
        assertEquals(2, registry.get(SeriesKeyCodec.decode(key)).orElseThrow().seriesRefCount().get());

        assertTrue(coordinator.closeSession("u1", s1.sessionId()));
        assertEquals(1, registry.get(SeriesKeyCodec.decode(key)).orElseThrow().seriesRefCount().get());

        assertTrue(coordinator.closeSession("u1", s2.sessionId()));
        assertTrue(registry.get(SeriesKeyCodec.decode(key)).isEmpty());

        Mockito.verify(subscriptionService, times(1)).subscribeInternal(eq("ui-series-registry"), anyList());
        Mockito.verify(subscriptionService, times(1)).unsubscribeInternal(eq("ui-series-registry"), anyList());
    }

    @Test
    void retainsOrClosesOnLastWsDisconnectBasedOnDestroyOnClose() {
        InMemoryConcurrentBarSeriesRegistry registry = new InMemoryConcurrentBarSeriesRegistry();
        SeriesRefManager refManager = new SeriesRefManager();
        SubscriptionService subscriptionService = Mockito.mock(SubscriptionService.class);
        SessionBootstrapCoordinator bootstrapCoordinator = Mockito.mock(SessionBootstrapCoordinator.class);
        SessionSeriesCoordinator coordinator = new SessionSeriesCoordinator(
                registry,
                refManager,
                subscriptionService,
                bootstrapCoordinator,
                new SimpleMeterRegistry());
        String key = SeriesKeyCodec.encode(new com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey(256265L, IntervalKind.TIME_1M));

        UiSession keep = coordinator.createSession("u1", List.of(key), 1000, false);
        UiSession destroy = coordinator.createSession("u1", List.of(key), 1000, true);

        coordinator.onWsConnected("u1", keep.sessionId());
        coordinator.onWsConnected("u1", destroy.sessionId());
        coordinator.onWsDisconnected(keep.sessionId());
        coordinator.onWsDisconnected(destroy.sessionId());

        assertEquals(SessionState.RETAINED, coordinator.getSession("u1", keep.sessionId()).state());
        assertThrows(SessionNotFoundException.class, () -> coordinator.getSession("u1", destroy.sessionId()));
    }

    @Test
    void closeSessionIsIdempotentWhenAlreadyClosedByWsDisconnect() {
        InMemoryConcurrentBarSeriesRegistry registry = new InMemoryConcurrentBarSeriesRegistry();
        SeriesRefManager refManager = new SeriesRefManager();
        SubscriptionService subscriptionService = Mockito.mock(SubscriptionService.class);
        SessionBootstrapCoordinator bootstrapCoordinator = Mockito.mock(SessionBootstrapCoordinator.class);
        SessionSeriesCoordinator coordinator = new SessionSeriesCoordinator(
                registry,
                refManager,
                subscriptionService,
                bootstrapCoordinator,
                new SimpleMeterRegistry());
        String key = SeriesKeyCodec.encode(new com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey(256265L, IntervalKind.TIME_1M));

        UiSession destroy = coordinator.createSession("u1", List.of(key), 1000, true);
        coordinator.onWsConnected("u1", destroy.sessionId());
        coordinator.onWsDisconnected(destroy.sessionId());

        assertFalse(coordinator.closeSession("u1", destroy.sessionId()));
    }

    @Test
    void snapshotReturnsEmptyBarsWhenSeriesHasNoBars() {
        InMemoryConcurrentBarSeriesRegistry registry = new InMemoryConcurrentBarSeriesRegistry();
        SeriesRefManager refManager = new SeriesRefManager();
        SubscriptionService subscriptionService = Mockito.mock(SubscriptionService.class);
        SessionBootstrapCoordinator bootstrapCoordinator = Mockito.mock(SessionBootstrapCoordinator.class);
        SessionSeriesCoordinator coordinator = new SessionSeriesCoordinator(
                registry,
                refManager,
                subscriptionService,
                bootstrapCoordinator,
                new SimpleMeterRegistry());

        String key = SeriesKeyCodec.encode(
                new com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey(256265L, IntervalKind.TIME_1M));
        UiSession session = coordinator.createSession("u1", List.of(key), 1000, true);

        var snapshot = coordinator.snapshot("u1", session.sessionId(), SeriesKeyCodec.decode(key));
        assertTrue(snapshot.bars().isEmpty());
    }

    @Test
    void reservesBootstrapBeforeSubscribingNewSeries() {
        InMemoryConcurrentBarSeriesRegistry registry = new InMemoryConcurrentBarSeriesRegistry();
        SeriesRefManager refManager = new SeriesRefManager();
        SubscriptionService subscriptionService = Mockito.mock(SubscriptionService.class);
        SessionBootstrapCoordinator bootstrapCoordinator = Mockito.mock(SessionBootstrapCoordinator.class);
        SessionSeriesCoordinator coordinator = new SessionSeriesCoordinator(
                registry,
                refManager,
                subscriptionService,
                bootstrapCoordinator,
                new SimpleMeterRegistry());

        String key = SeriesKeyCodec.encode(new com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey(256265L, IntervalKind.TIME_1M));
        UiSession session = coordinator.createSession("u1", List.of(key), 1000, true);

        InOrder inOrder = Mockito.inOrder(bootstrapCoordinator, subscriptionService);
        inOrder.verify(bootstrapCoordinator).reserveUiSeriesBootstrap(
                Mockito.eq(session.sessionId().value()),
                Mockito.any(),
                Mockito.any());
        inOrder.verify(subscriptionService).subscribeInternal(Mockito.eq("ui-series-registry"), Mockito.anyList());
        inOrder.verify(bootstrapCoordinator).bootstrapUiSeriesAsync(Mockito.any());
    }

    @Test
    void healthyRetainedSeriesReusesWithoutReservingBootstrapAgain() {
        InMemoryConcurrentBarSeriesRegistry registry = new InMemoryConcurrentBarSeriesRegistry();
        SeriesRefManager refManager = new SeriesRefManager();
        SubscriptionService subscriptionService = Mockito.mock(SubscriptionService.class);
        SessionBootstrapCoordinator bootstrapCoordinator = Mockito.mock(SessionBootstrapCoordinator.class);
        SessionSeriesCoordinator coordinator = new SessionSeriesCoordinator(
                registry,
                refManager,
                subscriptionService,
                bootstrapCoordinator,
                new SimpleMeterRegistry());

        String key = SeriesKeyCodec.encode(new com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey(256265L, IntervalKind.TIME_1M));
        coordinator.createSession("u1", List.of(key), 1000, true);
        var entry = registry.get(SeriesKeyCodec.decode(key)).orElseThrow();
        long runId = entry.bootstrapState().reserve(Instant.now(), false);
        entry.bootstrapState().markSeeded(12);
        entry.bootstrapState().markCompleted(runId);

        var reused = coordinator.createSession("u1", List.of(key), 1000, true);
        var snapshot = coordinator.snapshot("u1", reused.sessionId(), SeriesKeyCodec.decode(key));

        assertEquals("HEALTHY_REUSED", snapshot.seriesHealthStatus());
        assertEquals("REUSED_HEALTHY", snapshot.seriesReuseDecision());
        Mockito.verify(bootstrapCoordinator, times(1)).reserveUiSeriesBootstrap(Mockito.anyString(), Mockito.any(), Mockito.any());
    }

    @Test
    void staleRetainedExclusiveSeriesIsRebuiltAndReservedAgain() {
        InMemoryConcurrentBarSeriesRegistry registry = new InMemoryConcurrentBarSeriesRegistry();
        SeriesRefManager refManager = new SeriesRefManager();
        SubscriptionService subscriptionService = Mockito.mock(SubscriptionService.class);
        SessionBootstrapCoordinator bootstrapCoordinator = Mockito.mock(SessionBootstrapCoordinator.class);
        SessionSeriesCoordinator coordinator = new SessionSeriesCoordinator(
                registry,
                refManager,
                subscriptionService,
                bootstrapCoordinator,
                new SimpleMeterRegistry());

        String key = SeriesKeyCodec.encode(new com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey(256265L, IntervalKind.TIME_1M));
        coordinator.createSession("u1", List.of(key), 1000, false);
        var existing = registry.get(SeriesKeyCodec.decode(key)).orElseThrow();
        existing.bootstrapState().forceDegraded("live_bars_without_historical_seed");

        var rebuilt = coordinator.createSession("u1", List.of(key), 1000, true);
        var snapshot = coordinator.snapshot("u1", rebuilt.sessionId(), SeriesKeyCodec.decode(key));

        assertEquals("REBUILDING", snapshot.seriesHealthStatus());
        assertEquals("REBUILT_STALE_RETAINED", snapshot.seriesReuseDecision());
        Mockito.verify(bootstrapCoordinator, times(2)).reserveUiSeriesBootstrap(Mockito.anyString(), Mockito.any(), Mockito.any());
    }

    @Test
    void staleSharedActiveSeriesIsSurfacedWithoutBootstrapReserve() {
        InMemoryConcurrentBarSeriesRegistry registry = new InMemoryConcurrentBarSeriesRegistry();
        SeriesRefManager refManager = new SeriesRefManager();
        SubscriptionService subscriptionService = Mockito.mock(SubscriptionService.class);
        SessionBootstrapCoordinator bootstrapCoordinator = Mockito.mock(SessionBootstrapCoordinator.class);
        SessionSeriesCoordinator coordinator = new SessionSeriesCoordinator(
                registry,
                refManager,
                subscriptionService,
                bootstrapCoordinator,
                new SimpleMeterRegistry());

        String key = SeriesKeyCodec.encode(new com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey(256265L, IntervalKind.TIME_1M));
        UiSession activeOwner = coordinator.createSession("u1", List.of(key), 1000, false);
        coordinator.onWsConnected("u1", activeOwner.sessionId());
        var existing = registry.get(SeriesKeyCodec.decode(key)).orElseThrow();
        existing.bootstrapState().forceDegraded("live_bars_without_historical_seed");

        var shared = coordinator.createSession("u1", List.of(key), 1000, true);
        var snapshot = coordinator.snapshot("u1", shared.sessionId(), SeriesKeyCodec.decode(key));

        assertEquals("STALE_SHARED", snapshot.seriesHealthStatus());
        assertEquals("live_bars_without_historical_seed", snapshot.seriesHealthReason());
        assertEquals("STALE_SHARED", snapshot.seriesReuseDecision());
        Mockito.verify(bootstrapCoordinator, times(1)).reserveUiSeriesBootstrap(Mockito.anyString(), Mockito.any(), Mockito.any());
    }
}
