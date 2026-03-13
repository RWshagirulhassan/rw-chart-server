package com.readywealth.trading.trade_engine.marketdata.application;

import com.readywealth.trading.trade_engine.engine.application.IntervalKindMapper;
import com.readywealth.trading.trade_engine.engine.domain.series.Candle;
import com.readywealth.trading.trade_engine.engine.domain.series.IntervalKind;
import com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey;
import com.readywealth.trading.trade_engine.engine.application.runtime.SeriesAdvanceEvent;
import com.readywealth.trading.trade_engine.engine.application.runtime.SeriesKeyCodec;
import com.readywealth.trading.trade_engine.engine.application.runtime.SeriesMutationType;
import com.readywealth.trading.trade_engine.engine.application.runtime.SeriesRuntimeManager;
import com.readywealth.trading.trade_engine.marketdata.api.ws.TicksWebSocketBroadcaster;
import com.readywealth.trading.trade_engine.marketdata.api.ws.dto.BootstrapLifecycleEvent;
import com.readywealth.trading.trade_engine.marketdata.application.port.HistoricalCandleClient;
import com.readywealth.trading.trade_engine.marketdata.infrastructure.config.BootstrapProperties;
import com.readywealth.trading.trade_engine.marketdata.infrastructure.config.BootstrapSchedulerProperties;
import com.readywealth.trading.trade_engine.marketdata.infrastructure.http.HistoricalFetchException;
import com.readywealth.trading.trade_engine.session.application.SessionSeriesCoordinator;
import com.readywealth.trading.trade_engine.session.domain.UiSessionId;
import com.readywealth.trading.trade_engine.session.infrastructure.memory.InMemoryConcurrentBarSeriesRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Service
public class SessionBootstrapCoordinator {

    private static final Logger log = LoggerFactory.getLogger(SessionBootstrapCoordinator.class);
    private static final ZoneId EXCHANGE_ZONE = ZoneId.of("Asia/Kolkata");

    private final HistoricalCandleClient historicalCandleClient;
    private final InMemoryConcurrentBarSeriesRegistry seriesRegistry;
    private final ObjectProvider<SessionSeriesCoordinator> sessionSeriesCoordinatorProvider;
    private final ObjectProvider<SeriesRuntimeManager> runtimeManagerProvider;
    private final ObjectProvider<BootstrapTickBufferStore> bootstrapTickBufferStoreProvider;
    private final BootstrapV2ScopeResolver bootstrapV2ScopeResolver;
    private final BootstrapProperties bootstrapProperties;
    private final BootstrapSchedulerProperties schedulerProperties;
    private final TicksWebSocketBroadcaster webSocketBroadcaster;
    private final Counter startedCounter;
    private final Counter completedCounter;
    private final Counter degradedCounter;
    private final Counter seededBarsCounter;
    private final Counter replayedTicksCounter;
    private final Counter droppedTicksCounter;
    private final SessionCandleBoundaryService sessionCandleBoundaryService;
    private final SessionAlignedSeriesIngestor sessionAlignedSeriesIngestor;
    private final boolean liveAlignmentEnabled;

    private final ExecutorService engineExecutor = Executors.newFixedThreadPool(4);
    private final ExecutorService uiBootstrapScheduler = Executors.newSingleThreadExecutor();
    private final BlockingQueue<UiBootstrapJob> uiJobs = new LinkedBlockingQueue<>();
    private final AtomicInteger uiJobsRunning = new AtomicInteger(0);
    private final Map<SeriesKey, Long> queuedOrRunningUiRunIds = new ConcurrentHashMap<>();
    private final Map<SeriesKey, EngineBootstrapState> engineStates = new ConcurrentHashMap<>();

