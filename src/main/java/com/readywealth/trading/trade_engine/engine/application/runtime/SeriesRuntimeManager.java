package com.readywealth.trading.trade_engine.engine.application.runtime;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.readywealth.trading.trade_engine.engine.domain.script.ScriptAttachment;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptCatalog;
import com.readywealth.trading.trade_engine.engine.domain.series.SeriesConfig;
import com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey;
import com.readywealth.trading.trade_engine.marketdata.application.SessionBootstrapCoordinator;
import com.readywealth.trading.trade_engine.session.infrastructure.memory.InMemoryConcurrentBarSeriesRegistry;
import com.readywealth.trading.trade_engine.engine.application.runtime.model.DeltaResponse;
import com.readywealth.trading.trade_engine.engine.application.runtime.registry.RegistryType;

@Service
public class SeriesRuntimeManager {
    private static final Logger log = LoggerFactory.getLogger(SeriesRuntimeManager.class);

    private final Map<SeriesKey, ChartSessionRuntime> sessions = new ConcurrentHashMap<>();
    private final ScriptCatalog scriptCatalog;
    private final SessionBootstrapCoordinator bootstrapCoordinator;
    private final InMemoryConcurrentBarSeriesRegistry seriesRegistry;

    public SeriesRuntimeManager(
            ScriptCatalog scriptCatalog,
            SessionBootstrapCoordinator bootstrapCoordinator,
            InMemoryConcurrentBarSeriesRegistry seriesRegistry) {
        this.scriptCatalog = scriptCatalog;
        this.bootstrapCoordinator = bootstrapCoordinator;
        this.seriesRegistry = seriesRegistry;
    }

    public ChartSessionRuntime createAndStart(SeriesKey key, SeriesConfig config) {
        seriesRegistry.acquire(key, config.maximumBarCount());
        ChartSessionRuntime runtime = ensureRuntimeForSeries(key);
        bootstrapCoordinator.bootstrapEngineSeriesAsync(key, candles -> {
            // Canonical bars are managed by session/marketdata registry pipeline.
        });
        return runtime;
    }

    public ChartSessionRuntime ensureRuntimeForSeries(SeriesKey key) {
        ChartSessionRuntime runtime = sessions.computeIfAbsent(
                key,
                k -> new ChartSessionRuntime(k, scriptCatalog, seriesRegistry));
        runtime.start();
        return runtime;
    }

    public boolean stopAndRemove(SeriesKey key) {
        ChartSessionRuntime runtime = sessions.remove(key);
        if (runtime == null) {
            log.info("runtime_stop_skipped seriesKey={} reason=not_found", SeriesKeyCodec.encode(key));
            return false;
        }
        try {
            log.info("runtime_stop_begin seriesKey={}", SeriesKeyCodec.encode(key));
            runtime.stop();
            log.info("runtime_stop_completed seriesKey={}", SeriesKeyCodec.encode(key));
            return true;
        } catch (Exception stopError) {
            log.error("runtime_stop_failed seriesKey={} reason={}",
                    SeriesKeyCodec.encode(key),
                    stopError.getMessage(),
                    stopError);
            throw stopError;
        }
    }

    public AttachScriptResult attachScript(
            SeriesKey key,
            String ownerSessionId,
            String ownerUserId,
            String scriptId,
            Map<String, Object> params,
            ScriptExecutionMode executionMode) {
        return attachScript(key, ownerSessionId, ownerUserId, scriptId, params, executionMode, null);
    }

    public AttachScriptResult attachScript(
            SeriesKey key,
            String ownerSessionId,
            String ownerUserId,
            String scriptId,
            Map<String, Object> params,
            ScriptExecutionMode executionMode,
            Consumer<ScriptSnapshotReadyEvent> readyListener) {
        return ensureRuntimeForSeries(key).attachScript(
                ownerSessionId,
                ownerUserId,
                scriptId,
                params,
                executionMode,
                readyListener);
    }

