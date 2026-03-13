package com.readywealth.trading.trade_engine.session.application;

import com.readywealth.trading.trade_engine.session.api.http.dto.SeriesBarView;
import com.readywealth.trading.trade_engine.session.api.http.dto.SeriesSnapshotResponse;
import com.readywealth.trading.trade_engine.engine.domain.series.SeriesConfig;
import com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey;
import com.readywealth.trading.trade_engine.engine.application.runtime.SeriesKeyCodec;
import com.readywealth.trading.trade_engine.session.application.port.UpstreamSubscriptionPort;
import com.readywealth.trading.trade_engine.session.domain.SessionForbiddenException;
import com.readywealth.trading.trade_engine.session.domain.SessionNotFoundException;
import com.readywealth.trading.trade_engine.session.domain.SessionState;
import com.readywealth.trading.trade_engine.session.domain.UiSession;
import com.readywealth.trading.trade_engine.session.domain.UiSessionId;
import com.readywealth.trading.trade_engine.session.infrastructure.memory.InMemoryConcurrentBarSeriesRegistry;
import com.readywealth.trading.trade_engine.session.infrastructure.memory.SeriesRefManager;
import com.readywealth.trading.trade_engine.marketdata.application.SessionBootstrapCoordinator;
import com.readywealth.trading.trade_engine.marketdata.application.port.SessionRoutingPort;
import com.readywealth.trading.trade_engine.engine.application.runtime.SeriesRuntimeManager;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.time.Instant;

@Service
public class SessionSeriesCoordinator implements SessionRoutingPort {

    private static final Logger log = LoggerFactory.getLogger(SessionSeriesCoordinator.class);
    private static final String UI_SERIES_SUBSCRIBER_ID = "ui-series-registry";
    private static final ZoneId CLIENT_ZONE = ZoneId.of("Asia/Kolkata");

    private final Map<UiSessionId, UiSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Set<UiSessionId>> sessionsByUser = new ConcurrentHashMap<>();
    private final Map<SeriesKey, Set<UiSessionId>> sessionsBySeries = new ConcurrentHashMap<>();
    private final Map<Long, Set<UiSessionId>> sessionsByInstrument = new ConcurrentHashMap<>();
    private final Map<UiSessionId, Map<SeriesKey, Integer>> lastDeliveredBarIndexBySessionSeries = new ConcurrentHashMap<>();
    private final InMemoryConcurrentBarSeriesRegistry seriesRegistry;
    private final SeriesRefManager refManager;
    private final UpstreamSubscriptionPort upstreamSubscriptionPort;
    private final SessionBootstrapCoordinator bootstrapCoordinator;
    private final SeriesRuntimeManager runtimeManager;
    private final Counter sessionCreateCounter;
    private final Counter sessionDeleteCounter;
    private final Counter seriesAttachCounter;
    private final Counter seriesDetachCounter;
    private final Counter seriesDiscardCounter;
    @Autowired(required = false)
    private ObjectProvider<StressSessionRegistry> stressSessionRegistryProvider;

    @Autowired
    public SessionSeriesCoordinator(
            InMemoryConcurrentBarSeriesRegistry seriesRegistry,
            SeriesRefManager refManager,
            UpstreamSubscriptionPort upstreamSubscriptionPort,
            SessionBootstrapCoordinator bootstrapCoordinator,
            SeriesRuntimeManager runtimeManager,
            MeterRegistry meterRegistry) {
        this.seriesRegistry = seriesRegistry;
        this.refManager = refManager;
        this.upstreamSubscriptionPort = upstreamSubscriptionPort;
        this.bootstrapCoordinator = bootstrapCoordinator;
        this.runtimeManager = runtimeManager;
        this.sessionCreateCounter = meterRegistry.counter("session_create_calls");
        this.sessionDeleteCounter = meterRegistry.counter("session_delete_calls");
        this.seriesAttachCounter = meterRegistry.counter("series_attach_calls");
        this.seriesDetachCounter = meterRegistry.counter("series_detach_calls");
        this.seriesDiscardCounter = meterRegistry.counter("series_discard_count");

        meterRegistry.gauge("ui_sessions_active", this, SessionSeriesCoordinator::activeSessions);
        meterRegistry.gauge("series_entries_active", seriesRegistry, InMemoryConcurrentBarSeriesRegistry::size);
        meterRegistry.gauge("series_ref_total", seriesRegistry, InMemoryConcurrentBarSeriesRegistry::totalRefs);
        meterRegistry.gauge("instrument_ref_total", refManager, SeriesRefManager::totalInstrumentRefs);
    }