    public SessionBootstrapCoordinator(
            HistoricalCandleClient historicalCandleClient,
            InMemoryConcurrentBarSeriesRegistry seriesRegistry,
            ObjectProvider<SessionSeriesCoordinator> sessionSeriesCoordinatorProvider,
            TicksWebSocketBroadcaster webSocketBroadcaster,
            ObjectProvider<SeriesRuntimeManager> runtimeManagerProvider,
            ObjectProvider<BootstrapTickBufferStore> bootstrapTickBufferStoreProvider,
            BootstrapV2ScopeResolver bootstrapV2ScopeResolver,
            BootstrapProperties bootstrapProperties,
            BootstrapSchedulerProperties schedulerProperties,
            SessionCandleBoundaryService sessionCandleBoundaryService,
            SessionAlignedSeriesIngestor sessionAlignedSeriesIngestor,
            @Value("${session.candles.live-alignment-enabled:false}") boolean liveAlignmentEnabled,
            MeterRegistry meterRegistry) {
        this.historicalCandleClient = historicalCandleClient;
        this.seriesRegistry = seriesRegistry;
        this.sessionSeriesCoordinatorProvider = sessionSeriesCoordinatorProvider;
        this.runtimeManagerProvider = runtimeManagerProvider;
        this.bootstrapTickBufferStoreProvider = bootstrapTickBufferStoreProvider;
        this.bootstrapV2ScopeResolver = bootstrapV2ScopeResolver;
        this.bootstrapProperties = bootstrapProperties;
        this.schedulerProperties = schedulerProperties;
        this.webSocketBroadcaster = webSocketBroadcaster;
        this.sessionCandleBoundaryService = sessionCandleBoundaryService;
        this.sessionAlignedSeriesIngestor = sessionAlignedSeriesIngestor;
        this.liveAlignmentEnabled = liveAlignmentEnabled;
        this.startedCounter = meterRegistry.counter("bootstrap_started_total");
        this.completedCounter = meterRegistry.counter("bootstrap_completed_total");
        this.degradedCounter = meterRegistry.counter("bootstrap_degraded_total");
        this.seededBarsCounter = meterRegistry.counter("bootstrap_seeded_bars_total");
        this.replayedTicksCounter = meterRegistry.counter("bootstrap_replayed_ticks_total");
        this.droppedTicksCounter = meterRegistry.counter("bootstrap_buffer_dropped_ticks_total");
        meterRegistry.gauge("bootstrap_active_jobs", this, SessionBootstrapCoordinator::activeBootstrapJobs);
        meterRegistry.gauge("bootstrap_jobs_queued", uiJobs, BlockingQueue::size);
        meterRegistry.gauge("bootstrap_jobs_running", uiJobsRunning, AtomicInteger::get);

        uiBootstrapScheduler.submit(this::runUiBootstrapScheduler);
    }

    @PreDestroy
    public void shutdown() {
        uiBootstrapScheduler.shutdownNow();
        engineExecutor.shutdownNow();
    }

    public int activeBootstrapJobs() {
        int ui = seriesRegistry.activeBootstrapJobs();
        int engine = 0;
        for (EngineBootstrapState state : engineStates.values()) {
            if (state.snapshot().status() == InMemoryConcurrentBarSeriesRegistry.BootstrapStatus.RUNNING) {
                engine++;
            }
        }
        return ui + engine;
    }

