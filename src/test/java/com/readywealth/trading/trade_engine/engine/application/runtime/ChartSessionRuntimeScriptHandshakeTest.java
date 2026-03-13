package com.readywealth.trading.trade_engine.engine.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BaseBar;

import com.readywealth.trading.trade_engine.engine.domain.script.IndicatorScriptDefinition;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptCatalog;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptExecutionContext;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptId;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptInitContext;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptParamsBinder;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptRuntime;
import com.readywealth.trading.trade_engine.engine.domain.script.examples.SampleIndicatorScriptDefinition;
import com.readywealth.trading.trade_engine.engine.domain.series.IntervalKind;
import com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey;
import com.readywealth.trading.trade_engine.session.infrastructure.memory.InMemoryConcurrentBarSeriesRegistry;

class ChartSessionRuntimeScriptHandshakeTest {

    @Test
    void attachReturnsLoadingAndReadyEventIsPublishedAsync() throws Exception {
        SeriesKey key = new SeriesKey(115626503L, IntervalKind.TIME_1M);
        InMemoryConcurrentBarSeriesRegistry registry = withSeriesBars(key, List.of(100.0, 110.0, 120.0));
        ScriptCatalog catalog = new ScriptCatalog(List.of(new SampleIndicatorScriptDefinition()));
        ChartSessionRuntime runtime = new ChartSessionRuntime(key, catalog, registry);
        runtime.start();

        CountDownLatch readyLatch = new CountDownLatch(1);
        AtomicReference<ScriptSnapshotReadyEvent> readyRef = new AtomicReference<>();
        try {
            AttachScriptResult attach = runtime.attachScript(
                    "session-1",
                    "user-1",
                    "sample.indicator.close-plot",
                    Map.of("every", 1),
                    ScriptExecutionMode.ON_CANDLE_CLOSE,
                    ready -> {
                        readyRef.set(ready);
                        readyLatch.countDown();
                    });

            assertEquals(ScriptAttachmentState.LOADING, attach.attachment().lifecycleState());
            assertTrue(attach.attachAcceptedAtEpochMs() > 0);
            assertNotNull(attach.bootstrapJobId());

            assertTrue(readyLatch.await(2, TimeUnit.SECONDS), "script_snapshot_ready was not emitted");
            ScriptSnapshotReadyEvent ready = readyRef.get();
            assertNotNull(ready);
            assertEquals("session-1", ready.ownerSessionId());
            assertEquals(attach.attachment().instanceId().value(), ready.scriptInstanceId());
            assertEquals(ScriptBootstrapStatus.READY, ready.status());

            ScriptSnapshotAckResult ack = runtime.markScriptSnapshotDelivered(
                    "session-1",
                    attach.attachment().instanceId().value(),
                    ready.snapshotCursorSeq());
            assertTrue(ack.activated());
            assertEquals(ScriptAttachmentState.ACTIVE, runtime.listScripts().getFirst().lifecycleState());
        } finally {
            runtime.stop();
        }
    }