    public SessionSeriesCoordinator(
            InMemoryConcurrentBarSeriesRegistry seriesRegistry,
            SeriesRefManager refManager,
            UpstreamSubscriptionPort upstreamSubscriptionPort,
            SessionBootstrapCoordinator bootstrapCoordinator,
            MeterRegistry meterRegistry) {
        this(seriesRegistry, refManager, upstreamSubscriptionPort, bootstrapCoordinator, null, meterRegistry);
    }

    public synchronized UiSession createSession(
            String userId,
            List<String> rawSeriesKeys,
            Integer requestedMaxBarCount,
            Boolean destroyOnClose) {
        return createSession(userId, rawSeriesKeys, requestedMaxBarCount, destroyOnClose, false);
    }

    public synchronized UiSession createSession(
            String userId,
            List<String> rawSeriesKeys,
            Integer requestedMaxBarCount,
            Boolean destroyOnClose,
            boolean stressSession) {
        UiSessionId sessionId = UiSessionId.newId();
        UiSession session = new UiSession(sessionId, userId, destroyOnClose == null || destroyOnClose);
        session.setState(SessionState.RETAINED);
        sessions.put(sessionId, session);
        sessionsByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(sessionId);
        lastDeliveredBarIndexBySessionSeries.computeIfAbsent(sessionId, ignored -> new ConcurrentHashMap<>());
        if (stressSession) {
            StressSessionRegistry stressSessionRegistry = stressSessionRegistryProvider == null
                    ? null
                    : stressSessionRegistryProvider.getIfAvailable();
            if (stressSessionRegistry != null) {
                stressSessionRegistry.markStressSession(sessionId.value());
            }
        }

        List<SeriesKey> keys = parseSeriesKeys(rawSeriesKeys);
        int maxBarCount = requestedMaxBarCount == null ? SeriesConfig.liveDefault().maximumBarCount()
                : requestedMaxBarCount;

        AttachDetachResult result = attachSeriesInternal(session, keys, maxBarCount);
        applyUpstreamTransitions(result);
        keys.forEach(bootstrapCoordinator::bootstrapUiSeriesAsync);
        logRegistryHubStateForSeries("session_create", session, keys, null);

        sessionCreateCounter.increment();
        log.info("ui_session_created sessionId={} userId={} seriesCount={} destroyOnClose={}",
                sessionId.value(), userId, session.attachedSeries().size(), session.destroyOnClose());
        return session;
    }

    public synchronized UiSession addSeries(String userId, UiSessionId sessionId, List<String> rawSeriesKeys) {
        UiSession session = requiredSessionForUser(userId, sessionId);
        List<SeriesKey> keys = parseSeriesKeys(rawSeriesKeys);

        AttachDetachResult result = attachSeriesInternal(session, keys, SeriesConfig.liveDefault().maximumBarCount());
        applyUpstreamTransitions(result);
        keys.forEach(bootstrapCoordinator::bootstrapUiSeriesAsync);
        return session;
    }

    public synchronized UiSession removeSeries(String userId, UiSessionId sessionId, List<String> rawSeriesKeys) {
        UiSession session = requiredSessionForUser(userId, sessionId);
        List<SeriesKey> keys = parseSeriesKeys(rawSeriesKeys);

        AttachDetachResult result = detachSeriesInternal(session, keys);
        applyUpstreamTransitions(result);
        return session;
    }

    public synchronized boolean closeSession(String userId, UiSessionId sessionId) {
        UiSession session = sessions.get(sessionId);
        if (session == null) {
            log.info("ui_session_close_idempotent_miss sessionId={} userId={}", sessionId.value(), userId);
            return false;
        }
        if (!session.userId().equals(userId)) {
            throw new SessionForbiddenException("session does not belong to current user");
        }
        return closeSessionInternal(session, "api_close");
    }

