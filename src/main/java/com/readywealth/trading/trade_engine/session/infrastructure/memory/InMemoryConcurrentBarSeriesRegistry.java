package com.readywealth.trading.trade_engine.session.infrastructure.memory;

import com.readywealth.trading.trade_engine.engine.application.IntervalKindMapper;
import com.readywealth.trading.trade_engine.engine.domain.series.BarSeriesContext;
import com.readywealth.trading.trade_engine.engine.domain.series.SeriesConfig;
import com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey;
import org.springframework.stereotype.Component;
import org.ta4j.core.ConcurrentBarSeries;
import org.ta4j.core.ConcurrentBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.NumFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class InMemoryConcurrentBarSeriesRegistry {

    private final Map<SeriesKey, SeriesEntry> entries = new ConcurrentHashMap<>();
    private final Map<Long, Set<SeriesKey>> seriesByInstrument = new ConcurrentHashMap<>();

    public AcquireResult acquire(SeriesKey key, int maxBarCount) {
        SeriesEntry entry = entries.compute(key, (k, existing) -> {
            if (existing != null) {
                existing.seriesRefCount().incrementAndGet();
                return existing;
            }
            NumFactory numFactory = DecimalNumFactory.getInstance();
            ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder()
                    .withName(key.toString())
                    .withNumFactory(numFactory)
                    .withMaxBarCount(Math.max(1, maxBarCount))
                    .build();
            Duration timePeriod = IntervalKindMapper.toDuration(key.timeframe());
            series.tradeBarBuilder().timePeriod(timePeriod);
            BarSeriesContext context = new BarSeriesContext(key, series, numFactory, new SeriesConfig(maxBarCount));
            return new SeriesEntry(key, series, context, new AtomicInteger(1), new AtomicLong(0), maxBarCount, timePeriod,
                    new AtomicLong(Long.MIN_VALUE), new BootstrapState(), new ConcurrentLinkedDeque<>());
        });

        seriesByInstrument
                .computeIfAbsent(key.instrumentToken(), ignored -> ConcurrentHashMap.newKeySet())
                .add(key);

        boolean created = entry.seriesRefCount().get() == 1;
        return new AcquireResult(entry, created);
    }

    public ReleaseResult release(SeriesKey key) {
        SeriesEntry existing = entries.get(key);
        if (existing == null) {
            return new ReleaseResult(false, false, 0);
        }

        int remaining = existing.seriesRefCount().updateAndGet(value -> Math.max(0, value - 1));
        boolean removed = false;
        if (remaining == 0) {
            removed = entries.remove(key, existing);
            if (removed) {
                Set<SeriesKey> set = seriesByInstrument.get(key.instrumentToken());
                if (set != null) {
                    set.remove(key);
                    if (set.isEmpty()) {
                        seriesByInstrument.remove(key.instrumentToken(), set);
                    }
                }
            }
        }
        return new ReleaseResult(true, removed, remaining);
    }

    public Optional<SeriesEntry> get(SeriesKey key) {
        return Optional.ofNullable(entries.get(key));
    }

    public List<SeriesEntry> entriesForInstrument(long instrumentToken) {
        Set<SeriesKey> keys = seriesByInstrument.get(instrumentToken);
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<SeriesEntry> out = new ArrayList<>();
        for (SeriesKey key : keys) {
            SeriesEntry entry = entries.get(key);
            if (entry != null) {
                out.add(entry);
            }
        }
        return out;
    }

    public int size() {
        return entries.size();
    }

    public int totalRefs() {
        return entries.values().stream().mapToInt(e -> e.seriesRefCount().get()).sum();
    }

    public int activeBootstrapJobs() {
        int active = 0;
        for (SeriesEntry entry : entries.values()) {
            BootstrapSnapshot snapshot = entry.bootstrapState().snapshot();
            if (snapshot.status() == BootstrapStatus.RUNNING) {
                active++;
            }
        }
        return active;
    }

    public boolean appendBootstrapTick(SeriesEntry entry, long timestampMs, double price, double volume, int capacity) {
        if (!entry.bootstrapState().acceptsBufferedTicks()) {
            return false;
        }
        if (capacity < 1) {
            capacity = 1;
        }
        entry.bootstrapBuffer().addLast(new BufferedTick(timestampMs, price, volume));
        boolean overflow = false;
        while (entry.bootstrapBuffer().size() > capacity) {
            BufferedTick removed = entry.bootstrapBuffer().pollFirst();
            if (removed != null) {
                overflow = true;
            }
        }
        if (overflow) {
            entry.bootstrapState().markDroppedTicks(1, "buffer_overflow_tick_loss");
        }
        return true;
    }

    public List<BufferedTick> drainBufferedTicksAfter(SeriesEntry entry, long cutoffEpochMs) {
        List<BufferedTick> out = new ArrayList<>();
        for (BufferedTick tick : entry.bootstrapBuffer()) {
            if (tick.timestampMs() > cutoffEpochMs) {
                out.add(tick);
            }
        }
        entry.bootstrapBuffer().clear();
        out.sort(Comparator.comparingLong(BufferedTick::timestampMs));
        return out;
    }

    public record SeriesEntry(
            SeriesKey seriesKey,
            ConcurrentBarSeries series,
            BarSeriesContext context,
            AtomicInteger seriesRefCount,
            AtomicLong lastSeq,
            int maxBarCount,
            Duration timePeriod,
            AtomicLong lastIngestTimestampMs,
            BootstrapState bootstrapState,
            ConcurrentLinkedDeque<BufferedTick> bootstrapBuffer) {
    }

    public record AcquireResult(SeriesEntry entry, boolean created) {
    }

    public record ReleaseResult(boolean existed, boolean removed, int remainingRefs) {
    }

    public record BufferedTick(long timestampMs, double price, double volume) {
    }

    public enum BootstrapStatus {
        PENDING, RUNNING, COMPLETED, DEGRADED
    }

    public enum BootstrapLifecycle {
        NOT_STARTED,
        BUFFERING,
        RUNNING,
        REPLAYING,
        COMPLETED,
        DEGRADED
    }

    public record BootstrapSnapshot(
            BootstrapStatus status,
            BootstrapLifecycle lifecycle,
            Instant startedAt,
            Instant completedAt,
            Instant cutoffAt,
            String error,
            long seededBars,
            long replayedTicks,
            long droppedTicks,
            long runId,
            boolean v2Enabled) {
    }

    public static final class BootstrapState {
        private final AtomicLong runIdCounter = new AtomicLong(0);
        private BootstrapStatus status = BootstrapStatus.PENDING;
        private BootstrapLifecycle lifecycle = BootstrapLifecycle.NOT_STARTED;
        private Instant startedAt;
        private Instant completedAt;
        private Instant cutoffAt;
        private String error;
        private long seededBars;
        private long replayedTicks;
        private long droppedTicks;
        private long runId;
        private boolean v2Enabled;

        public synchronized long tryStart(Instant cutoff) {
            if (status == BootstrapStatus.RUNNING || status == BootstrapStatus.COMPLETED) {
                return -1L;
            }
            runId = runIdCounter.incrementAndGet();
            status = BootstrapStatus.RUNNING;
            lifecycle = BootstrapLifecycle.RUNNING;
            startedAt = Instant.now();
            completedAt = null;
            cutoffAt = cutoff;
            error = null;
            seededBars = 0;
            replayedTicks = 0;
            droppedTicks = 0;
            v2Enabled = false;
            return runId;
        }

        public synchronized long reserve(Instant cutoff, boolean useV2Buffering) {
            if (status == BootstrapStatus.RUNNING || status == BootstrapStatus.COMPLETED) {
                return -1L;
            }
            runId = runIdCounter.incrementAndGet();
            status = BootstrapStatus.RUNNING;
            lifecycle = useV2Buffering ? BootstrapLifecycle.BUFFERING : BootstrapLifecycle.RUNNING;
            startedAt = Instant.now();
            completedAt = null;
            cutoffAt = cutoff;
            error = null;
            seededBars = 0;
            replayedTicks = 0;
            droppedTicks = 0;
            v2Enabled = useV2Buffering;
            return runId;
        }

        public synchronized boolean markRunning(long expectedRunId) {
            if (expectedRunId != runId) {
                return false;
            }
            if (status != BootstrapStatus.RUNNING) {
                return false;
            }
            lifecycle = BootstrapLifecycle.RUNNING;
            return true;
        }

        public synchronized boolean markReplaying(long expectedRunId) {
            if (expectedRunId != runId) {
                return false;
            }
            if (status != BootstrapStatus.RUNNING) {
                return false;
            }
            lifecycle = BootstrapLifecycle.REPLAYING;
            return true;
        }

        public synchronized void markSeeded(long count) {
            this.seededBars = Math.max(0, count);
        }

        public synchronized void markReplayed(long count) {
            this.replayedTicks = Math.max(0, count);
        }

        public synchronized void markDroppedTicks(long count, String reason) {
            this.droppedTicks += Math.max(0, count);
            this.status = BootstrapStatus.DEGRADED;
            this.lifecycle = BootstrapLifecycle.DEGRADED;
            this.error = reason == null ? "bootstrap_dropped_ticks" : reason;
        }

        public synchronized void markCompleted(long expectedRunId) {
            if (expectedRunId != runId) {
                return;
            }
            if (status == BootstrapStatus.RUNNING) {
                status = BootstrapStatus.COMPLETED;
            }
            if (lifecycle != BootstrapLifecycle.DEGRADED) {
                lifecycle = BootstrapLifecycle.COMPLETED;
            }
            completedAt = Instant.now();
        }

        public synchronized void markDegraded(long expectedRunId, String reason) {
            if (expectedRunId != runId) {
                return;
            }
            status = BootstrapStatus.DEGRADED;
            lifecycle = BootstrapLifecycle.DEGRADED;
            error = reason == null ? "bootstrap_failed" : reason;
            completedAt = Instant.now();
        }

        public synchronized boolean acceptsBufferedTicks() {
            return lifecycle == BootstrapLifecycle.BUFFERING
                    || lifecycle == BootstrapLifecycle.RUNNING
                    || lifecycle == BootstrapLifecycle.REPLAYING;
        }

        public synchronized BootstrapSnapshot snapshot() {
            return new BootstrapSnapshot(
                    status,
                    lifecycle,
                    startedAt,
                    completedAt,
                    cutoffAt,
                    error,
                    seededBars,
                    replayedTicks,
                    droppedTicks,
                    runId,
                    v2Enabled);
        }
    }
}
