package com.readywealth.trading.trade_engine.engine.application.runtime;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.ta4j.core.BarSeries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.readywealth.trading.trade_engine.engine.domain.module.spec.ModuleSpec;
import com.readywealth.trading.trade_engine.engine.domain.module.spec.StrategyModulePlan;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptAttachment;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptCatalog;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptDefinition;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptExecutionContext;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptId;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptInitContext;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptInstanceId;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptParams;
import com.readywealth.trading.trade_engine.engine.domain.script.instance.ScriptAttachmentManager;
import com.readywealth.trading.trade_engine.engine.domain.series.BarSeriesContext;
import com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey;
import com.readywealth.trading.trade_engine.engine.application.runtime.model.DeltaResponse;
import com.readywealth.trading.trade_engine.engine.application.runtime.model.SessionEvent;
import com.readywealth.trading.trade_engine.engine.application.runtime.model.TradeIntent;
import com.readywealth.trading.trade_engine.engine.application.runtime.registry.RegistryHub;
import com.readywealth.trading.trade_engine.engine.application.runtime.registry.RegistryType;
import com.readywealth.trading.trade_engine.session.infrastructure.memory.InMemoryConcurrentBarSeriesRegistry;

public final class ChartSessionRuntime {
    private static final Logger log = LoggerFactory.getLogger(ChartSessionRuntime.class);

    private static final int DEFAULT_BUFFER_LIMIT = 20000;

    private final SeriesKey seriesKey;
    private final ScriptCatalog scriptCatalog;
    private final InMemoryConcurrentBarSeriesRegistry seriesRegistry;
    private final ThreadPoolExecutor eventLoop;
    private final ScriptAttachmentManager attachmentManager = new ScriptAttachmentManager();
    private final RegistryHub registryHub = new RegistryHub();
    private final AtomicLong attachSequence = new AtomicLong(0);
    private final Set<ModuleSpec<?>> moduleRoots = new LinkedHashSet<>();
    private final Map<String, BufferedScriptState> bufferedScripts = new LinkedHashMap<>();
    private final Map<String, Future<?>> bootstrapFutures = new LinkedHashMap<>();

    private volatile SeriesRuntimeState state = SeriesRuntimeState.CREATED;
    private volatile String lastError;
    private volatile boolean formingBarExecutionBlocked;