    public synchronized List<UiSession> listSessionsForUser(String userId) {
        Set<UiSessionId> ids = sessionsByUser.getOrDefault(userId, Collections.emptySet());
        List<UiSession> out = new ArrayList<>();
        for (UiSessionId id : ids) {
            UiSession session = sessions.get(id);
            if (session != null && session.state() != SessionState.CLOSED) {
                out.add(session);
            }
        }
        out.sort((a, b) -> b.updatedAt().compareTo(a.updatedAt()));
        return out;
    }

    public synchronized List<UiSession> listAllSessions() {
        List<UiSession> out = new ArrayList<>();
        for (UiSession session : sessions.values()) {
            if (session.state() != SessionState.CLOSED) {
                out.add(session);
            }
        }
        out.sort((a, b) -> b.updatedAt().compareTo(a.updatedAt()));
        return out;
    }

    public synchronized UiSession onWsConnected(String userId, UiSessionId sessionId) {
        UiSession session = requiredSessionForUser(userId, sessionId);
        session.incrementConnectedClients();
        session.setState(SessionState.RUNNING);
        return session;
    }

    public synchronized void onWsDisconnected(UiSessionId sessionId) {
        UiSession session = sessions.get(sessionId);
        if (session == null) {
            log.info("ws_disconnect_session_missing sessionId={}", sessionId.value());
            return;
        }
        int connectedBefore = session.connectedClients();
        int connected = session.decrementConnectedClients();
        log.info("ws_disconnect_session_updated sessionId={} userId={} connectedBefore={} connectedAfter={} destroyOnClose={} stateBefore={}",
                sessionId.value(),
                session.userId(),
                connectedBefore,
                connected,
                session.destroyOnClose(),
                session.state().name());
        if (connected > 0) {
            session.setState(SessionState.RUNNING);
            log.info("ws_disconnect_session_kept_running sessionId={} userId={} remainingClients={}",
                    sessionId.value(),
                    session.userId(),
                    connected);
            return;
        }
        if (session.destroyOnClose()) {
            try {
                closeSessionInternal(session, "ws_disconnected_destroy_on_close");
            } catch (Exception ex) {
                session.setState(SessionState.RETAINED);
                log.error("ws_disconnect_auto_close_failed sessionId={} userId={} reason={}",
                        sessionId.value(),
                        session.userId(),
                        ex.getMessage(),
                        ex);
            }
        } else {
            session.setState(SessionState.RETAINED);
            log.info("ws_disconnect_session_retained sessionId={} userId={}",
                    sessionId.value(),
                    session.userId());
        }
    }

    public synchronized UiSession getSession(String userId, UiSessionId sessionId) {
        return requiredSessionForUser(userId, sessionId);
    }

    public synchronized SeriesSnapshotResponse snapshot(String userId, UiSessionId sessionId, SeriesKey seriesKey) {
        UiSession session = requiredSessionForUser(userId, sessionId);
        if (!session.attachedSeries().contains(seriesKey)) {
            throw new IllegalArgumentException("seriesKey is not attached to session: " + SeriesKeyCodec.encode(seriesKey));
        }

        InMemoryConcurrentBarSeriesRegistry.SeriesEntry entry = seriesRegistry.get(seriesKey)
                .orElseThrow(() -> new IllegalArgumentException("seriesKey not found: " + SeriesKeyCodec.encode(seriesKey)));

        var series = entry.series();
        List<SeriesBarView> bars = new ArrayList<>();
        SnapshotWindow snapshotWindow = new SnapshotWindow(-1, -1);
        series.withReadLock(() -> {
            int begin = series.getBeginIndex();
            int end = series.getEndIndex();
            if (begin < 0 || end < 0 || begin > end) {
                snapshotWindow.beginIndex = begin;
                snapshotWindow.endIndex = end;
                return;
            }
            snapshotWindow.beginIndex = begin;
            snapshotWindow.endIndex = end;
            for (int i = begin; i <= end; i++) {
                var bar = series.getBar(i);
                bars.add(new SeriesBarView(
                        OffsetDateTime.ofInstant(bar.getBeginTime(), CLIENT_ZONE),
                        OffsetDateTime.ofInstant(bar.getEndTime(), CLIENT_ZONE),
                        i,
                        "SNAPSHOT",
                        bar.getOpenPrice().toString(),
                        bar.getHighPrice().toString(),
                        bar.getLowPrice().toString(),
                        bar.getClosePrice().toString(),
                        bar.getVolume().toString(),
                        bar.getTrades()));
            }
        });
        markSnapshotDelivered(session.sessionId(), seriesKey, snapshotWindow.endIndex);
        log.info("sync_snapshot_sent sessionId={} seriesKey={} beginIndex={} endIndex={} bars={} lastSeq={} snapshotCursor={}",
                session.sessionId().value(),
                SeriesKeyCodec.encode(seriesKey),
                snapshotWindow.beginIndex,
                snapshotWindow.endIndex,
                bars.size(),
                entry.lastSeq().get(),
                entry.lastSeq().get());

        return new SeriesSnapshotResponse(
                SeriesKeyCodec.encode(seriesKey),
                snapshotWindow.beginIndex,
                snapshotWindow.endIndex,
                entry.maxBarCount(),
                entry.seriesRefCount().get(),
                entry.lastSeq().get(),
                entry.lastSeq().get(),
                Instant.now(),
                session.state().name(),
                bars,
                entry.bootstrapState().snapshot().status().name(),
                entry.bootstrapState().snapshot().startedAt(),
                entry.bootstrapState().snapshot().completedAt(),
                entry.bootstrapState().snapshot().cutoffAt(),
                entry.bootstrapState().snapshot().error(),
                entry.bootstrapState().snapshot().seededBars(),
                entry.bootstrapState().snapshot().replayedTicks(),
                entry.bootstrapState().snapshot().droppedTicks());
    }