    @Test
    void deltasAreBufferedUntilSnapshotAckThenReplayed() throws Exception {
        SeriesKey key = new SeriesKey(115626503L, IntervalKind.TIME_1M);
        InMemoryConcurrentBarSeriesRegistry registry = withSeriesBars(key, List.of(100.0));
        ScriptCatalog catalog = new ScriptCatalog(List.of(new SampleIndicatorScriptDefinition()));
        ChartSessionRuntime runtime = new ChartSessionRuntime(key, catalog, registry);
        runtime.start();

        CountDownLatch readyLatch = new CountDownLatch(1);
        AtomicReference<ScriptSnapshotReadyEvent> readyRef = new AtomicReference<>();
        try {
            AttachScriptResult attach = runtime.attachScript(
                    "session-1",
                    "user-1",
                    "sample.indicator.close-plot",
                    Map.of("every", 1),
                    ScriptExecutionMode.ON_CANDLE_CLOSE,
                    ready -> {
                        readyRef.set(ready);
                        readyLatch.countDown();
                    });
            assertTrue(readyLatch.await(2, TimeUnit.SECONDS), "script_snapshot_ready was not emitted");
            ScriptSnapshotReadyEvent ready = readyRef.get();
            assertNotNull(ready);

            var entry = registry.get(key).orElseThrow();
            int beforeBegin = entry.series().getBeginIndex();
            int beforeEnd = entry.series().getEndIndex();
            addBar(entry, 130.0);
            int afterBegin = entry.series().getBeginIndex();
            int afterEnd = entry.series().getEndIndex();

            var liveDelta = runtime.onSeriesAdvanced(new SeriesAdvanceEvent(
                    beforeBegin,
                    beforeEnd,
                    afterBegin,
                    afterEnd,
                    SeriesMutationType.APPEND,
                    System.currentTimeMillis()));
            assertTrue(liveDelta.events().isEmpty(), "loading script deltas should be buffered");

            ScriptSnapshotAckResult ack = runtime.markScriptSnapshotDelivered(
                    "session-1",
                    attach.attachment().instanceId().value(),
                    ready.snapshotCursorSeq());
            assertTrue(ack.activated());
            assertTrue(ack.replayedEvents() > 0);
            assertFalse(ack.replayDeltas().events().isEmpty());
            assertTrue(ack.replayDeltas().events().stream().allMatch(event -> {
                Object payload = event.payload();
                return payload instanceof Map<?, ?> map
                        && attach.attachment().instanceId().value().equals(String.valueOf(map.get("scriptInstanceId")));
            }));
        } finally {
            runtime.stop();
        }
    }

    @Test
    void drawingDeltasAreBufferedUntilSnapshotAckThenReplayed() throws Exception {
        SeriesKey key = new SeriesKey(115626503L, IntervalKind.TIME_1M);
        InMemoryConcurrentBarSeriesRegistry registry = withSeriesBars(key, List.of(100.0));
        ScriptCatalog catalog = new ScriptCatalog(List.of(new DrawingScriptDefinition()));
        ChartSessionRuntime runtime = new ChartSessionRuntime(key, catalog, registry);
        runtime.start();

        CountDownLatch readyLatch = new CountDownLatch(1);
        AtomicReference<ScriptSnapshotReadyEvent> readyRef = new AtomicReference<>();
        try {
            AttachScriptResult attach = runtime.attachScript(
                    "session-1",
                    "user-1",
                    "test.drawing.script",
                    Map.of(),
                    ScriptExecutionMode.ON_CANDLE_CLOSE,
                    ready -> {
                        readyRef.set(ready);
                        readyLatch.countDown();
                    });
            assertTrue(readyLatch.await(2, TimeUnit.SECONDS), "script_snapshot_ready was not emitted");
            ScriptSnapshotReadyEvent ready = readyRef.get();
            assertNotNull(ready);

            var entry = registry.get(key).orElseThrow();
            int beforeBegin = entry.series().getBeginIndex();
            int beforeEnd = entry.series().getEndIndex();
            addBar(entry, 101.0);
            int afterBegin = entry.series().getBeginIndex();
            int afterEnd = entry.series().getEndIndex();

            var liveDelta = runtime.onSeriesAdvanced(new SeriesAdvanceEvent(
                    beforeBegin,
                    beforeEnd,
                    afterBegin,
                    afterEnd,
                    SeriesMutationType.APPEND,
                    System.currentTimeMillis()));
            assertTrue(liveDelta.events().isEmpty(), "loading script deltas should be buffered");

            ScriptSnapshotAckResult ack = runtime.markScriptSnapshotDelivered(
                    "session-1",
                    attach.attachment().instanceId().value(),
                    ready.snapshotCursorSeq());
            assertTrue(ack.activated());
            assertTrue(ack.replayDeltas().events().stream()
                    .anyMatch(event -> "DRAWING_UPSERT".equals(event.type())));
        } finally {
            runtime.stop();
        }
    }