    public ChartSessionRuntime(
            SeriesKey seriesKey,
            ScriptCatalog scriptCatalog,
            InMemoryConcurrentBarSeriesRegistry seriesRegistry) {
        this.seriesKey = seriesKey;
        this.scriptCatalog = scriptCatalog;
        this.seriesRegistry = seriesRegistry;
        this.eventLoop = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>());
    }

    public synchronized void start() {
        if (state == SeriesRuntimeState.RUNNING || state == SeriesRuntimeState.STARTING) {
            return;
        }
        if (state == SeriesRuntimeState.STOPPING) {
            throw new IllegalStateException("Runtime is stopping: " + seriesKey);
        }
        state = SeriesRuntimeState.STARTING;
        state = SeriesRuntimeState.RUNNING;
    }

    public synchronized void stop() {
        if (state == SeriesRuntimeState.STOPPED || state == SeriesRuntimeState.STOPPING) {
            return;
        }
        state = SeriesRuntimeState.STOPPING;
        log.info("runtime_stop_cleanup_begin seriesKey={} state={} attachmentCount={} queueDepth={}",
                SeriesKeyCodec.encode(seriesKey),
                state.name(),
                attachmentManager.size(),
                eventLoop.getQueue().size());
        try {
            runOnLoop(() -> {
                BarSeriesContext context = seriesRegistry.get(seriesKey)
                        .map(InMemoryConcurrentBarSeriesRegistry.SeriesEntry::context)
                        .orElse(null);
                if (context == null) {
                    log.warn("runtime_stop_series_context_missing seriesKey={} runtimeState={} attachmentCount={} queueDepth={}",
                            SeriesKeyCodec.encode(seriesKey),
                            state.name(),
                            attachmentManager.size(),
                            eventLoop.getQueue().size());
                }
                for (ScriptAttachment attachment : attachmentManager.orderedAttachments()) {
                    if (context != null) {
                        attachment.runtime().onDetach(executionContextFor(context, attachment));
                    }
                }
                attachmentManager.clear();
                registryHub.clear();
                moduleRoots.clear();
                bufferedScripts.clear();
                for (Future<?> future : bootstrapFutures.values()) {
                    future.cancel(true);
                }
                bootstrapFutures.clear();
                return null;
            });
        } catch (IllegalStateException stopError) {
            log.warn("runtime_stop_cleanup_failed seriesKey={} runtimeState={} queueDepth={} reason={}",
                    SeriesKeyCodec.encode(seriesKey),
                    state.name(),
                    eventLoop.getQueue().size(),
                    stopError.getMessage());
        } finally {
            eventLoop.shutdownNow();
            state = SeriesRuntimeState.STOPPED;
        }
    }

    public AttachScriptResult attachScript(
            String ownerSessionId,
            String ownerUserId,
            String scriptIdValue,
            Map<String, Object> rawParams,
            ScriptExecutionMode executionMode,
            Consumer<ScriptSnapshotReadyEvent> readyListener) {
        return runOnLoop(() -> {
            assertRunning();
            var paramKeys = rawParams == null ? java.util.Set.<String>of() : rawParams.keySet();
            logRegistryHubState(
                    "script_attach_begin",
                    ownerSessionId,
                    null,
                    scriptIdValue,
                    null);
            log.info("script_runtime_attach_begin seriesKey={} ownerSessionId={} ownerUserId={} scriptId={} executionMode={} paramKeys={}",
                    SeriesKeyCodec.encode(seriesKey), ownerSessionId, ownerUserId, scriptIdValue, executionMode.name(), paramKeys);
            ScriptAttachment loading = prepareLoadingAttachment(
                    scriptIdValue,
                    rawParams,
                    executionMode,
                    ownerSessionId,
                    ownerUserId);
            AttachScriptResult result = attachPreparedScript(loading, readyListener);
            logRegistryHubState(
                    "script_attach_accepted",
                    ownerSessionId,
                    result.attachment().instanceId().value(),
                    result.attachment().scriptId().value(),
                    null);
            return result;
        });
    }

    public AttachScriptResult replaceScript(
            String ownerSessionId,
            String ownerUserId,
            String oldScriptInstanceId,
            Map<String, Object> rawParams,
            ScriptExecutionMode executionMode,
            Consumer<ScriptSnapshotReadyEvent> readyListener) {
        return runOnLoop(() -> {
            assertRunning();
            ScriptInstanceId oldId = new ScriptInstanceId(oldScriptInstanceId);
            ScriptAttachment current = attachmentManager.get(oldId)
                    .orElseThrow(() -> new IllegalArgumentException("Script instance not found: " + oldScriptInstanceId));
            if (ownerSessionId != null && !ownerSessionId.equals(current.ownerSessionId())) {
                throw new IllegalArgumentException("script instance not found for owner session");
            }

            ScriptExecutionMode resolvedMode = executionMode == null ? current.executionMode() : executionMode;
            String resolvedOwnerSessionId = ownerSessionId == null ? current.ownerSessionId() : ownerSessionId;
            String resolvedOwnerUserId = ownerUserId == null ? current.ownerUserId() : ownerUserId;
            var paramKeys = rawParams == null ? java.util.Set.<String>of() : rawParams.keySet();

            logRegistryHubState(
                    "script_replace_begin",
                    resolvedOwnerSessionId,
                    oldScriptInstanceId,
                    current.scriptId().value(),
                    null);
            log.info("script_runtime_replace_begin seriesKey={} ownerSessionId={} ownerUserId={} oldScriptInstanceId={} scriptId={} executionMode={} paramKeys={}",
                    SeriesKeyCodec.encode(seriesKey),
                    resolvedOwnerSessionId,
                    resolvedOwnerUserId,
                    oldScriptInstanceId,
                    current.scriptId().value(),
                    resolvedMode.name(),
                    paramKeys);

            ScriptAttachment loading = prepareLoadingAttachment(
                    current.scriptId().value(),
                    rawParams,
                    resolvedMode,
                    resolvedOwnerSessionId,
                    resolvedOwnerUserId);

            if (!detachAttachmentInternal(current)) {
                throw new IllegalStateException("replace failed: unable to detach old script instance " + oldScriptInstanceId);
            }

            AttachScriptResult result = attachPreparedScript(loading, readyListener);
            log.info("script_runtime_replace_accepted seriesKey={} oldScriptInstanceId={} newScriptInstanceId={} scriptId={} executionMode={} attachAcceptedAtEpochMs={} bootstrapJobId={}",
                    SeriesKeyCodec.encode(seriesKey),
                    oldScriptInstanceId,
                    result.attachment().instanceId().value(),
                    result.attachment().scriptId().value(),
                    result.attachment().executionMode().name(),
                    result.attachAcceptedAtEpochMs(),
                    result.bootstrapJobId());
            logRegistryHubState(
                    "script_replace_accepted",
                    resolvedOwnerSessionId,
                    result.attachment().instanceId().value(),
                    result.attachment().scriptId().value(),
                    "replacedScriptInstanceId=" + oldScriptInstanceId);
            return result;
        });
    }

    public ScriptSnapshotAckResult markScriptSnapshotDelivered(
            String ownerSessionId,
            String scriptInstanceId,
            Long snapshotCursorSeq) {
        return runOnLoop(() -> {
            assertRunning();
            ScriptInstanceId id = new ScriptInstanceId(scriptInstanceId);
            ScriptAttachment attachment = attachmentManager.get(id).orElse(null);
            if (attachment == null) {
                log.info("script_runtime_snapshot_ack_rejected seriesKey={} scriptInstanceId={} reason=attachment_not_found",
                        SeriesKeyCodec.encode(seriesKey), scriptInstanceId);
                return new ScriptSnapshotAckResult(false, 0, null, null, new DeltaResponse(0, List.of()));
            }
            if (ownerSessionId != null && !ownerSessionId.equals(attachment.ownerSessionId())) {
                log.info("script_runtime_snapshot_ack_rejected seriesKey={} scriptInstanceId={} reason=owner_session_mismatch expectedOwnerSessionId={} providedOwnerSessionId={}",
                        SeriesKeyCodec.encode(seriesKey), scriptInstanceId, attachment.ownerSessionId(), ownerSessionId);
                return new ScriptSnapshotAckResult(false, 0, null, null, new DeltaResponse(0, List.of()));
            }
            BufferedScriptState buffered = bufferedScripts.get(scriptInstanceId);
            if (buffered == null || buffered.snapshotCursorSeq < 0) {
                log.info("script_runtime_snapshot_ack_rejected seriesKey={} scriptInstanceId={} reason=buffer_or_snapshot_cursor_missing snapshotCursorSeq={}",
                        SeriesKeyCodec.encode(seriesKey), scriptInstanceId, buffered == null ? null : buffered.snapshotCursorSeq);
                return new ScriptSnapshotAckResult(false, 0, null, null, new DeltaResponse(0, List.of()));
            }
            if (snapshotCursorSeq != null && snapshotCursorSeq.longValue() != buffered.snapshotCursorSeq) {
                log.info("script_runtime_snapshot_ack_rejected seriesKey={} scriptInstanceId={} reason=snapshot_cursor_mismatch providedCursor={} expectedCursor={}",
                        SeriesKeyCodec.encode(seriesKey), scriptInstanceId, snapshotCursorSeq, buffered.snapshotCursorSeq);
                return new ScriptSnapshotAckResult(false, 0, null, null, new DeltaResponse(buffered.snapshotCursorSeq, List.of()));
            }
            if (attachment.lifecycleState() == ScriptAttachmentState.FAILED
                    || buffered.status == ScriptBootstrapStatus.FAILED) {
                log.info("script_runtime_snapshot_ack_rejected seriesKey={} scriptInstanceId={} reason=attachment_or_bootstrap_failed lifecycleState={} bootstrapStatus={} bootstrapError={}",
                        SeriesKeyCodec.encode(seriesKey),
                        scriptInstanceId,
                        attachment.lifecycleState().name(),
                        buffered.status.name(),
                        buffered.error);
                return new ScriptSnapshotAckResult(false, 0, null, null, new DeltaResponse(buffered.snapshotCursorSeq, List.of()));
            }

            List<SessionEvent> replay = new ArrayList<>();
            for (SessionEvent event : buffered.events) {
                if (event.seq() > buffered.snapshotCursorSeq) {
                    replay.add(event);
                }
            }
            buffered.bufferingEnabled = false;
            buffered.events.clear();
            buffered.acked = true;

            ScriptAttachment active = attachment.withLifecycleState(ScriptAttachmentState.ACTIVE);
            attachmentManager.replace(active);

            Long fromSeq = replay.isEmpty() ? null : replay.get(0).seq();
            Long toSeq = replay.isEmpty() ? null : replay.get(replay.size() - 1).seq();
            long nextSeq = replay.isEmpty() ? buffered.snapshotCursorSeq : replay.get(replay.size() - 1).seq();
            log.info("script_runtime_snapshot_ack_activated seriesKey={} scriptInstanceId={} replayedEvents={} fromSeq={} toSeq={} snapshotCursorSeq={} nextSeq={}",
                    SeriesKeyCodec.encode(seriesKey),
                    scriptInstanceId,
                    replay.size(),
                    fromSeq,
                    toSeq,
                    buffered.snapshotCursorSeq,
                    nextSeq);
            return new ScriptSnapshotAckResult(
                    true,
                    replay.size(),
                    fromSeq,
                    toSeq,
                    new DeltaResponse(nextSeq, List.copyOf(replay)));
        });
    }

    public boolean detachScript(String ownerSessionId, String scriptInstanceId) {
        return runOnLoop(() -> {
            assertRunning();
            logRegistryHubState(
                    "script_detach_begin",
                    ownerSessionId,
                    scriptInstanceId,
                    null,
                    null);
            ScriptInstanceId id = new ScriptInstanceId(scriptInstanceId);
            Optional<ScriptAttachment> found = attachmentManager.get(id);
            if (found.isEmpty()) {
                logRegistryHubState(
                        "script_detach_not_found",
                        ownerSessionId,
                        scriptInstanceId,
                        null,
                        "attachment_not_found");
                return false;
            }
            ScriptAttachment current = found.get();
            if (ownerSessionId != null && !ownerSessionId.equals(current.ownerSessionId())) {
                logRegistryHubState(
                        "script_detach_rejected",
                        ownerSessionId,
                        scriptInstanceId,
                        current.scriptId().value(),
                        "owner_session_mismatch");
                return false;
            }
            boolean removed = detachAttachmentInternal(current);
            logRegistryHubState(
                    "script_detach_completed",
                    ownerSessionId,
                    scriptInstanceId,
                    current.scriptId().value(),
                    removed ? null : "attachment_remove_failed");
            return removed;
        });
    }

    public int detachOwnedBySession(String ownerSessionId) {
        return runOnLoop(() -> {
            assertRunning();
            logRegistryHubState(
                    "script_detach_owned_by_session_begin",
                    ownerSessionId,
                    null,
                    null,
                    null);
            int removed = 0;
            for (ScriptAttachment attachment : attachmentManager.orderedAttachments()) {
                if (!ownerSessionId.equals(attachment.ownerSessionId())) {
                    continue;
                }
                if (detachAttachmentInternal(attachment)) {
                    removed++;
                }
            }
            logRegistryHubState(
                    "script_detach_owned_by_session_completed",
                    ownerSessionId,
                    null,
                    null,
                    "removedCount=" + removed);
            return removed;
        });
    }

    public List<ScriptAttachment> listScripts() {
        return runOnLoop(() -> List.copyOf(attachmentManager.orderedAttachments()));
    }

    public DeltaResponse onSeriesAdvanced(SeriesAdvanceEvent event) {
        return runOnLoop(() -> {
            assertRunning();
            BarSeriesContext seriesContext = requiredSeriesContext();
            BarSeries series = seriesContext.series();
            long beforeSeq = registryHub.lastSeq();

            if (event.afterBeginIndex() > event.beforeBeginIndex()) {
                seriesContext.context().onEvict(event.afterBeginIndex());
            }

            switch (event.mutationType()) {
                case LIVE_UPSERT -> executeLiveUpsert(seriesContext, series, event.afterEndIndex());
                case APPEND -> executeAppendRange(seriesContext, event.beforeEndIndex(), event.afterEndIndex(), false);
                case BOOTSTRAP_SEED, BOOTSTRAP_REPLAY -> executeAppendRange(seriesContext, event.beforeEndIndex(),
                        event.afterEndIndex(), true);
                default -> {
                    // no-op
                }
            }

            updateFormingBarBlocked(series);
            DeltaResponse all = registryHub.deltasSince(beforeSeq, Integer.MAX_VALUE);
            return routeAndBufferEvents(all, beforeSeq);
        });
    }

    public Object snapshot(RegistryType type) {
        return runOnLoop(() -> registryHub.snapshot(type));
    }

    public Object snapshotForScript(RegistryType type, String scriptInstanceId) {
        return runOnLoop(() -> registryHub.snapshotForScript(type, scriptInstanceId));
    }

    public DeltaResponse deltasSince(long sinceSeq, int limit) {
        return runOnLoop(() -> registryHub.deltasSince(sinceSeq, limit));
    }

    public RuntimeHealth health() {
        return new RuntimeHealth(
                state,
                eventLoop.getQueue().size(),
                registryHub.lastSeq(),
                attachmentManager.size(),
                lastError,
                formingBarExecutionBlocked);
    }

    public RegistryHub.RegistryState registryState() {
        return registryHub.stateSnapshot();
    }

    public RegistryHub.RegistryDebugState registryDebugState() {
        return runOnLoop(registryHub::debugStateSnapshot);
    }

    public SeriesKey seriesKey() {
        return seriesKey;
    }

    public String ownerSessionIdForScript(String scriptInstanceId) {
        return runOnLoop(() -> attachmentManager.get(new ScriptInstanceId(scriptInstanceId))
                .map(ScriptAttachment::ownerSessionId)
                .orElse(null));
    }

    public java.util.Optional<ScriptAttachment> scriptAttachment(String scriptInstanceId) {
        return runOnLoop(() -> attachmentManager.get(new ScriptInstanceId(scriptInstanceId)));
    }

    private ScriptAttachment prepareLoadingAttachment(
            String scriptIdValue,
            Map<String, Object> rawParams,
            ScriptExecutionMode executionMode,
            String ownerSessionId,
            String ownerUserId) {
        BarSeriesContext seriesContext = requiredSeriesContext();

        ScriptId scriptId = new ScriptId(scriptIdValue);
        ScriptDefinition definition = scriptCatalog.get(scriptId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown scriptId: " + scriptIdValue));

        ScriptParams raw = new ScriptParams(rawParams == null ? Map.of() : rawParams);
        Object typedParams = definition.paramsBinder().bind(raw);
        List<ModuleSpec<?>> requiredSpecs = definition.requiredModules(typedParams);

        boolean planChanged = moduleRoots.addAll(requiredSpecs);
        if (planChanged) {
            seriesContext.rebuildContext(StrategyModulePlan.builder().addAll(moduleRoots).build());
        }

        ScriptInstanceId instanceId = ScriptInstanceId.newId();
        ScriptInitContext initContext = new ScriptInitContext(seriesContext, instanceId);
        return new ScriptAttachment(
                instanceId,
                definition.id(),
                definition.kind(),
                raw,
                typedParams,
                definition.createRuntime(initContext, typedParams),
                attachSequence.incrementAndGet(),
                executionMode,
                ScriptAttachmentState.LOADING,
                ownerSessionId,
                ownerUserId);
    }

    private AttachScriptResult attachPreparedScript(
            ScriptAttachment loading,
            Consumer<ScriptSnapshotReadyEvent> readyListener) {
        attachmentManager.attach(loading);

        ScriptInstanceId instanceId = loading.instanceId();
        long now = Instant.now().toEpochMilli();
        String bootstrapJobId = ScriptInstanceId.newId().value();
        bufferedScripts.put(instanceId.value(), new BufferedScriptState(bootstrapJobId, now, DEFAULT_BUFFER_LIMIT));
        Future<?> future = eventLoop.submit(() -> runScriptBootstrapOnLoop(instanceId, bootstrapJobId, readyListener));
        bootstrapFutures.put(instanceId.value(), future);

        log.info("script_runtime_attach_accepted seriesKey={} scriptInstanceId={} scriptId={} lifecycleState={} bootstrapJobId={} attachAcceptedAtEpochMs={} attachmentCount={} bufferedScriptCount={}",
                SeriesKeyCodec.encode(seriesKey),
                instanceId.value(),
                loading.scriptId().value(),
                loading.lifecycleState().name(),
                bootstrapJobId,
                now,
                attachmentManager.size(),
                bufferedScripts.size());
        return new AttachScriptResult(loading, bootstrapJobId, now);
    }

    private boolean detachAttachmentInternal(ScriptAttachment attachment) {
        Optional<ScriptAttachment> detached = attachmentManager.detach(attachment.instanceId());
        if (detached.isEmpty()) {
            return false;
        }
        BarSeriesContext context = seriesRegistry.get(seriesKey)
                .map(InMemoryConcurrentBarSeriesRegistry.SeriesEntry::context)
                .orElse(null);
        ScriptAttachment removed = detached.get();
        if (context != null) {
            removed.runtime().onDetach(executionContextFor(context, removed));
        } else {
            log.warn("runtime_detach_series_context_missing seriesKey={} scriptInstanceId={}",
                    SeriesKeyCodec.encode(seriesKey),
                    removed.instanceId().value());
        }
        String sid = removed.instanceId().value();
        registryHub.removeScriptArtifacts(sid);
        BufferedScriptState buffered = bufferedScripts.remove(sid);
        if (buffered != null) {
            buffered.bufferingEnabled = false;
            buffered.events.clear();
        }
        Future<?> future = bootstrapFutures.remove(sid);
        if (future != null) {
            future.cancel(true);
        }
        return true;
    }

    private void runScriptBootstrapOnLoop(
            ScriptInstanceId instanceId,
            String bootstrapJobId,
            Consumer<ScriptSnapshotReadyEvent> readyListener) {
        ScriptSnapshotReadyEvent completion;
        ScriptAttachment attachment = attachmentManager.get(instanceId).orElse(null);
        BufferedScriptState buffered = bufferedScripts.get(instanceId.value());
        if (attachment == null || buffered == null) {
            log.warn("script_runtime_bootstrap_skipped seriesKey={} scriptInstanceId={} bootstrapJobId={} reason=attachment_or_buffer_missing attachmentPresent={} bufferedPresent={}",
                    SeriesKeyCodec.encode(seriesKey),
                    instanceId.value(),
                    bootstrapJobId,
                    attachment != null,
                    buffered != null);
            return;
        }

        long startedAt = buffered.startedAtEpochMs;
        long startSeq = registryHub.lastSeq();
        log.info("script_runtime_bootstrap_begin seriesKey={} scriptInstanceId={} bootstrapJobId={} startSeq={} startedAtEpochMs={} executionMode={}",
                SeriesKeyCodec.encode(seriesKey),
                instanceId.value(),
                bootstrapJobId,
                startSeq,
                startedAt,
                attachment.executionMode().name());
        try {
            BarSeriesContext seriesContext = requiredSeriesContext();
            BarSeries series = seriesContext.series();
            int begin = series.getBeginIndex();
            int end = series.getEndIndex();
            if (begin >= 0 && end >= 0 && end >= begin) {
                seriesContext.context().ensureComputedUpTo(end);
                runCatchUp(seriesContext, attachment, begin, end);
            }

            DeltaResponse bootstrapDeltas = registryHub.deltasSince(startSeq, Integer.MAX_VALUE);
            routeAndBufferEvents(bootstrapDeltas, startSeq);

            buffered.snapshotCursorSeq = registryHub.lastSeq();
            buffered.completedAtEpochMs = Instant.now().toEpochMilli();
            buffered.status = buffered.droppedCount > 0 ? ScriptBootstrapStatus.DEGRADED : ScriptBootstrapStatus.READY;
            buffered.error = buffered.droppedCount > 0
                    ? "script_buffer_overflow_during_loading dropped=" + buffered.droppedCount
                    : null;

            completion = new ScriptSnapshotReadyEvent(
                    attachment.ownerSessionId(),
                    SeriesKeyCodec.encode(seriesKey),
                    instanceId.value(),
                    bootstrapJobId,
                    buffered.snapshotCursorSeq,
                    startedAt,
                    buffered.completedAtEpochMs,
                    buffered.status,
                    buffered.error);
            log.info("script_runtime_bootstrap_complete seriesKey={} scriptInstanceId={} bootstrapJobId={} status={} snapshotCursorSeq={} startedAtEpochMs={} completedAtEpochMs={} droppedCount={} bufferedEventCount={} error={}",
                    SeriesKeyCodec.encode(seriesKey),
                    instanceId.value(),
                    bootstrapJobId,
                    buffered.status.name(),
                    buffered.snapshotCursorSeq,
                    startedAt,
                    buffered.completedAtEpochMs,
                    buffered.droppedCount,
                    buffered.events.size(),
                    buffered.error);
        } catch (Exception e) {
            ScriptAttachment failed = attachment.withLifecycleState(ScriptAttachmentState.FAILED);
            attachmentManager.replace(failed);
            buffered.bufferingEnabled = false;
            buffered.completedAtEpochMs = Instant.now().toEpochMilli();
            buffered.status = ScriptBootstrapStatus.FAILED;
            buffered.error = e.getMessage() == null ? "script_bootstrap_failed" : e.getMessage();

            completion = new ScriptSnapshotReadyEvent(
                    attachment.ownerSessionId(),
                    SeriesKeyCodec.encode(seriesKey),
                    instanceId.value(),
                    bootstrapJobId,
                    buffered.snapshotCursorSeq,
                    startedAt,
                    buffered.completedAtEpochMs,
                    buffered.status,
                    buffered.error);
            log.warn("script_runtime_bootstrap_failed seriesKey={} scriptInstanceId={} bootstrapJobId={} startedAtEpochMs={} completedAtEpochMs={} snapshotCursorSeq={} error={}",
                    SeriesKeyCodec.encode(seriesKey),
                    instanceId.value(),
                    bootstrapJobId,
                    startedAt,
                    buffered.completedAtEpochMs,
                    buffered.snapshotCursorSeq,
                    buffered.error,
                    e);
        } finally {
            bootstrapFutures.remove(instanceId.value());
        }

        if (completion != null && readyListener != null) {
            readyListener.accept(completion);
        }
    }

    private void runCatchUp(
            BarSeriesContext seriesContext,
            ScriptAttachment attachment,
            int begin,
            int end) {
        if (attachment.executionMode() == ScriptExecutionMode.ON_TICK) {
            for (int i = begin; i <= end; i++) {
                attachment.runtime().onBar(executionContextFor(seriesContext, attachment), i);
            }
            return;
        }

        int closeEnd = Math.max(begin, end - 1);
        for (int i = begin; i <= closeEnd; i++) {
            attachment.runtime().onBar(executionContextFor(seriesContext, attachment), i);
        }
    }

    private void executeLiveUpsert(BarSeriesContext seriesContext, BarSeries series, int liveIndex) {
        if (liveIndex < 0 || liveIndex < series.getBeginIndex()) {
            return;
        }
        for (ScriptAttachment attachment : attachmentManager.orderedAttachments()) {
            if (!isExecutable(attachment)) {
                continue;
            }
            if (attachment.executionMode() != ScriptExecutionMode.ON_TICK) {
                continue;
            }
            attachment.runtime().onInvalidateFrom(executionContextFor(seriesContext, attachment), liveIndex);
            attachment.runtime().onBar(executionContextFor(seriesContext, attachment), liveIndex);
        }
    }

    private void executeAppendRange(BarSeriesContext seriesContext, int beforeEnd, int afterEnd, boolean inclusiveEnd) {
        int begin = seriesContext.series().getBeginIndex();
        if (begin < 0) {
            return;
        }
        int start = Math.max(begin, beforeEnd);
        int closedEnd = inclusiveEnd ? afterEnd : afterEnd - 1;
        if (closedEnd < 0) {
            return;
        }
        if (closedEnd < start) {
            return;
        }
        seriesContext.context().ensureComputedUpTo(Math.max(start, closedEnd));
        for (int i = start; i <= closedEnd; i++) {
            for (ScriptAttachment attachment : attachmentManager.orderedAttachments()) {
                if (!isExecutable(attachment)) {
                    continue;
                }
                if (attachment.executionMode() == ScriptExecutionMode.ON_CANDLE_CLOSE
                        || attachment.executionMode() == ScriptExecutionMode.ON_TICK) {
                    attachment.runtime().onBar(executionContextFor(seriesContext, attachment), i);
                }
            }
        }
    }

    private boolean isExecutable(ScriptAttachment attachment) {
        return attachment.lifecycleState() == ScriptAttachmentState.LOADING
                || attachment.lifecycleState() == ScriptAttachmentState.ACTIVE;
    }

    private void updateFormingBarBlocked(BarSeries series) {
        int begin = series.getBeginIndex();
        int end = series.getEndIndex();
        if (begin >= 0 && end >= 0 && end >= begin) {
            Instant lastEnd = series.getBar(end).getEndTime();
            formingBarExecutionBlocked = lastEnd.isAfter(Instant.now());
            return;
        }
        formingBarExecutionBlocked = false;
    }

    private BarSeriesContext requiredSeriesContext() {
        InMemoryConcurrentBarSeriesRegistry.SeriesEntry entry = seriesRegistry.get(seriesKey)
                .orElseThrow(() -> new IllegalStateException("Series entry not found: " + SeriesKeyCodec.encode(seriesKey)));
        return entry.context();
    }

    private ScriptExecutionContext executionContextFor(BarSeriesContext context, ScriptAttachment attachment) {
        return new ScriptExecutionContext(context, registryHub.writer(attachment.instanceId().value()));
    }

    private DeltaResponse routeAndBufferEvents(DeltaResponse response, long fallbackSeq) {
        if (response == null || response.events() == null || response.events().isEmpty()) {
            return new DeltaResponse(fallbackSeq, List.of());
        }
        List<SessionEvent> emitted = new ArrayList<>();
        for (SessionEvent event : response.events()) {
            String scriptInstanceId = extractScriptInstanceId(event.payload());
            if (scriptInstanceId != null && shouldBuffer(scriptInstanceId)) {
                BufferedScriptState buffered = bufferedScripts.get(scriptInstanceId);
                if (buffered != null) {
                    buffered.append(event);
                }
                continue;
            }
            emitted.add(event);
        }

        long nextSeq = emitted.isEmpty() ? fallbackSeq : emitted.get(emitted.size() - 1).seq();
        return new DeltaResponse(nextSeq, List.copyOf(emitted));
    }

    private boolean shouldBuffer(String scriptInstanceId) {
        BufferedScriptState buffered = bufferedScripts.get(scriptInstanceId);
        return buffered != null && buffered.bufferingEnabled;
    }

    private static String extractScriptInstanceId(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            Object sid = map.get("scriptInstanceId");
            if (sid != null) {
                return sid.toString();
            }
        }
        if (payload instanceof TradeIntent intent) {
            return intent.scriptInstanceId();
        }
        return null;
    }

    private void logRegistryHubState(
            String event,
            String ownerSessionId,
            String scriptInstanceId,
            String scriptId,
            String reason) {
        RegistryHub.RegistryState state = registryHub.stateSnapshot();
        log.info("registry_hub_state event={} seriesKey={} ownerSessionId={} scriptInstanceId={} scriptId={} reason={} runtimeState={} scriptCount={} queueDepth={} lastSeq={} drawingCount={} drawingOwnerCount={} plotSeriesCount={} plotOwnerCount={} totalPlotPointCount={} alertCount={} intentCount={}",
                event,
                SeriesKeyCodec.encode(seriesKey),
                ownerSessionId,
                scriptInstanceId,
                scriptId,
                reason,
                this.state.name(),
                attachmentManager.size(),
                eventLoop.getQueue().size(),
                state.lastSeq(),
                state.drawingCount(),
                state.drawingOwnerCount(),
                state.plotSeriesCount(),
                state.plotOwnerCount(),
                state.totalPlotPointCount(),
                state.alertCount(),
                state.intentCount());
    }

    private void assertRunning() {
        if (state != SeriesRuntimeState.RUNNING) {
            throw new IllegalStateException("Session is not running: " + seriesKey + " state=" + state);
        }
    }

    private <T> T runOnLoop(java.util.concurrent.Callable<T> task) {
        try {
            Future<T> future = eventLoop.submit(() -> {
                try {
                    return task.call();
                } catch (Exception e) {
                    lastError = e.getMessage();
                    state = SeriesRuntimeState.FAILED;
                    throw e;
                }
            });
            return future.get();
        } catch (Exception e) {
            log.warn("runtime_loop_operation_failed seriesKey={} runtimeState={} queueDepth={} reason={}",
                    SeriesKeyCodec.encode(seriesKey),
                    state.name(),
                    eventLoop.getQueue().size(),
                    e.getMessage());
            throw new IllegalStateException("Session operation failed for " + seriesKey + ": " + e.getMessage(), e);
        }
    }

    private static final class BufferedScriptState {
        private final String bootstrapJobId;
        private final long startedAtEpochMs;
        private final int maxEvents;
        private final ArrayDeque<SessionEvent> events = new ArrayDeque<>();
        private boolean bufferingEnabled = true;
        private boolean acked;
        private long snapshotCursorSeq = -1;
        private long completedAtEpochMs;
        private long droppedCount;
        private ScriptBootstrapStatus status = ScriptBootstrapStatus.READY;
        private String error;

        private BufferedScriptState(String bootstrapJobId, long startedAtEpochMs, int maxEvents) {
            this.bootstrapJobId = bootstrapJobId;
            this.startedAtEpochMs = startedAtEpochMs;
            this.maxEvents = maxEvents;
        }

        private void append(SessionEvent event) {
            events.addLast(event);
            if (events.size() > maxEvents) {
                events.removeFirst();
                droppedCount++;
            }
        }
    }
}