    private static final class SnapshotWindow {
        private int beginIndex;
        private int endIndex;

        private SnapshotWindow(int beginIndex, int endIndex) {
            this.beginIndex = beginIndex;
            this.endIndex = endIndex;
        }
    }

    public synchronized void markSnapshotDelivered(UiSessionId sessionId, SeriesKey seriesKey, int endIndex) {
        lastDeliveredBarIndexBySessionSeries
                .computeIfAbsent(sessionId, ignored -> new ConcurrentHashMap<>())
                .put(seriesKey, endIndex);
    }

    public synchronized String resolveAndMarkCandleMutation(UiSessionId sessionId, SeriesKey seriesKey, int barIndex) {
        Map<SeriesKey, Integer> perSeries = lastDeliveredBarIndexBySessionSeries
                .computeIfAbsent(sessionId, ignored -> new ConcurrentHashMap<>());
        int previous = perSeries.getOrDefault(seriesKey, Integer.MIN_VALUE);
        String mutation = barIndex > previous ? "APPEND" : "REPLACE";
        perSeries.put(seriesKey, Math.max(previous, barIndex));
        return mutation;
    }

    @Override
    public synchronized Set<UiSessionId> sessionsForInstrument(long instrumentToken) {
        return new HashSet<>(sessionsByInstrument.getOrDefault(instrumentToken, Collections.emptySet()));
    }

    @Override
    public synchronized Set<UiSessionId> sessionsForSeries(SeriesKey seriesKey) {
        return new HashSet<>(sessionsBySeries.getOrDefault(seriesKey, Collections.emptySet()));
    }

    public int activeSessions() {
        return sessions.size();
    }