    @Test
    void bootstrapFailureMarksScriptFailedAndSkipsActivation() throws Exception {
        SeriesKey key = new SeriesKey(115626503L, IntervalKind.TIME_1M);
        InMemoryConcurrentBarSeriesRegistry registry = withSeriesBars(key, List.of(100.0));
        ScriptCatalog catalog = new ScriptCatalog(List.of(new FailingScriptDefinition()));
        ChartSessionRuntime runtime = new ChartSessionRuntime(key, catalog, registry);
        runtime.start();

        CountDownLatch readyLatch = new CountDownLatch(1);
        AtomicReference<ScriptSnapshotReadyEvent> readyRef = new AtomicReference<>();
        try {
            AttachScriptResult attach = runtime.attachScript(
                    "session-1",
                    "user-1",
                    "test.fail.script",
                    Map.of(),
                    ScriptExecutionMode.ON_CANDLE_CLOSE,
                    ready -> {
                        readyRef.set(ready);
                        readyLatch.countDown();
                    });

            assertTrue(readyLatch.await(2, TimeUnit.SECONDS), "failed ready event was not emitted");
            ScriptSnapshotReadyEvent ready = readyRef.get();
            assertNotNull(ready);
            assertEquals(ScriptBootstrapStatus.FAILED, ready.status());
            assertTrue(ready.error().contains("boom"));

            ScriptSnapshotAckResult ack = runtime.markScriptSnapshotDelivered(
                    "session-1",
                    attach.attachment().instanceId().value(),
                    ready.snapshotCursorSeq());
            assertFalse(ack.activated());
            assertEquals(ScriptAttachmentState.FAILED, runtime.listScripts().getFirst().lifecycleState());
        } finally {
            runtime.stop();
        }
    }

    @Test
    void emptyBootstrapAdvanceDoesNotFailRuntime() {
        SeriesKey key = new SeriesKey(115626503L, IntervalKind.TIME_1M);
        InMemoryConcurrentBarSeriesRegistry registry = withSeriesBars(key, List.of());
        ScriptCatalog catalog = new ScriptCatalog(List.of(new SampleIndicatorScriptDefinition()));
        ChartSessionRuntime runtime = new ChartSessionRuntime(key, catalog, registry);
        runtime.start();

        try {
            var delta = runtime.onSeriesAdvanced(new SeriesAdvanceEvent(
                    -1,
                    -1,
                    -1,
                    -1,
                    SeriesMutationType.BOOTSTRAP_SEED,
                    System.currentTimeMillis()));
            assertNotNull(delta);
            assertTrue(delta.events().isEmpty());
            assertEquals(SeriesRuntimeState.RUNNING, runtime.health().state());
        } finally {
            runtime.stop();
        }
    }

    @Test
    void replaceCreatesNewInstanceAndRemovesOldInstance() throws Exception {
        SeriesKey key = new SeriesKey(115626503L, IntervalKind.TIME_1M);
        InMemoryConcurrentBarSeriesRegistry registry = withSeriesBars(key, List.of(100.0, 110.0, 120.0));
        ScriptCatalog catalog = new ScriptCatalog(List.of(new SampleIndicatorScriptDefinition()));
        ChartSessionRuntime runtime = new ChartSessionRuntime(key, catalog, registry);
        runtime.start();

        CountDownLatch attachReadyLatch = new CountDownLatch(1);
        AtomicReference<ScriptSnapshotReadyEvent> attachReadyRef = new AtomicReference<>();
        CountDownLatch replaceReadyLatch = new CountDownLatch(1);
        AtomicReference<ScriptSnapshotReadyEvent> replaceReadyRef = new AtomicReference<>();
        try {
            AttachScriptResult attached = runtime.attachScript(
                    "session-1",
                    "user-1",
                    "sample.indicator.close-plot",
                    Map.of("every", 1),
                    ScriptExecutionMode.ON_CANDLE_CLOSE,
                    ready -> {
                        attachReadyRef.set(ready);
                        attachReadyLatch.countDown();
                    });
            assertTrue(attachReadyLatch.await(2, TimeUnit.SECONDS), "script_snapshot_ready was not emitted");
            ScriptSnapshotReadyEvent attachReady = attachReadyRef.get();
            assertNotNull(attachReady);
            ScriptSnapshotAckResult attachAck = runtime.markScriptSnapshotDelivered(
                    "session-1",
                    attached.attachment().instanceId().value(),
                    attachReady.snapshotCursorSeq());
            assertTrue(attachAck.activated());

            String oldScriptInstanceId = attached.attachment().instanceId().value();
            AttachScriptResult replaced = runtime.replaceScript(
                    "session-1",
                    "user-1",
                    oldScriptInstanceId,
                    Map.of("every", 2),
                    null,
                    ready -> {
                        replaceReadyRef.set(ready);
                        replaceReadyLatch.countDown();
                    });
            String newScriptInstanceId = replaced.attachment().instanceId().value();

            assertNotEquals(oldScriptInstanceId, newScriptInstanceId);
            assertEquals(1, runtime.listScripts().size());
            assertEquals(newScriptInstanceId, runtime.listScripts().getFirst().instanceId().value());
            assertEquals(ScriptAttachmentState.LOADING, runtime.listScripts().getFirst().lifecycleState());

            assertTrue(replaceReadyLatch.await(2, TimeUnit.SECONDS), "replace script_snapshot_ready was not emitted");
            ScriptSnapshotReadyEvent replaceReady = replaceReadyRef.get();
            assertNotNull(replaceReady);
            ScriptSnapshotAckResult replaceAck = runtime.markScriptSnapshotDelivered(
                    "session-1",
                    newScriptInstanceId,
                    replaceReady.snapshotCursorSeq());
            assertTrue(replaceAck.activated());
            assertEquals(ScriptAttachmentState.ACTIVE, runtime.listScripts().getFirst().lifecycleState());
        } finally {
            runtime.stop();
        }
    }