    public AttachScriptResult replaceScript(
            SeriesKey key,
            String ownerSessionId,
            String ownerUserId,
            String oldScriptInstanceId,
            Map<String, Object> params,
            ScriptExecutionMode executionMode,
            Consumer<ScriptSnapshotReadyEvent> readyListener) {
        return requiredSession(key).replaceScript(
                ownerSessionId,
                ownerUserId,
                oldScriptInstanceId,
                params,
                executionMode,
                readyListener);
    }

    public boolean detachScript(SeriesKey key, String ownerSessionId, String instanceId) {
        return requiredSession(key).detachScript(ownerSessionId, instanceId);
    }

    public int detachAllOwnedBySession(SeriesKey key, String ownerSessionId) {
        ChartSessionRuntime runtime = sessions.get(key);
        if (runtime == null) {
            log.info("runtime_detach_owned_skipped seriesKey={} ownerSessionId={} reason=not_found",
                    SeriesKeyCodec.encode(key),
                    ownerSessionId);
            return 0;
        }
        try {
            int removed = runtime.detachOwnedBySession(ownerSessionId);
            log.info("runtime_detach_owned_completed seriesKey={} ownerSessionId={} removed={}",
                    SeriesKeyCodec.encode(key),
                    ownerSessionId,
                    removed);
            return removed;
        } catch (Exception detachError) {
            log.warn("runtime_detach_owned_failed seriesKey={} ownerSessionId={} reason={}",
                    SeriesKeyCodec.encode(key),
                    ownerSessionId,
                    detachError.getMessage(),
                    detachError);
            return 0;
        }
    }

    public List<ScriptAttachment> listScripts(SeriesKey key) {
        return requiredSession(key).listScripts();
    }

    public DeltaResponse onSeriesAdvanced(SeriesKey key, SeriesAdvanceEvent event) {
        ChartSessionRuntime runtime = sessions.get(key);
        if (runtime == null) {
            return new DeltaResponse(0, List.of());
        }
        return runtime.onSeriesAdvanced(event);
    }

    public Object snapshot(SeriesKey key, RegistryType type) {
        return requiredSession(key).snapshot(type);
    }

    public Object snapshotForScript(SeriesKey key, RegistryType type, String scriptInstanceId) {
        return requiredSession(key).snapshotForScript(type, scriptInstanceId);
    }

    public ScriptSnapshotAckResult markScriptSnapshotDelivered(
            SeriesKey key,
            String ownerSessionId,
            String scriptInstanceId,
            Long snapshotCursorSeq) {
        return requiredSession(key).markScriptSnapshotDelivered(ownerSessionId, scriptInstanceId, snapshotCursorSeq);
    }

    public String ownerSessionIdForScript(SeriesKey key, String scriptInstanceId) {
        ChartSessionRuntime runtime = sessions.get(key);
        if (runtime == null) {
            return null;
        }
        return runtime.ownerSessionIdForScript(scriptInstanceId);
    }

    public Optional<ScriptAttachment> scriptAttachment(SeriesKey key, String scriptInstanceId) {
        ChartSessionRuntime runtime = sessions.get(key);
        if (runtime == null) {
            return Optional.empty();
        }
        return runtime.scriptAttachment(scriptInstanceId);
    }

    public DeltaResponse deltasSince(SeriesKey key, long sinceSeq, int limit) {
        return requiredSession(key).deltasSince(sinceSeq, limit);
    }

    public RuntimeHealth health(SeriesKey key) {
        return requiredSession(key).health();
    }

    public Optional<ChartSessionRuntime> get(SeriesKey key) {
        return Optional.ofNullable(sessions.get(key));
    }

    public Set<SeriesKey> activeSeriesKeys() {
        return Set.copyOf(sessions.keySet());
    }

    public InMemoryConcurrentBarSeriesRegistry.BootstrapSnapshot bootstrapSnapshot(SeriesKey key) {
        return bootstrapCoordinator.engineSnapshot(key);
    }

    private ChartSessionRuntime requiredSession(SeriesKey key) {
        ChartSessionRuntime runtime = sessions.get(key);
        if (runtime == null) {
            throw new IllegalArgumentException("Session not found for key: " + SeriesKeyCodec.encode(key));
        }
        return runtime;
    }
}