    public void reserveUiSeriesBootstrap(
            String sessionId,
            SeriesKey key,
            InMemoryConcurrentBarSeriesRegistry.SeriesEntry entry) {
        if (entry == null) {
            return;
        }
        InMemoryConcurrentBarSeriesRegistry.BootstrapSnapshot current = entry.bootstrapState().snapshot();
        if (current.status() == InMemoryConcurrentBarSeriesRegistry.BootstrapStatus.RUNNING
                || current.status() == InMemoryConcurrentBarSeriesRegistry.BootstrapStatus.COMPLETED) {
            return;
        }

        boolean v2Enabled = bootstrapV2ScopeResolver.isV2EnabledForSession(sessionId);
        if (v2Enabled && !"redis-stream".equalsIgnoreCase(bootstrapProperties.getStore())) {
            log.warn("bootstrap_v2_forcing_redis_stream seriesKey={} sessionId={} configuredStore={}",
                    key, sessionId, bootstrapProperties.getStore());
        }
        if (v2Enabled && bootstrapTickBufferStoreProvider.getIfAvailable() == null) {
            log.warn("bootstrap_v2_requested_but_buffer_store_missing seriesKey={} sessionId={}",
                    key, sessionId);
            v2Enabled = false;
        }

        Instant cutoff = nextBoundary(Instant.now(), entry.timePeriod(), key.timeframe());
        long runId = entry.bootstrapState().reserve(cutoff, v2Enabled);
        if (runId < 0) {
            return;
        }

        if (v2Enabled) {
            BootstrapTickBufferStore store = bootstrapTickBufferStoreProvider.getIfAvailable();
            if (store != null) {
                try {
                    store.purge(key);
                } catch (RuntimeException purgeError) {
                    log.warn("bootstrap_v2_buffer_purge_failed seriesKey={} runId={} reason={}",
                            key, runId, purgeError.getMessage());
                }
            }
        }

        startedCounter.increment();
        log.info("bootstrap_reserved seriesKey={} runId={} cutoff={} v2Enabled={}", key, runId, cutoff, v2Enabled);
        emitBootstrapEvent("bootstrap_started", key, entry);
    }

    public void bootstrapUiSeriesAsync(SeriesKey key) {
        InMemoryConcurrentBarSeriesRegistry.SeriesEntry entry = seriesRegistry.get(key).orElse(null);
        if (entry == null) {
            return;
        }

        InMemoryConcurrentBarSeriesRegistry.BootstrapSnapshot snapshot = entry.bootstrapState().snapshot();
        long runId = snapshot.runId();
        Instant cutoff = snapshot.cutoffAt();
        boolean v2Enabled = snapshot.v2Enabled();

        if (snapshot.status() != InMemoryConcurrentBarSeriesRegistry.BootstrapStatus.RUNNING
                || runId <= 0
                || cutoff == null) {
            cutoff = nextBoundary(Instant.now(), entry.timePeriod(), key.timeframe());
            runId = entry.bootstrapState().tryStart(cutoff);
            if (runId < 0) {
                return;
            }
            startedCounter.increment();
            log.info("bootstrap_start seriesKey={} runId={} cutoff={} v2Enabled=false", key, runId, cutoff);
            emitBootstrapEvent("bootstrap_started", key, entry);
            v2Enabled = false;
        }

        enqueueUiBootstrap(new UiBootstrapJob(key, runId, cutoff, v2Enabled));
    }

    public void bootstrapEngineSeriesAsync(SeriesKey key, Consumer<List<Candle>> ingestConsumer) {
        Duration timeframe;
        try {
            timeframe = IntervalKindMapper.toDuration(key.timeframe());
        } catch (Exception ex) {
            EngineBootstrapState state = engineStates.computeIfAbsent(key, ignored -> new EngineBootstrapState());
            long runId = state.tryStart(null);
            state.markDegraded(runId, "unsupported_timeframe_for_bootstrap");
            degradedCounter.increment();
            return;
        }
        EngineBootstrapState state = engineStates.computeIfAbsent(key, ignored -> new EngineBootstrapState());
        Instant cutoff = nextBoundary(Instant.now(), timeframe, key.timeframe());
        long runId = state.tryStart(cutoff);
        if (runId < 0) {
            return;
        }
        startedCounter.increment();

        CompletableFuture.runAsync(() -> {
            try {
                List<Candle> recent = historicalCandleClient.fetchRecentCandles(key.instrumentToken(), key.timeframe());
                List<Candle> seed = normalizeAndFilter(recent, key, timeframe, cutoff.toEpochMilli());
                ingestConsumer.accept(seed);
                state.markSeeded(seed.size());
                seededBarsCounter.increment(seed.size());
                state.markCompleted(runId);
                completedCounter.increment();
            } catch (Exception e) {
                state.markDegraded(runId, e.getMessage());
                degradedCounter.increment();
            }
        }, engineExecutor);
    }

