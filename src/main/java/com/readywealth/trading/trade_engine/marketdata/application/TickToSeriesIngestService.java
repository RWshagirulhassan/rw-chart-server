package com.readywealth.trading.trade_engine.marketdata.application;

import com.readywealth.trading.trade_engine.session.api.http.dto.CandleAppendedEvent;
import com.readywealth.trading.trade_engine.session.api.http.dto.SeriesBarView;
import com.readywealth.trading.trade_engine.execution.application.service.StrategyIntentDispatchService;
import com.readywealth.trading.trade_engine.marketdata.api.ws.dto.ScriptDeltaWsEvent;
import com.readywealth.trading.trade_engine.engine.application.runtime.SeriesKeyCodec;
import com.readywealth.trading.trade_engine.engine.application.runtime.SeriesAdvanceEvent;
import com.readywealth.trading.trade_engine.engine.application.runtime.SeriesMutationType;
import com.readywealth.trading.trade_engine.engine.application.runtime.SeriesRuntimeManager;
import com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey;
import com.readywealth.trading.trade_engine.engine.application.runtime.model.DeltaResponse;
import com.readywealth.trading.trade_engine.engine.application.runtime.model.SessionEvent;
import com.readywealth.trading.trade_engine.engine.application.runtime.model.TradeIntent;
import com.readywealth.trading.trade_engine.session.application.SessionSeriesCoordinator;
import com.readywealth.trading.trade_engine.session.domain.UiSessionId;
import com.readywealth.trading.trade_engine.session.infrastructure.memory.InMemoryConcurrentBarSeriesRegistry;
import com.readywealth.trading.trade_engine.marketdata.domain.IndexQuote;
import com.readywealth.trading.trade_engine.marketdata.domain.TickQuote;
import com.readywealth.trading.trade_engine.marketdata.domain.TypedTick;
import com.readywealth.trading.trade_engine.marketdata.infrastructure.config.BootstrapProperties;
import com.readywealth.trading.trade_engine.marketdata.api.ws.TicksWebSocketBroadcaster;
import com.zerodhatech.models.Tick;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class TickToSeriesIngestService {

    private static final Logger log = LoggerFactory.getLogger(TickToSeriesIngestService.class);
    private static final ZoneId CLIENT_ZONE = ZoneId.of("Asia/Kolkata");

    private final InMemoryConcurrentBarSeriesRegistry seriesRegistry;
    private final SeriesRuntimeManager runtimeManager;
    private final ObjectProvider<SessionSeriesCoordinator> sessionSeriesCoordinatorProvider;
    private final ObjectProvider<BootstrapTickBufferStore> bootstrapTickBufferStoreProvider;
    private final TicksWebSocketBroadcaster webSocketBroadcaster;
    private final Counter candleAppendedCounter;
    private final Counter candleLiveUpsertCounter;
    private final Counter outOfOrderDroppedCounter;
    private final Counter bucketOutOfOrderDroppedCounter;
    private final Counter missingTimestampDroppedCounter;
    private final Counter volumeFromCumulativeDeltaCounter;
    private final Counter volumeFromLtqFallbackCounter;
    private final Counter volumeZeroFallbackCounter;
    private final AtomicLong lastMissingTimestampWarnMs = new AtomicLong(0);
    private final ConcurrentMap<Long, Long> lastCumulativeVolumeByInstrument = new ConcurrentHashMap<>();
    private final BootstrapProperties bootstrapProperties;
    private final SessionAlignedSeriesIngestor sessionAlignedSeriesIngestor;
    private final boolean liveAlignmentEnabled;
    @Autowired(required = false)
    private ObjectProvider<StrategyIntentDispatchService> strategyIntentDispatchServiceProvider;

    @Autowired
    public TickToSeriesIngestService(
            InMemoryConcurrentBarSeriesRegistry seriesRegistry,
            SeriesRuntimeManager runtimeManager,
            ObjectProvider<SessionSeriesCoordinator> sessionSeriesCoordinatorProvider,
            ObjectProvider<BootstrapTickBufferStore> bootstrapTickBufferStoreProvider,
            TicksWebSocketBroadcaster webSocketBroadcaster,
            BootstrapProperties bootstrapProperties,
            SessionAlignedSeriesIngestor sessionAlignedSeriesIngestor,
            @Value("${session.candles.live-alignment-enabled:false}")
            boolean liveAlignmentEnabled,
            MeterRegistry meterRegistry) {
        this.seriesRegistry = seriesRegistry;
        this.runtimeManager = runtimeManager;
        this.sessionSeriesCoordinatorProvider = sessionSeriesCoordinatorProvider;
        this.bootstrapTickBufferStoreProvider = bootstrapTickBufferStoreProvider;
        this.webSocketBroadcaster = webSocketBroadcaster;
        this.bootstrapProperties = bootstrapProperties;
        this.sessionAlignedSeriesIngestor = sessionAlignedSeriesIngestor;
        this.liveAlignmentEnabled = liveAlignmentEnabled;
        this.candleAppendedCounter = meterRegistry.counter("candle_appended_events");
        this.candleLiveUpsertCounter = meterRegistry.counter("candle_live_upsert_events");
        this.outOfOrderDroppedCounter = meterRegistry.counter("tick_out_of_order_dropped");
        this.bucketOutOfOrderDroppedCounter = meterRegistry.counter("tick_bucket_out_of_order_dropped");
        this.missingTimestampDroppedCounter = meterRegistry.counter("tick_timestamp_missing_dropped");
        this.volumeFromCumulativeDeltaCounter = meterRegistry.counter("tick_volume_from_cumulative_delta");
        this.volumeFromLtqFallbackCounter = meterRegistry.counter("tick_volume_from_ltq_fallback");
        this.volumeZeroFallbackCounter = meterRegistry.counter("tick_volume_zero_fallback");
    }

    public TickToSeriesIngestService(
            InMemoryConcurrentBarSeriesRegistry seriesRegistry,
            ObjectProvider<SessionSeriesCoordinator> sessionSeriesCoordinatorProvider,
            TicksWebSocketBroadcaster webSocketBroadcaster,
            BootstrapProperties bootstrapProperties,
            MeterRegistry meterRegistry) {
        this(seriesRegistry,
                null,
                sessionSeriesCoordinatorProvider,
                null,
                webSocketBroadcaster,
                bootstrapProperties,
                new SessionAlignedSeriesIngestor("Asia/Kolkata", "09:15", "15:30"),
                false,
                meterRegistry);
    }

    public void ingest(Tick rawTick, TypedTick typedTick) {
        ExtractedTickFields fields = extract(rawTick, typedTick);
        if (fields == null) {
            return;
        }

        List<InMemoryConcurrentBarSeriesRegistry.SeriesEntry> entries = seriesRegistry.entriesForInstrument(fields.instrumentToken());
        if (entries.isEmpty()) {
            return;
        }

        for (InMemoryConcurrentBarSeriesRegistry.SeriesEntry entry : entries) {
            InMemoryConcurrentBarSeriesRegistry.BootstrapSnapshot bootstrapSnapshot = entry.bootstrapState().snapshot();
            if (isBootstrapBuffering(bootstrapSnapshot.lifecycle())) {
                if (bootstrapSnapshot.v2Enabled()) {
                    BootstrapTickBufferStore store = bootstrapTickBufferStoreProvider == null
                            ? null
                            : bootstrapTickBufferStoreProvider.getIfAvailable();
                    InMemoryConcurrentBarSeriesRegistry.BootstrapSnapshot latest = entry.bootstrapState().snapshot();
                    if (store != null
                            && latest.v2Enabled()
                            && latest.runId() == bootstrapSnapshot.runId()
                            && isBootstrapBuffering(latest.lifecycle())) {
                        store.append(
                                entry.seriesKey(),
                                latest.runId(),
                                fields.timestampMs(),
                                fields.price(),
                                fields.volume(),
                                System.currentTimeMillis());
                        continue;
                    }
                    if (isBootstrapBuffering(latest.lifecycle())) {
                        seriesRegistry.appendBootstrapTick(
                                entry,
                                fields.timestampMs(),
                                fields.price(),
                                fields.volume(),
                                bootstrapProperties.capacity());
                        continue;
                    }
                } else {
                    seriesRegistry.appendBootstrapTick(
                            entry,
                            fields.timestampMs(),
                            fields.price(),
                            fields.volume(),
                            bootstrapProperties.capacity());
                    continue;
                }
            }

            var series = entry.series();
            int beforeBegin = series.getBeginIndex();
            int beforeEnd = series.getEndIndex();

            long previousTimestamp = entry.lastIngestTimestampMs().get();
            if (previousTimestamp != Long.MIN_VALUE && fields.timestampMs() < previousTimestamp) {
                outOfOrderDroppedCounter.increment();
                log.warn("tick_out_of_order seriesKey={} prevTs={} currTs={}",
                        SeriesKeyCodec.encode(entry.seriesKey()), previousTimestamp, fields.timestampMs());
                continue;
            }
            if (liveAlignmentEnabled) {
                SessionAlignedSeriesIngestor.IngestResult ingestResult = sessionAlignedSeriesIngestor.ingest(
                        entry,
                        fields.timestampMs(),
                        fields.price(),
                        fields.volume());
                if (ingestResult == SessionAlignedSeriesIngestor.IngestResult.DROPPED_OUT_OF_ORDER_BUCKET) {
                    bucketOutOfOrderDroppedCounter.increment();
                    log.warn("tick_bucket_out_of_order_drop seriesKey={} ts={}",
                            SeriesKeyCodec.encode(entry.seriesKey()),
                            fields.timestampMs());
                    continue;
                }
                if (ingestResult == SessionAlignedSeriesIngestor.IngestResult.DROPPED_OUT_OF_SESSION) {
                    continue;
                }
            } else {
                series.withWriteLock(() -> series.ingestTrade(
                        Instant.ofEpochMilli(fields.timestampMs()),
                        series.numFactory().numOf(fields.volume()),
                        series.numFactory().numOf(fields.price())));
            }
            entry.lastIngestTimestampMs().set(fields.timestampMs());
            int afterBegin = series.getBeginIndex();
            int afterEnd = series.getEndIndex();

            DeltaResponse appendDeltas = null;
            if (runtimeManager != null && afterEnd > beforeEnd) {
                appendDeltas = runtimeManager.onSeriesAdvanced(
                        entry.seriesKey(),
                        new SeriesAdvanceEvent(
                                beforeBegin,
                                beforeEnd,
                                afterBegin,
                                afterEnd,
                                SeriesMutationType.APPEND,
                                fields.timestampMs()));
            }
            DeltaResponse liveDeltas = null;
            if (runtimeManager != null) {
                liveDeltas = runtimeManager.onSeriesAdvanced(
                        entry.seriesKey(),
                        new SeriesAdvanceEvent(
                                beforeBegin,
                                beforeEnd,
                                afterBegin,
                                afterEnd,
                                SeriesMutationType.LIVE_UPSERT,
                                fields.timestampMs()));
            }

            SessionSeriesCoordinator sessionSeriesCoordinator = sessionSeriesCoordinatorProvider.getIfAvailable();
            if (sessionSeriesCoordinator == null) {
                continue;
            }
            Set<UiSessionId> sessionIds = sessionSeriesCoordinator.sessionsForSeries(entry.seriesKey());
            if (sessionIds.isEmpty()) {
                continue;
            }
            String encodedSeriesKey = SeriesKeyCodec.encode(entry.seriesKey());
            if (appendDeltas != null) {
                broadcastScriptDeltas(entry.seriesKey(), sessionIds, encodedSeriesKey, appendDeltas);
            }
            if (liveDeltas != null) {
                broadcastScriptDeltas(entry.seriesKey(), sessionIds, encodedSeriesKey, liveDeltas);
            }
            if (afterEnd > beforeEnd) {
                // Emit only finalized candle: the previous bar that just closed on rollover.
                int finalizedIndex = afterEnd - 1;
                if (finalizedIndex < series.getBeginIndex()) {
                    continue;
                }
                long seq = entry.lastSeq().incrementAndGet();
                var bar = series.getBar(finalizedIndex);
                for (UiSessionId sessionId : sessionIds) {
                    String mutation = sessionSeriesCoordinator.resolveAndMarkCandleMutation(
                            sessionId,
                            entry.seriesKey(),
                            finalizedIndex);
                    CandleAppendedEvent.Meta meta = new CandleAppendedEvent.Meta(
                            series.getBeginIndex(),
                            series.getEndIndex(),
                            entry.maxBarCount());
                    var payload = new CandleAppendedEvent(
                            sessionId.value(),
                            encodedSeriesKey,
                            seq,
                            new SeriesBarView(
                                    OffsetDateTime.ofInstant(bar.getBeginTime(), CLIENT_ZONE),
                                    OffsetDateTime.ofInstant(bar.getEndTime(), CLIENT_ZONE),
                                    finalizedIndex,
                                    mutation,
                                    bar.getOpenPrice().toString(),
                                    bar.getHighPrice().toString(),
                                    bar.getLowPrice().toString(),
                                    bar.getClosePrice().toString(),
                                    bar.getVolume().toString(),
                                    bar.getTrades()),
                            meta);
                    webSocketBroadcaster.broadcastCandleAppendedToSession(sessionId, payload);
                    logWsEmit(
                            "candle_appended",
                            sessionId.value(),
                            encodedSeriesKey,
                            seq,
                            finalizedIndex,
                            mutation,
                            meta.beginIndex(),
                            meta.endIndex());
                }
                candleAppendedCounter.increment();
            }

            int currentIndex = series.getEndIndex();
            if (currentIndex < series.getBeginIndex()) {
                continue;
            }
            long liveSeq = entry.lastSeq().incrementAndGet();
            var liveBar = series.getBar(currentIndex);
            for (UiSessionId sessionId : sessionIds) {
                CandleAppendedEvent.Meta meta = new CandleAppendedEvent.Meta(
                        series.getBeginIndex(),
                        series.getEndIndex(),
                        entry.maxBarCount());
                var payload = new CandleAppendedEvent(
                        sessionId.value(),
                        encodedSeriesKey,
                        liveSeq,
                        new SeriesBarView(
                                OffsetDateTime.ofInstant(liveBar.getBeginTime(), CLIENT_ZONE),
                                OffsetDateTime.ofInstant(liveBar.getEndTime(), CLIENT_ZONE),
                                currentIndex,
                                "REPLACE",
                                liveBar.getOpenPrice().toString(),
                                liveBar.getHighPrice().toString(),
                                liveBar.getLowPrice().toString(),
                                liveBar.getClosePrice().toString(),
                                liveBar.getVolume().toString(),
                                liveBar.getTrades()),
                        meta);
                webSocketBroadcaster.broadcastCandleLiveUpsertToSession(sessionId, payload);
                logWsEmit(
                        "candle_live_upsert",
                        sessionId.value(),
                        encodedSeriesKey,
                        liveSeq,
                        currentIndex,
                        "REPLACE",
                        meta.beginIndex(),
                        meta.endIndex());
            }
            candleLiveUpsertCounter.increment();
        }
    }

    private void broadcastScriptDeltas(SeriesKey seriesKey, Set<UiSessionId> sessionIds, String encodedSeriesKey, DeltaResponse deltas) {
        if (deltas == null || deltas.events() == null || deltas.events().isEmpty()) {
            return;
        }
        for (SessionEvent event : deltas.events()) {
            Object payload = event.payload();
            String scriptInstanceId = null;
            String registryType = null;
            if (payload instanceof java.util.Map<?, ?> map) {
                Object sid = map.get("scriptInstanceId");
                if (sid != null) {
                    scriptInstanceId = sid.toString();
                }
            } else if (payload instanceof TradeIntent intent) {
                scriptInstanceId = intent.scriptInstanceId();
                if ("INTENT_ADD".equals(event.type())) {
                    StrategyIntentDispatchService dispatchService = strategyIntentDispatchServiceProvider == null
                            ? null
                            : strategyIntentDispatchServiceProvider.getIfAvailable();
                    if (dispatchService != null) {
                        dispatchService.dispatchAsync(seriesKey, intent);
                    }
                }
            }
            if (event.type().startsWith("DRAWING")) {
                registryType = "drawing";
            } else if (event.type().startsWith("PLOT")) {
                registryType = "plot";
            } else if (event.type().startsWith("ALERT")) {
                registryType = "alert";
            } else if (event.type().startsWith("INTENT")) {
                registryType = "intent";
            }

            if (scriptInstanceId != null) {
                String ownerSessionId = runtimeManager.ownerSessionIdForScript(seriesKey, scriptInstanceId);
                if (ownerSessionId != null) {
                    UiSessionId target = new UiSessionId(ownerSessionId);
                    if (!sessionIds.contains(target)) {
                        continue;
                    }
                    ScriptDeltaWsEvent wsEvent = new ScriptDeltaWsEvent(
                            target.value(),
                            encodedSeriesKey,
                            event.seq(),
                            event.type(),
                            scriptInstanceId,
                            payload,
                            registryType);
                    if ("INTENT_ADD".equals(event.type())) {
                        webSocketBroadcaster.broadcastIntentToSession(target, wsEvent);
                    } else {
                        webSocketBroadcaster.broadcastScriptDeltaToSession(target, wsEvent);
                    }
                    continue;
                }
            }
            for (UiSessionId sessionId : sessionIds) {
                ScriptDeltaWsEvent wsEvent = new ScriptDeltaWsEvent(
                        sessionId.value(),
                        encodedSeriesKey,
                        event.seq(),
                        event.type(),
                        scriptInstanceId,
                        payload,
                        registryType);
                if ("INTENT_ADD".equals(event.type())) {
                    webSocketBroadcaster.broadcastIntentToSession(sessionId, wsEvent);
                } else {
                    webSocketBroadcaster.broadcastScriptDeltaToSession(sessionId, wsEvent);
                }
            }
        }
    }

    private void logWsEmit(
            String type,
            String sessionId,
            String seriesKey,
            long seq,
            int barIndex,
            String mutation,
            int beginIndex,
            int endIndex) {
        if (seq <= 5 || seq % 100 == 0) {
            log.info("sync_ws_emit type={} sessionId={} seriesKey={} seq={} barIndex={} mutation={} beginIndex={} endIndex={}",
                    type, sessionId, seriesKey, seq, barIndex, mutation, beginIndex, endIndex);
        }
    }

    private ExtractedTickFields extract(Tick rawTick, TypedTick typedTick) {
        if (typedTick == null) {
            return null;
        }

        TickQuote quote = typedTick.tickQuote();
        if (quote != null) {
            Long token = quote.getInstrumentToken();
            Long timestamp = quote.getTickTimestamp();
            Double ltp = quote.getLastTradedPrice();
            if (token == null || ltp == null) {
                log.warn("tick_missing_fields type=tradeable token={} ts={} ltp={}", token, timestamp, ltp);
                return null;
            }
            if (timestamp == null) {
                missingTimestampDroppedCounter.increment();
                rateLimitedTimestampWarn(token, "tradeable");
                return null;
            }
            long ts = timestamp;
            double volume = resolveTradeSize(token, quote);
            return new ExtractedTickFields(token, ts, ltp, volume);
        }

        IndexQuote index = typedTick.indexQuote();
        if (index == null) {
            return null;
        }
        Long token = index.getInstrumentToken();
        Long timestamp = index.getTickTimestamp();
        Double ltp = index.getLastTradedPrice();
        if (token == null || ltp == null) {
            log.warn("tick_missing_fields type=index token={} ts={} ltp={}", token, timestamp, ltp);
            return null;
        }
        if (timestamp == null) {
            missingTimestampDroppedCounter.increment();
            rateLimitedTimestampWarn(token, "index");
            return null;
        }
        return new ExtractedTickFields(token, timestamp, ltp, 0.0d);
    }

    private void rateLimitedTimestampWarn(long token, String type) {
        long now = System.currentTimeMillis();
        long previous = lastMissingTimestampWarnMs.get();
        if (now - previous >= 5000 && lastMissingTimestampWarnMs.compareAndSet(previous, now)) {
            log.warn("tick_timestamp_missing_dropped type={} token={}", type, token);
        }
    }

    private double resolveTradeSize(long instrumentToken, TickQuote quote) {
        Integer cumulativeVolume = quote.getVolumeTradedToday();
        if (cumulativeVolume != null) {
            long cumNow = Math.max(0L, cumulativeVolume.longValue());
            Long previous = lastCumulativeVolumeByInstrument.putIfAbsent(instrumentToken, cumNow);
            if (previous == null) {
                volumeZeroFallbackCounter.increment();
                return 0.0d;
            }
            if (cumNow < previous) {
                lastCumulativeVolumeByInstrument.put(instrumentToken, cumNow);
                volumeZeroFallbackCounter.increment();
                return 0.0d;
            }
            if (cumNow > previous) {
                long delta = cumNow - previous;
                lastCumulativeVolumeByInstrument.put(instrumentToken, cumNow);
                volumeFromCumulativeDeltaCounter.increment();
                return delta;
            }
            volumeZeroFallbackCounter.increment();
            return 0.0d;
        }

        Integer lastTradedQuantity = quote.getLastTradedQuantity();
        if (lastTradedQuantity != null && lastTradedQuantity > 0) {
            volumeFromLtqFallbackCounter.increment();
            return lastTradedQuantity.doubleValue();
        }
        volumeZeroFallbackCounter.increment();
        return 0.0d;
    }

    private boolean isBootstrapBuffering(InMemoryConcurrentBarSeriesRegistry.BootstrapLifecycle lifecycle) {
        return lifecycle == InMemoryConcurrentBarSeriesRegistry.BootstrapLifecycle.BUFFERING
                || lifecycle == InMemoryConcurrentBarSeriesRegistry.BootstrapLifecycle.RUNNING
                || lifecycle == InMemoryConcurrentBarSeriesRegistry.BootstrapLifecycle.REPLAYING;
    }

    private record ExtractedTickFields(long instrumentToken, long timestampMs, double price, double volume) {
    }
}