    @Test
    void replaceRejectsOwnerMismatch() throws Exception {
        SeriesKey key = new SeriesKey(115626503L, IntervalKind.TIME_1M);
        InMemoryConcurrentBarSeriesRegistry registry = withSeriesBars(key, List.of(100.0));
        ScriptCatalog catalog = new ScriptCatalog(List.of(new SampleIndicatorScriptDefinition()));
        ChartSessionRuntime runtime = new ChartSessionRuntime(key, catalog, registry);
        runtime.start();

        CountDownLatch readyLatch = new CountDownLatch(1);
        AtomicReference<ScriptSnapshotReadyEvent> readyRef = new AtomicReference<>();
        try {
            AttachScriptResult attached = runtime.attachScript(
                    "session-1",
                    "user-1",
                    "sample.indicator.close-plot",
                    Map.of("every", 1),
                    ScriptExecutionMode.ON_CANDLE_CLOSE,
                    ready -> {
                        readyRef.set(ready);
                        readyLatch.countDown();
                    });
            assertTrue(readyLatch.await(2, TimeUnit.SECONDS), "script_snapshot_ready was not emitted");
            assertNotNull(readyRef.get());

            IllegalStateException error = assertThrows(IllegalStateException.class, () -> runtime.replaceScript(
                    "session-2",
                    "user-2",
                    attached.attachment().instanceId().value(),
                    Map.of("every", 2),
                    null,
                    null));
            assertTrue(error.getMessage().contains("owner session"));
            assertEquals(1, runtime.listScripts().size());
            assertEquals(attached.attachment().instanceId().value(), runtime.listScripts().getFirst().instanceId().value());
        } finally {
            runtime.stop();
        }
    }

    @Test
    void replaceRejectsMissingScriptInstance() {
        SeriesKey key = new SeriesKey(115626503L, IntervalKind.TIME_1M);
        InMemoryConcurrentBarSeriesRegistry registry = withSeriesBars(key, List.of(100.0));
        ScriptCatalog catalog = new ScriptCatalog(List.of(new SampleIndicatorScriptDefinition()));
        ChartSessionRuntime runtime = new ChartSessionRuntime(key, catalog, registry);
        runtime.start();

        try {
            IllegalStateException error = assertThrows(IllegalStateException.class, () -> runtime.replaceScript(
                    "session-1",
                    "user-1",
                    "missing-script-instance-id",
                    Map.of("every", 2),
                    ScriptExecutionMode.ON_TICK,
                    null));
            assertTrue(error.getMessage().contains("Script instance not found"));
        } finally {
            runtime.stop();
        }
    }