    public BootstrapAggregate bootstrapSummary(UiSession session) {
        InMemoryConcurrentBarSeriesRegistry.BootstrapStatus status = InMemoryConcurrentBarSeriesRegistry.BootstrapStatus.PENDING;
        Instant startedAt = null;
        Instant completedAt = null;
        Instant cutoffAt = null;
        String error = null;
        long seeded = 0;
        long replayed = 0;
        long dropped = 0;

        for (SeriesKey key : session.attachedSeries()) {
            var snapshot = seriesRegistry.get(key)
                    .map(entry -> entry.bootstrapState().snapshot())
                    .orElse(new InMemoryConcurrentBarSeriesRegistry.BootstrapSnapshot(
                            InMemoryConcurrentBarSeriesRegistry.BootstrapStatus.PENDING,
                            InMemoryConcurrentBarSeriesRegistry.BootstrapLifecycle.NOT_STARTED,
                            null, null, null, null, 0, 0, 0, 0, false));
            if (snapshot.startedAt() != null && (startedAt == null || snapshot.startedAt().isBefore(startedAt))) {
                startedAt = snapshot.startedAt();
            }
            if (snapshot.completedAt() != null && (completedAt == null || snapshot.completedAt().isAfter(completedAt))) {
                completedAt = snapshot.completedAt();
            }
            if (snapshot.cutoffAt() != null && (cutoffAt == null || snapshot.cutoffAt().isAfter(cutoffAt))) {
                cutoffAt = snapshot.cutoffAt();
            }
            if (snapshot.error() != null && error == null) {
                error = snapshot.error();
            }
            seeded += snapshot.seededBars();
            replayed += snapshot.replayedTicks();
            dropped += snapshot.droppedTicks();
            if (snapshot.status() == InMemoryConcurrentBarSeriesRegistry.BootstrapStatus.RUNNING) {
                status = InMemoryConcurrentBarSeriesRegistry.BootstrapStatus.RUNNING;
            } else if (status != InMemoryConcurrentBarSeriesRegistry.BootstrapStatus.RUNNING
                    && snapshot.status() == InMemoryConcurrentBarSeriesRegistry.BootstrapStatus.DEGRADED) {
                status = InMemoryConcurrentBarSeriesRegistry.BootstrapStatus.DEGRADED;
            } else if (status == InMemoryConcurrentBarSeriesRegistry.BootstrapStatus.PENDING
                    && snapshot.status() == InMemoryConcurrentBarSeriesRegistry.BootstrapStatus.COMPLETED) {
                status = InMemoryConcurrentBarSeriesRegistry.BootstrapStatus.COMPLETED;
            }
        }
        return new BootstrapAggregate(status.name(), startedAt, completedAt, cutoffAt, error, seeded, replayed, dropped);
    }

    private UiSession requiredSession(UiSessionId sessionId) {
        UiSession session = sessions.get(sessionId);
        if (session == null) {
            throw new SessionNotFoundException("session not found: " + sessionId.value());
        }
        return session;
    }

    private UiSession requiredSessionForUser(String userId, UiSessionId sessionId) {
        UiSession session = requiredSession(sessionId);
        if (!session.userId().equals(userId)) {
            throw new SessionForbiddenException("session does not belong to current user");
        }
        return session;
    }