    public InMemoryConcurrentBarSeriesRegistry.BootstrapSnapshot engineSnapshot(SeriesKey key) {
        EngineBootstrapState state = engineStates.get(key);
        if (state == null) {
            return new InMemoryConcurrentBarSeriesRegistry.BootstrapSnapshot(
                    InMemoryConcurrentBarSeriesRegistry.BootstrapStatus.PENDING,
                    InMemoryConcurrentBarSeriesRegistry.BootstrapLifecycle.NOT_STARTED,
                    null,
                    null,
                    null,
                    null,
                    0,
                    0,
                    0,
                    0,
                    false);
        }
        return state.snapshot();
    }

    private void enqueueUiBootstrap(UiBootstrapJob job) {
        Long existing = queuedOrRunningUiRunIds.putIfAbsent(job.key(), job.runId());
        if (existing != null) {
            return;
        }
        if (!uiJobs.offer(job)) {
            queuedOrRunningUiRunIds.remove(job.key(), job.runId());
        }
    }

    private void runUiBootstrapScheduler() {
        while (!Thread.currentThread().isInterrupted()) {
            UiBootstrapJob job;
            try {
                job = uiJobs.take();
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return;
            }

            uiJobsRunning.incrementAndGet();
            try {
                processUiBootstrapJob(job);
            } catch (BootstrapCancelledException ignored) {
                log.debug("bootstrap_job_cancelled seriesKey={} runId={}", job.key(), job.runId());
            } catch (Exception ex) {
                log.error("bootstrap_job_failed_unexpected seriesKey={} runId={} reason={}",
                        job.key(), job.runId(), ex.getMessage(), ex);
            } finally {
                uiJobsRunning.decrementAndGet();
                queuedOrRunningUiRunIds.remove(job.key(), job.runId());
            }
        }
    }