    @Test
    void replaceWithInvalidParamsKeepsOriginalAttachment() throws Exception {
        SeriesKey key = new SeriesKey(115626503L, IntervalKind.TIME_1M);
        InMemoryConcurrentBarSeriesRegistry registry = withSeriesBars(key, List.of(100.0));
        ScriptCatalog catalog = new ScriptCatalog(List.of(new SampleIndicatorScriptDefinition()));
        ChartSessionRuntime runtime = new ChartSessionRuntime(key, catalog, registry);
        runtime.start();

        CountDownLatch readyLatch = new CountDownLatch(1);
        AtomicReference<ScriptSnapshotReadyEvent> readyRef = new AtomicReference<>();
        try {
            AttachScriptResult attached = runtime.attachScript(
                    "session-1",
                    "user-1",
                    "sample.indicator.close-plot",
                    Map.of("every", 1),
                    ScriptExecutionMode.ON_CANDLE_CLOSE,
                    ready -> {
                        readyRef.set(ready);
                        readyLatch.countDown();
                    });
            assertTrue(readyLatch.await(2, TimeUnit.SECONDS), "script_snapshot_ready was not emitted");
            assertNotNull(readyRef.get());

            IllegalStateException error = assertThrows(IllegalStateException.class, () -> runtime.replaceScript(
                    "session-1",
                    "user-1",
                    attached.attachment().instanceId().value(),
                    Map.of("every", "abc"),
                    null,
                    null));
            assertTrue(error.getMessage().contains("For input string"));
            assertEquals(1, runtime.listScripts().size());
            assertEquals(attached.attachment().instanceId().value(), runtime.listScripts().getFirst().instanceId().value());
        } finally {
            runtime.stop();
        }
    }

    private static InMemoryConcurrentBarSeriesRegistry withSeriesBars(SeriesKey key, List<Double> closes) {
        InMemoryConcurrentBarSeriesRegistry registry = new InMemoryConcurrentBarSeriesRegistry();
        var entry = registry.acquire(key, 5000).entry();
        for (double close : closes) {
            addBar(entry, close);
        }
        return registry;
    }

    private static void addBar(InMemoryConcurrentBarSeriesRegistry.SeriesEntry entry, double close) {
        int nextIndex = entry.series().getEndIndex() + 1;
        Instant begin = Instant.parse("2026-01-01T09:15:00Z").plus(Duration.ofMinutes(Math.max(0, nextIndex)));
        Instant end = begin.plus(Duration.ofMinutes(1));
        entry.series().addBar(new BaseBar(
                Duration.ofMinutes(1),
                begin,
                end,
                entry.series().numFactory().numOf(close),
                entry.series().numFactory().numOf(close),
                entry.series().numFactory().numOf(close),
                entry.series().numFactory().numOf(close),
                entry.series().numFactory().numOf(1.0),
                entry.series().numFactory().numOf(close),
                1L));
    }

    private static final class FailingScriptDefinition implements IndicatorScriptDefinition {
        private static final ScriptId ID = new ScriptId("test.fail.script");

        @Override
        public ScriptId id() {
            return ID;
        }

        @Override
        public ScriptParamsBinder<?> paramsBinder() {
            return raw -> Map.of();
        }

        @Override
        public ScriptRuntime createRuntime(ScriptInitContext init, Object typedParams) {
            return new ScriptRuntime() {
                @Override
                public void onBar(ScriptExecutionContext ctx, int index) {
                    throw new IllegalStateException("boom");
                }
            };
        }
    }

    private static final class DrawingScriptDefinition implements IndicatorScriptDefinition {
        private static final ScriptId ID = new ScriptId("test.drawing.script");

        @Override
        public ScriptId id() {
            return ID;
        }

        @Override
        public ScriptParamsBinder<?> paramsBinder() {
            return raw -> Map.of();
        }

        @Override
        public ScriptRuntime createRuntime(ScriptInitContext init, Object typedParams) {
            return new ScriptRuntime() {
                @Override
                public void onBar(ScriptExecutionContext ctx, int index) {
                    double close = ctx.seriesContext().series().getBar(index).getClosePrice().doubleValue();
                    ctx.registries().upsertDrawing(
                            "line",
                            Map.of(
                                    "kind", "line",
                                    "p1", Map.of("index", index, "price", close),
                                    "p2", Map.of("index", index, "price", close),
                                    "stroke", Map.of("color", "rgba(0, 0, 0, 1)", "width", 1)));
                }
            };
        }
    }
}