    private AttachDetachResult attachSeriesInternal(UiSession session, List<SeriesKey> keys, int maxBarCount) {
        Set<Long> instrumentsToSubscribe = new LinkedHashSet<>();

        for (SeriesKey key : keys) {
            if (!session.attach(key)) {
                continue;
            }
            InMemoryConcurrentBarSeriesRegistry.AcquireResult acquire = seriesRegistry.acquire(key, maxBarCount);
            if (acquire.created()) {
                bootstrapCoordinator.reserveUiSeriesBootstrap(session.sessionId().value(), key, acquire.entry());
            }
            if (runtimeManager != null) {
                runtimeManager.ensureRuntimeForSeries(key);
            }
            sessionsBySeries.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet()).add(session.sessionId());
            sessionsByInstrument.computeIfAbsent(key.instrumentToken(), ignored -> ConcurrentHashMap.newKeySet())
                    .add(session.sessionId());
            SeriesRefManager.RefTransition transition = refManager.incrementInstrumentRef(key.instrumentToken());
            if (transition.becameActive()) {
                instrumentsToSubscribe.add(key.instrumentToken());
            }
            seriesAttachCounter.increment();
            log.info("series_attached sessionId={} seriesKey={} seriesCreated={} seriesRef={} instrumentRef={}",
                    session.sessionId().value(),
                    SeriesKeyCodec.encode(key),
                    acquire.created(),
                    acquire.entry().seriesRefCount().get(),
                    transition.current());
        }
        return new AttachDetachResult(instrumentsToSubscribe, Set.of());
    }

    private AttachDetachResult detachSeriesInternal(UiSession session, List<SeriesKey> keys) {
        Set<Long> instrumentsToUnsubscribe = new LinkedHashSet<>();

        for (SeriesKey key : keys) {
            if (!session.detach(key)) {
                continue;
            }
            removeFromIndexes(session, key);
            boolean runtimeStoppedPreRelease = false;
            int detachedOwnedScripts = 0;
            if (runtimeManager != null) {
                try {
                    detachedOwnedScripts = runtimeManager.detachAllOwnedBySession(key, session.sessionId().value());
                } catch (Exception detachError) {
                    log.warn("series_runtime_detach_owned_failed sessionId={} seriesKey={} reason={}",
                            session.sessionId().value(),
                            SeriesKeyCodec.encode(key),
                            detachError.getMessage(),
                            detachError);
                }
                InMemoryConcurrentBarSeriesRegistry.SeriesEntry entry = seriesRegistry.get(key).orElse(null);
                if (entry != null && entry.seriesRefCount().get() <= 1) {
                    try {
                        runtimeStoppedPreRelease = runtimeManager.stopAndRemove(key);
                    } catch (Exception stopError) {
                        log.error("series_runtime_stop_prerelease_failed sessionId={} seriesKey={} reason={}",
                                session.sessionId().value(),
                                SeriesKeyCodec.encode(key),
                                stopError.getMessage(),
                                stopError);
                    }
                }
            }
            InMemoryConcurrentBarSeriesRegistry.ReleaseResult release = seriesRegistry.release(key);
            if (runtimeManager != null && release.removed() && !runtimeStoppedPreRelease) {
                try {
                    runtimeManager.stopAndRemove(key);
                } catch (Exception stopError) {
                    log.error("series_runtime_stop_postrelease_failed sessionId={} seriesKey={} reason={}",
                            session.sessionId().value(),
                            SeriesKeyCodec.encode(key),
                            stopError.getMessage(),
                            stopError);
                }
            }
            SeriesRefManager.RefTransition transition = refManager.decrementInstrumentRef(key.instrumentToken());
            if (transition.becameInactive()) {
                instrumentsToUnsubscribe.add(key.instrumentToken());
            }
            if (release.removed()) {
                seriesDiscardCounter.increment();
            }
            seriesDetachCounter.increment();
            log.info("series_detached sessionId={} seriesKey={} removed={} remainingSeriesRefs={} instrumentRef={} detachedOwnedScripts={} runtimeStoppedPreRelease={}",
                    session.sessionId().value(),
                    SeriesKeyCodec.encode(key),
                    release.removed(),
                    release.remainingRefs(),
                    transition.current(),
                    detachedOwnedScripts,
                    runtimeStoppedPreRelease);
        }
        session.touch();
        return new AttachDetachResult(Set.of(), instrumentsToUnsubscribe);
    }

    private boolean closeSessionInternal(UiSession session, String reason) {
        log.info("ui_session_close_request sessionId={} userId={} reason={} attachedSeries={} connectedClients={} destroyOnClose={}",
                session.sessionId().value(),
                session.userId(),
                reason,
                session.attachedSeries().size(),
                session.connectedClients(),
                session.destroyOnClose());
        UiSession removed = sessions.remove(session.sessionId());
        if (removed == null) {
            log.info("ui_session_close_skipped sessionId={} reason=not_found reasonHint={}",
                    session.sessionId().value(),
                    reason);
            return false;
        }
        List<SeriesKey> attachedSeries = new ArrayList<>(removed.attachedSeries());
        logRegistryHubStateForSeries("session_close_begin", removed, attachedSeries, reason);
        lastDeliveredBarIndexBySessionSeries.remove(removed.sessionId());
        Set<UiSessionId> userSet = sessionsByUser.get(removed.userId());
        if (userSet != null) {
            userSet.remove(removed.sessionId());
            if (userSet.isEmpty()) {
                sessionsByUser.remove(removed.userId(), userSet);
            }
        }
        AttachDetachResult result = detachSeriesInternal(removed, attachedSeries);
        applyUpstreamTransitions(result);
        removed.setState(SessionState.CLOSED);
        StressSessionRegistry stressSessionRegistry = stressSessionRegistryProvider == null
                ? null
                : stressSessionRegistryProvider.getIfAvailable();
        if (stressSessionRegistry != null) {
            stressSessionRegistry.unmarkStressSession(removed.sessionId().value());
        }
        logRegistryHubStateForSeries("session_close_completed", removed, attachedSeries, reason);
        sessionDeleteCounter.increment();
        log.info("ui_session_closed sessionId={} userId={}", removed.sessionId().value(), removed.userId());
        return true;
    }

    private void logRegistryHubStateForSeries(String event, UiSession session, List<SeriesKey> keys, String reason) {
        if (runtimeManager == null || keys == null || keys.isEmpty()) {
            return;
        }
        for (SeriesKey key : keys) {
            var runtimeOpt = runtimeManager.get(key);
            if (runtimeOpt.isEmpty()) {
                log.info("registry_hub_state event={} reason={} sessionId={} userId={} seriesKey={} runtimePresent=false",
                        event,
                        reason,
                        session.sessionId().value(),
                        session.userId(),
                        SeriesKeyCodec.encode(key));
                continue;
            }

            var runtime = runtimeOpt.get();
            var health = runtime.health();
            var state = runtime.registryState();
            log.info("registry_hub_state event={} reason={} sessionId={} userId={} seriesKey={} runtimePresent=true runtimeState={} queueDepth={} scriptCount={} lastSeq={} drawingCount={} drawingOwnerCount={} plotSeriesCount={} plotOwnerCount={} totalPlotPointCount={} alertCount={} intentCount={}",
                    event,
                    reason,
                    session.sessionId().value(),
                    session.userId(),
                    SeriesKeyCodec.encode(key),
                    health.state().name(),
                    health.queueDepth(),
                    health.scriptCount(),
                    state.lastSeq(),
                    state.drawingCount(),
                    state.drawingOwnerCount(),
                    state.plotSeriesCount(),
                    state.plotOwnerCount(),
                    state.totalPlotPointCount(),
                    state.alertCount(),
                    state.intentCount());
        }
    }

    private void removeFromIndexes(UiSession session, SeriesKey key) {
        Set<UiSessionId> seriesSessions = sessionsBySeries.get(key);
        if (seriesSessions != null) {
            seriesSessions.remove(session.sessionId());
            if (seriesSessions.isEmpty()) {
                sessionsBySeries.remove(key, seriesSessions);
            }
        }
        Map<SeriesKey, Integer> deliveredBySeries = lastDeliveredBarIndexBySessionSeries.get(session.sessionId());
        if (deliveredBySeries != null) {
            deliveredBySeries.remove(key);
            if (deliveredBySeries.isEmpty()) {
                lastDeliveredBarIndexBySessionSeries.remove(session.sessionId(), deliveredBySeries);
            }
        }
        Set<UiSessionId> instrumentSessions = sessionsByInstrument.get(key.instrumentToken());
        if (instrumentSessions != null) {
            instrumentSessions.remove(session.sessionId());
            if (instrumentSessions.isEmpty()) {
                sessionsByInstrument.remove(key.instrumentToken(), instrumentSessions);
            }
        }
    }

    private void applyUpstreamTransitions(AttachDetachResult result) {
        if (!result.instrumentsToSubscribe().isEmpty()) {
            upstreamSubscriptionPort.subscribeInternal(UI_SERIES_SUBSCRIBER_ID, new ArrayList<>(result.instrumentsToSubscribe()));
            log.info("instrument_subscribe_transition count={} tokens={}",
                    result.instrumentsToSubscribe().size(), result.instrumentsToSubscribe());
        }
        if (!result.instrumentsToUnsubscribe().isEmpty()) {
            upstreamSubscriptionPort.unsubscribeInternal(UI_SERIES_SUBSCRIBER_ID,
                    new ArrayList<>(result.instrumentsToUnsubscribe()));
            log.info("instrument_unsubscribe_transition count={} tokens={}",
                    result.instrumentsToUnsubscribe().size(), result.instrumentsToUnsubscribe());
        }
    }

    private List<SeriesKey> parseSeriesKeys(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("seriesKeys is required");
        }
        List<SeriesKey> out = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            out.add(SeriesKeyCodec.decode(value.trim()));
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("seriesKeys must contain valid values");
        }
        return out;
    }

    private record AttachDetachResult(
            Set<Long> instrumentsToSubscribe,
            Set<Long> instrumentsToUnsubscribe) {
    }

    public record BootstrapAggregate(
            String status,
            Instant startedAt,
            Instant completedAt,
            Instant cutoffAt,
            String error,
            long seededBars,
            long replayedTicks,
            long droppedTicks) {
    }
}