    private void processUiBootstrapJob(UiBootstrapJob job) {
        InMemoryConcurrentBarSeriesRegistry.SeriesEntry entry = seriesRegistry.get(job.key()).orElse(null);
        if (entry == null) {
            throw new BootstrapCancelledException();
        }
        InMemoryConcurrentBarSeriesRegistry.BootstrapSnapshot snapshot = entry.bootstrapState().snapshot();
        if (snapshot.runId() != job.runId()
                || snapshot.status() != InMemoryConcurrentBarSeriesRegistry.BootstrapStatus.RUNNING) {
            throw new BootstrapCancelledException();
        }

        if (entry.bootstrapState().markRunning(job.runId())) {
            emitBootstrapEvent("bootstrap_running", job.key(), entry);
        }

        Instant cutoff = snapshot.cutoffAt() == null ? job.cutoff() : snapshot.cutoffAt();

        try {
            List<Candle> recent = fetchRecentWithRetry(job.key(), job.runId());
            List<Candle> seed = normalizeAndFilter(recent, job.key(), entry.timePeriod(), cutoff.toEpochMilli());
            List<Bar> bars = toBars(entry, seed);

            int beforeBegin = entry.series().getBeginIndex();
            int beforeEnd = entry.series().getEndIndex();
            entry.series().withWriteLock(() -> entry.series().ingestStreamingBars(bars));
            int afterBegin = entry.series().getBeginIndex();
            int afterEnd = entry.series().getEndIndex();
            SeriesRuntimeManager runtimeManager = runtimeManagerProvider.getIfAvailable();
            if (runtimeManager != null && (afterBegin != beforeBegin || afterEnd != beforeEnd)) {
                runtimeManager.onSeriesAdvanced(
                        job.key(),
                        new SeriesAdvanceEvent(
                                beforeBegin,
                                beforeEnd,
                                afterBegin,
                                afterEnd,
                                SeriesMutationType.BOOTSTRAP_SEED,
                                System.currentTimeMillis()));
            }
            entry.bootstrapState().markSeeded(seed.size());
            seededBarsCounter.increment(seed.size());

            if (entry.bootstrapState().markReplaying(job.runId())) {
                emitBootstrapEvent("bootstrap_replaying", job.key(), entry);
            }

            long replayed = replayTicks(entry, cutoff.toEpochMilli(), job.runId(), job.v2Enabled());
            entry.bootstrapState().markReplayed(replayed);
            replayedTicksCounter.increment(replayed);

            if (job.v2Enabled()) {
                BootstrapTickBufferStore store = bootstrapTickBufferStoreProvider.getIfAvailable();
                if (store != null) {
                    store.purge(job.key());
                }
            }

            long dropped = entry.bootstrapState().snapshot().droppedTicks();
            if (dropped > 0) {
                droppedTicksCounter.increment(dropped);
            }

            entry.bootstrapState().markCompleted(job.runId());
            if (entry.bootstrapState().snapshot().status() == InMemoryConcurrentBarSeriesRegistry.BootstrapStatus.DEGRADED) {
                degradedCounter.increment();
                log.warn("bootstrap_degraded seriesKey={} runId={} error={}",
                        job.key(), job.runId(), entry.bootstrapState().snapshot().error());
                emitBootstrapEvent("bootstrap_degraded", job.key(), entry);
            } else {
                completedCounter.increment();
                log.info("bootstrap_completed seriesKey={} runId={} seededBars={} replayedTicks={} v2Enabled={}",
                        job.key(), job.runId(), seed.size(), replayed, job.v2Enabled());
                emitBootstrapEvent("bootstrap_completed", job.key(), entry);
            }
            emitBootstrapEvent("snapshot_ready", job.key(), entry);
        } catch (HistoricalFetchException historicalFetchException) {
            entry.bootstrapState().markDegraded(job.runId(), historicalFetchException.getMessage());
            degradedCounter.increment();
            log.warn("bootstrap_degraded seriesKey={} runId={} transient={} reason={}",
                    job.key(), job.runId(), historicalFetchException.isTransientFailure(),
                    historicalFetchException.getMessage());
            emitBootstrapEvent("bootstrap_degraded", job.key(), entry);
            emitBootstrapEvent("snapshot_ready", job.key(), entry);
        } catch (Exception exception) {
            entry.bootstrapState().markDegraded(job.runId(), exception.getMessage());
            degradedCounter.increment();
            log.warn("bootstrap_degraded seriesKey={} runId={} reason={}",
                    job.key(), job.runId(), exception.toString());
            emitBootstrapEvent("bootstrap_degraded", job.key(), entry);
            emitBootstrapEvent("snapshot_ready", job.key(), entry);
        }
    }

    private List<Candle> fetchRecentWithRetry(SeriesKey key, long runId) {
        long attempt = 0L;
        while (true) {
            ensureUiRunStillActive(key, runId);
            try {
                return historicalCandleClient.fetchRecentCandlesForBootstrap(key.instrumentToken(), key.timeframe());
            } catch (HistoricalFetchException historicalFetchException) {
                if (!historicalFetchException.isTransientFailure()) {
                    throw historicalFetchException;
                }
                attempt++;
                long backoffMs = backoffWithJitterMs(attempt);
                log.warn("bootstrap_historical_retry seriesKey={} runId={} attempt={} waitMs={} reason={}",
                        key, runId, attempt, backoffMs, historicalFetchException.getMessage());
                sleepQuietly(backoffMs);
            }
        }
    }

    private void ensureUiRunStillActive(SeriesKey key, long runId) {
        InMemoryConcurrentBarSeriesRegistry.SeriesEntry entry = seriesRegistry.get(key).orElse(null);
        if (entry == null) {
            throw new BootstrapCancelledException();
        }
        InMemoryConcurrentBarSeriesRegistry.BootstrapSnapshot snapshot = entry.bootstrapState().snapshot();
        if (snapshot.status() != InMemoryConcurrentBarSeriesRegistry.BootstrapStatus.RUNNING || snapshot.runId() != runId) {
            throw new BootstrapCancelledException();
        }
    }

    private long backoffWithJitterMs(long attempt) {
        long initialDelay = Math.max(1L, schedulerProperties.getRetry().getInitialDelayMs());
        long maxDelay = Math.max(initialDelay, schedulerProperties.getRetry().getMaxDelayMs());
        double jitterRatio = Math.max(0d, schedulerProperties.getRetry().getJitterRatio());
        long raw = initialDelay;
        for (long i = 1; i < attempt; i++) {
            if (raw >= maxDelay / 2) {
                raw = maxDelay;
                break;
            }
            raw = raw * 2;
        }
        long capped = Math.min(maxDelay, raw);
        if (jitterRatio <= 0d) {
            return Math.max(1L, capped);
        }
        double min = Math.max(0d, 1d - jitterRatio);
        double max = 1d + jitterRatio;
        double factor = ThreadLocalRandom.current().nextDouble(min, max);
        return Math.max(1L, Math.round(capped * factor));
    }

    private void sleepQuietly(long millis) {
        if (millis <= 0L) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new BootstrapCancelledException();
        }
    }

    private long replayTicks(
            InMemoryConcurrentBarSeriesRegistry.SeriesEntry entry,
            long cutoffEpochMs,
            long runId,
            boolean v2Enabled) {
        List<ReplayTick> ticks;
        if (v2Enabled) {
            BootstrapTickBufferStore store = bootstrapTickBufferStoreProvider.getIfAvailable();
            if (store == null) {
                throw new IllegalStateException("bootstrap_v2_buffer_store_missing");
            }
            ticks = store.readAll(entry.seriesKey(), runId).stream()
                    .filter(tick -> tick.timestampMs() > cutoffEpochMs)
                    .map(tick -> new ReplayTick(tick.timestampMs(), tick.price(), tick.volume()))
                    .toList();
        } else {
            ticks = seriesRegistry.drainBufferedTicksAfter(entry, cutoffEpochMs).stream()
                    .map(tick -> new ReplayTick(tick.timestampMs(), tick.price(), tick.volume()))
                    .sorted(Comparator.comparingLong(ReplayTick::timestampMs))
                    .toList();
        }
        if (ticks.isEmpty()) {
            return 0L;
        }

        int beforeBegin = entry.series().getBeginIndex();
        int beforeEnd = entry.series().getEndIndex();
        long replayed = 0L;
        if (liveAlignmentEnabled) {
            for (ReplayTick tick : ticks) {
                SessionAlignedSeriesIngestor.IngestResult result = sessionAlignedSeriesIngestor.ingest(
                        entry,
                        tick.timestampMs(),
                        tick.price(),
                        tick.volume());
                if (result == SessionAlignedSeriesIngestor.IngestResult.DROPPED_OUT_OF_ORDER_BUCKET
                        || result == SessionAlignedSeriesIngestor.IngestResult.DROPPED_OUT_OF_SESSION) {
                    continue;
                }
                entry.lastIngestTimestampMs().set(Math.max(entry.lastIngestTimestampMs().get(), tick.timestampMs()));
                replayed++;
            }
        } else {
            entry.series().withWriteLock(() -> {
                for (ReplayTick tick : ticks) {
                    entry.series().ingestTrade(
                            Instant.ofEpochMilli(tick.timestampMs()),
                            entry.series().numFactory().numOf(tick.volume()),
                            entry.series().numFactory().numOf(tick.price()));
                    entry.lastIngestTimestampMs().set(Math.max(entry.lastIngestTimestampMs().get(), tick.timestampMs()));
                }
            });
            replayed = ticks.size();
        }

        SeriesRuntimeManager runtimeManager = runtimeManagerProvider.getIfAvailable();
        if (runtimeManager != null) {
            runtimeManager.onSeriesAdvanced(
                    entry.seriesKey(),
                    new SeriesAdvanceEvent(
                            beforeBegin,
                            beforeEnd,
                            entry.series().getBeginIndex(),
                            entry.series().getEndIndex(),
                            SeriesMutationType.BOOTSTRAP_REPLAY,
                            System.currentTimeMillis()));
        }
        return replayed;
    }

    private void emitBootstrapEvent(String type, SeriesKey key, InMemoryConcurrentBarSeriesRegistry.SeriesEntry entry) {
        SessionSeriesCoordinator coordinator = sessionSeriesCoordinatorProvider.getIfAvailable();
        if (coordinator == null) {
            return;
        }
        Set<UiSessionId> sessionIds = coordinator.sessionsForSeries(key);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return;
        }
        InMemoryConcurrentBarSeriesRegistry.BootstrapSnapshot snapshot = entry.bootstrapState().snapshot();
        BootstrapLifecycleEvent event = new BootstrapLifecycleEvent(
                null,
                SeriesKeyCodec.encode(key),
                snapshot.status().name(),
                snapshot.cutoffAt(),
                snapshot.seededBars(),
                snapshot.replayedTicks(),
                snapshot.droppedTicks(),
                snapshot.error(),
                entry.lastSeq().get(),
                Instant.now());
        webSocketBroadcaster.broadcastBootstrapEventToSessions(type, sessionIds, event);
    }

    private static List<Candle> normalizeAndFilter(List<Candle> source, SeriesKey key, Duration timePeriod, long cutoffEpochMs) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<Candle> out = new ArrayList<>();
        long barMs = Math.max(1, timePeriod.toMillis());
        for (Candle candle : source) {
            long start = candle.startTimeEpochMs();
            long end = start + barMs;
            if (end <= cutoffEpochMs) {
                out.add(new Candle(
                        key.instrumentToken(),
                        key.timeframe(),
                        start,
                        end,
                        candle.open(),
                        candle.high(),
                        candle.low(),
                        candle.close(),
                        candle.volume(),
                        candle.tickCount(),
                        candle.barVersion(),
                        candle.cause()));
            }
        }
        out.sort(Comparator.comparingLong(Candle::startTimeEpochMs));
        return out;
    }

    private static List<Bar> toBars(InMemoryConcurrentBarSeriesRegistry.SeriesEntry entry, List<Candle> candles) {
        List<Bar> out = new ArrayList<>(candles.size());
        for (Candle candle : candles) {
            out.add(entry.series().barBuilder()
                    .timePeriod(Duration.ofMillis(Math.max(1, candle.endTimeEpochMs() - candle.startTimeEpochMs())))
                    .beginTime(Instant.ofEpochMilli(candle.startTimeEpochMs()))
                    .endTime(Instant.ofEpochMilli(candle.endTimeEpochMs()))
                    .openPrice(candle.open())
                    .highPrice(candle.high())
                    .lowPrice(candle.low())
                    .closePrice(candle.close())
                    .volume(candle.volume())
                    .amount(candle.close() * candle.volume())
                    .trades(Math.max(1L, candle.tickCount()))
                    .build());
        }
        return out;
    }

    private Instant nextBoundary(Instant now, Duration timeframe, IntervalKind intervalKind) {
        if (liveAlignmentEnabled) {
            return sessionCandleBoundaryService.nextBucketBoundary(now, timeframe, intervalKind);
        }
        return legacyNextBoundary(now, timeframe);
    }

    private static Instant legacyNextBoundary(Instant now, Duration timeframe) {
        ZonedDateTime zdt = now.atZone(EXCHANGE_ZONE).withNano(0);
        long minutes = timeframe.toMinutes();
        if (minutes >= 1440) {
            return ZonedDateTime.of(zdt.toLocalDate().plusDays(1), LocalTime.MIDNIGHT, EXCHANGE_ZONE).toInstant();
        }
        if (minutes >= 60 && minutes % 60 == 0) {
            long hours = minutes / 60;
            int flooredHour = (int) ((zdt.getHour() / hours) * hours);
            ZonedDateTime floor = zdt.withHour(flooredHour).withMinute(0).withSecond(0);
            return floor.plusHours(hours).toInstant();
        }
        int step = (int) Math.max(1, minutes);
        int flooredMinute = (zdt.getMinute() / step) * step;
        ZonedDateTime floor = zdt.withMinute(flooredMinute).withSecond(0);
        return floor.plusMinutes(step).toInstant();
    }

    private record UiBootstrapJob(
            SeriesKey key,
            long runId,
            Instant cutoff,
            boolean v2Enabled) {
    }

    private record ReplayTick(long timestampMs, double price, double volume) {
    }

    private static final class BootstrapCancelledException extends RuntimeException {
    }

    private static final class EngineBootstrapState {
        private InMemoryConcurrentBarSeriesRegistry.BootstrapStatus status = InMemoryConcurrentBarSeriesRegistry.BootstrapStatus.PENDING;
        private Instant startedAt;
        private Instant completedAt;
        private Instant cutoffAt;
        private String error;
        private long seededBars;
        private long replayedTicks;
        private long droppedTicks;
        private long runId;

        private synchronized long tryStart(Instant cutoff) {
            if (status == InMemoryConcurrentBarSeriesRegistry.BootstrapStatus.RUNNING
                    || status == InMemoryConcurrentBarSeriesRegistry.BootstrapStatus.COMPLETED) {
                return -1;
            }
            runId++;
            status = InMemoryConcurrentBarSeriesRegistry.BootstrapStatus.RUNNING;
            startedAt = Instant.now();
            completedAt = null;
            cutoffAt = cutoff;
            error = null;
            seededBars = 0;
            replayedTicks = 0;
            droppedTicks = 0;
            return runId;
        }

        private synchronized void markSeeded(long seededBars) {
            this.seededBars = Math.max(0, seededBars);
        }

        private synchronized void markCompleted(long expectedRunId) {
            if (expectedRunId != runId) {
                return;
            }
            if (status == InMemoryConcurrentBarSeriesRegistry.BootstrapStatus.RUNNING) {
                status = InMemoryConcurrentBarSeriesRegistry.BootstrapStatus.COMPLETED;
            }
            completedAt = Instant.now();
        }

        private synchronized void markDegraded(long expectedRunId, String reason) {
            if (expectedRunId != runId) {
                return;
            }
            status = InMemoryConcurrentBarSeriesRegistry.BootstrapStatus.DEGRADED;
            error = reason == null ? "bootstrap_failed" : reason;
            completedAt = Instant.now();
        }

        private synchronized InMemoryConcurrentBarSeriesRegistry.BootstrapSnapshot snapshot() {
            return new InMemoryConcurrentBarSeriesRegistry.BootstrapSnapshot(
                    status,
                    status == InMemoryConcurrentBarSeriesRegistry.BootstrapStatus.PENDING
                            ? InMemoryConcurrentBarSeriesRegistry.BootstrapLifecycle.NOT_STARTED
                            : status == InMemoryConcurrentBarSeriesRegistry.BootstrapStatus.RUNNING
                                    ? InMemoryConcurrentBarSeriesRegistry.BootstrapLifecycle.RUNNING
                                    : status == InMemoryConcurrentBarSeriesRegistry.BootstrapStatus.COMPLETED
                                            ? InMemoryConcurrentBarSeriesRegistry.BootstrapLifecycle.COMPLETED
                                            : InMemoryConcurrentBarSeriesRegistry.BootstrapLifecycle.DEGRADED,
                    startedAt,
                    completedAt,
                    cutoffAt,
                    error,
                    seededBars,
                    replayedTicks,
                    droppedTicks,
                    runId,
                    false);
        }
    }
}
