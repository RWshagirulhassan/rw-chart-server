package com.readywealth.trading.trade_engine.session.api.http;

import com.readywealth.trading.trade_engine.engine.domain.script.ScriptAttachment;
import com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey;
import com.readywealth.trading.trade_engine.engine.application.runtime.registry.RegistryHub;
import com.readywealth.trading.trade_engine.session.api.http.dto.CreateUiSessionRequest;
import com.readywealth.trading.trade_engine.session.api.http.dto.ScriptSnapshotAckRequest;
import com.readywealth.trading.trade_engine.session.api.http.dto.ScriptSnapshotAckResponse;
import com.readywealth.trading.trade_engine.session.api.http.dto.SeriesSnapshotResponse;
import com.readywealth.trading.trade_engine.session.api.http.dto.UiAttachScriptRequest;
import com.readywealth.trading.trade_engine.session.api.http.dto.UiAttachScriptResponse;
import com.readywealth.trading.trade_engine.session.api.http.dto.UiReplaceScriptRequest;
import com.readywealth.trading.trade_engine.session.api.http.dto.UiReplaceScriptResponse;
import com.readywealth.trading.trade_engine.session.api.http.dto.UiScriptInstanceView;
import com.readywealth.trading.trade_engine.session.api.http.dto.UiSessionResponse;
import com.readywealth.trading.trade_engine.session.api.http.dto.UpdateUiSessionSeriesRequest;
import com.readywealth.trading.trade_engine.marketdata.api.ws.TicksWebSocketBroadcaster;
import com.readywealth.trading.trade_engine.marketdata.api.ws.dto.ScriptDeltaWsEvent;
import com.readywealth.trading.trade_engine.marketdata.api.ws.dto.ScriptSnapshotReadyWsEvent;
import com.readywealth.trading.trade_engine.engine.application.runtime.ScriptExecutionMode;
import com.readywealth.trading.trade_engine.engine.application.runtime.SeriesKeyCodec;
import com.readywealth.trading.trade_engine.engine.application.runtime.SeriesRuntimeManager;
import com.readywealth.trading.trade_engine.engine.application.runtime.registry.RegistryType;
import com.readywealth.trading.trade_engine.session.application.SessionIdentityService;
import com.readywealth.trading.trade_engine.session.application.SessionSeriesCoordinator;
import com.readywealth.trading.trade_engine.session.domain.UiSession;
import com.readywealth.trading.trade_engine.session.domain.UiSessionId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/engine/ui-sessions")
public class UiSessionController {
    private static final Logger log = LoggerFactory.getLogger(UiSessionController.class);

    private final SessionSeriesCoordinator coordinator;
    private final SessionIdentityService sessionIdentityService;
    private final SeriesRuntimeManager runtimeManager;
    private final TicksWebSocketBroadcaster webSocketBroadcaster;

    public UiSessionController(
            SessionSeriesCoordinator coordinator,
            SessionIdentityService sessionIdentityService,
            SeriesRuntimeManager runtimeManager,
            TicksWebSocketBroadcaster webSocketBroadcaster) {
        this.coordinator = coordinator;
        this.sessionIdentityService = sessionIdentityService;
        this.runtimeManager = runtimeManager;
        this.webSocketBroadcaster = webSocketBroadcaster;
    }

    @PostMapping
    public UiSessionResponse create(@RequestBody CreateUiSessionRequest request) {
        String userId = sessionIdentityService.requireCurrentUserId();
        log.info("sync_ui_create_request userId={} seriesKeys={} maxBarCount={} destroyOnClose={}",
                userId, request.seriesKeys(), request.maxBarCount(), request.destroyOnClose());
        UiSession session = coordinator.createSession(userId, request.seriesKeys(), request.maxBarCount(), request.destroyOnClose());
        log.info("sync_ui_create_response userId={} sessionId={} state={} connectedClients={}",
                userId, session.sessionId().value(), session.state(), session.connectedClients());
        return toResponse(session);
    }

    @GetMapping
    public List<UiSessionResponse> list() {
        String userId = sessionIdentityService.requireCurrentUserId();
        return coordinator.listSessionsForUser(userId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/debug/state")
    public BackendDebugStateResponse debugState() {
        String requestedByUserId = sessionIdentityService.requireCurrentUserId();
        List<UiSession> sessions = coordinator.listAllSessions();
        Set<SeriesKey> allSeries = new LinkedHashSet<>();
        for (UiSession session : sessions) {
            allSeries.addAll(session.attachedSeries());
        }
        allSeries.addAll(runtimeManager.activeSeriesKeys());

        Map<String, List<ScriptAttachment>> scriptsBySeries = new LinkedHashMap<>();
        for (SeriesKey key : allSeries) {
            String encoded = SeriesKeyCodec.encode(key);
            List<ScriptAttachment> scripts = runtimeManager.get(key)
                    .map(runtime -> runtime.listScripts().stream()
                            .sorted(Comparator
                                    .comparing((ScriptAttachment item) -> valueOrEmpty(item.ownerSessionId()))
                                    .thenComparing(item -> item.instanceId().value()))
                            .toList())
                    .orElse(List.of());
            scriptsBySeries.put(encoded, scripts);
        }

        List<DebugSessionView> sessionViews = sessions.stream()
                .map(session -> toDebugSessionView(session, scriptsBySeries))
                .sorted(Comparator.comparing(DebugSessionView::sessionId))
                .toList();

        List<DebugSeriesRuntimeView> runtimeViews = allSeries.stream()
                .map(this::toDebugSeriesRuntimeView)
                .sorted(Comparator.comparing(DebugSeriesRuntimeView::seriesKey))
                .toList();

        return new BackendDebugStateResponse(
                Instant.now(),
                requestedByUserId,
                sessionViews.size(),
                runtimeViews.stream().filter(DebugSeriesRuntimeView::runtimePresent).count(),
                sessionViews,
                runtimeViews);
    }

    @PostMapping("/{sessionId}/series")
    public UiSessionResponse addSeries(@PathVariable String sessionId, @RequestBody UpdateUiSessionSeriesRequest request) {
        String userId = sessionIdentityService.requireCurrentUserId();
        UiSession session = coordinator.addSeries(userId, new UiSessionId(sessionId), request.seriesKeys());
        return toResponse(session);
    }

    @DeleteMapping("/{sessionId}/series")
    public UiSessionResponse removeSeries(@PathVariable String sessionId, @RequestBody UpdateUiSessionSeriesRequest request) {
        String userId = sessionIdentityService.requireCurrentUserId();
        UiSession session = coordinator.removeSeries(userId, new UiSessionId(sessionId), request.seriesKeys());
        return toResponse(session);
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<?> close(@PathVariable String sessionId) {
        String userId = sessionIdentityService.requireCurrentUserId();
        log.info("sync_ui_close_request userId={} sessionId={}", userId, sessionId);
        boolean removed = coordinator.closeSession(userId, new UiSessionId(sessionId));
        log.info("sync_ui_close_response userId={} sessionId={} removed={}", userId, sessionId, removed);
        return ResponseEntity.ok(java.util.Map.of("removed", removed));
    }

    @GetMapping("/{sessionId}/series/{seriesKey:.+}/snapshot")
    public SeriesSnapshotResponse snapshot(@PathVariable String sessionId, @PathVariable String seriesKey) {
        String userId = sessionIdentityService.requireCurrentUserId();
        var response = coordinator.snapshot(userId, new UiSessionId(sessionId), SeriesKeyCodec.decode(seriesKey));
        log.info("sync_ui_snapshot_response userId={} sessionId={} seriesKey={} snapshotCursor={} lastSeq={} beginIndex={} endIndex={} bars={}",
                userId,
                sessionId,
                seriesKey,
                response.snapshotCursor(),
                response.lastSeq(),
                response.beginIndex(),
                response.endIndex(),
                response.bars() == null ? 0 : response.bars().size());
        return response;
    }

    @GetMapping("/{sessionId}/series/{seriesKey:.+}/deltas")
    public ResponseEntity<?> deltasNotImplemented(@PathVariable String sessionId, @PathVariable String seriesKey) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(java.util.Map.of("error", "Per-series REST deltas are not implemented in v1. Use /ticks/ws candle_appended and candle_live_upsert events."));
    }

    @PostMapping("/{sessionId}/series/{seriesKey:.+}/scripts/attach")
    public ResponseEntity<UiAttachScriptResponse> attachScript(
            @PathVariable String sessionId,
            @PathVariable String seriesKey,
            @RequestBody UiAttachScriptRequest request) {
        String userId = sessionIdentityService.requireCurrentUserId();
        UiSession session = coordinator.getSession(userId, new UiSessionId(sessionId));
        var key = SeriesKeyCodec.decode(seriesKey);
        if (!session.attachedSeries().contains(key)) {
            throw new IllegalArgumentException("seriesKey is not attached to session: " + seriesKey);
        }
        ScriptExecutionMode mode = ScriptExecutionMode.fromNullable(request.executionMode(), ScriptExecutionMode.ON_CANDLE_CLOSE);
        var paramKeys = request.params() == null ? java.util.Set.<String>of() : request.params().keySet();
        log.info("script_attach_request userId={} sessionId={} seriesKey={} scriptId={} executionMode={} paramKeys={}",
                userId, sessionId, seriesKey, request.scriptId(), mode.name(), paramKeys);
        var result = runtimeManager.attachScript(
                key,
                sessionId,
                userId,
                request.scriptId(),
                request.params(),
                mode,
                ready -> {
                    log.info(
                            "script_snapshot_ready_emit sessionId={} seriesKey={} scriptInstanceId={} bootstrapJobId={} status={} snapshotCursorSeq={} startedAtEpochMs={} completedAtEpochMs={} error={}",
                            sessionId,
                            seriesKey,
                            ready.scriptInstanceId(),
                            ready.bootstrapJobId(),
                            ready.status().name(),
                            ready.snapshotCursorSeq(),
                            ready.startedAtEpochMs(),
                            ready.completedAtEpochMs(),
                            ready.error());
                    webSocketBroadcaster.broadcastScriptSnapshotReadyToSession(
                            new UiSessionId(sessionId),
                            new ScriptSnapshotReadyWsEvent(
                                    sessionId,
                                    seriesKey,
                                    ready.scriptInstanceId(),
                                    ready.bootstrapJobId(),
                                    ready.snapshotCursorSeq(),
                                    ready.startedAtEpochMs(),
                                    ready.completedAtEpochMs(),
                                    ready.status().name(),
                                    ready.error()));
                });
        UiAttachScriptResponse body = new UiAttachScriptResponse(
                result.attachment().instanceId().value(),
                result.attachment().scriptId().value(),
                result.attachment().kind().name(),
                result.attachment().executionMode().name(),
                sessionId,
                seriesKey,
                result.attachment().lifecycleState().name(),
                true,
                result.attachAcceptedAtEpochMs(),
                result.bootstrapJobId());
        log.info(
                "script_attach_accepted userId={} sessionId={} seriesKey={} scriptInstanceId={} scriptId={} kind={} executionMode={} lifecycleState={} attachAcceptedAtEpochMs={} bootstrapJobId={}",
                userId,
                sessionId,
                seriesKey,
                body.scriptInstanceId(),
                body.scriptId(),
                body.kind(),
                body.executionMode(),
                body.lifecycleState(),
                body.attachAcceptedAtEpochMs(),
                body.bootstrapJobId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    @PostMapping("/{sessionId}/series/{seriesKey:.+}/scripts/{scriptInstanceId}/detach")
    public ResponseEntity<?> detachScript(
            @PathVariable String sessionId,
            @PathVariable String seriesKey,
            @PathVariable String scriptInstanceId) {
        String userId = sessionIdentityService.requireCurrentUserId();
        UiSession session = coordinator.getSession(userId, new UiSessionId(sessionId));
        var key = SeriesKeyCodec.decode(seriesKey);
        if (!session.attachedSeries().contains(key)) {
            throw new IllegalArgumentException("seriesKey is not attached to session: " + seriesKey);
        }
        boolean detached = runtimeManager.detachScript(key, sessionId, scriptInstanceId);
        return ResponseEntity.ok(java.util.Map.of("detached", detached));
    }

    @PostMapping("/{sessionId}/series/{seriesKey:.+}/scripts/{scriptInstanceId}/replace")
    public ResponseEntity<UiReplaceScriptResponse> replaceScript(
            @PathVariable String sessionId,
            @PathVariable String seriesKey,
            @PathVariable String scriptInstanceId,
            @RequestBody UiReplaceScriptRequest request) {
        if (request == null || request.params() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "params is required");
        }
        String userId = sessionIdentityService.requireCurrentUserId();
        UiSession session = coordinator.getSession(userId, new UiSessionId(sessionId));
        var key = SeriesKeyCodec.decode(seriesKey);
        if (!session.attachedSeries().contains(key)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "seriesKey is not attached to session");
        }
        if (!isOwnedScriptInstance(key, sessionId, scriptInstanceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "script instance not found for session and series");
        }

        ScriptExecutionMode requestedMode = ScriptExecutionMode.fromNullable(request.executionMode(), null);
        var paramKeys = request.params().keySet();
        log.info("script_replace_request userId={} sessionId={} seriesKey={} replacedScriptInstanceId={} executionMode={} paramKeys={}",
                userId,
                sessionId,
                seriesKey,
                scriptInstanceId,
                requestedMode == null ? "<preserve>" : requestedMode.name(),
                paramKeys);
        var result = runtimeManager.replaceScript(
                key,
                sessionId,
                userId,
                scriptInstanceId,
                request.params(),
                requestedMode,
                ready -> {
                    log.info(
                            "script_snapshot_ready_emit sessionId={} seriesKey={} scriptInstanceId={} bootstrapJobId={} status={} snapshotCursorSeq={} startedAtEpochMs={} completedAtEpochMs={} error={}",
                            sessionId,
                            seriesKey,
                            ready.scriptInstanceId(),
                            ready.bootstrapJobId(),
                            ready.status().name(),
                            ready.snapshotCursorSeq(),
                            ready.startedAtEpochMs(),
                            ready.completedAtEpochMs(),
                            ready.error());
                    webSocketBroadcaster.broadcastScriptSnapshotReadyToSession(
                            new UiSessionId(sessionId),
                            new ScriptSnapshotReadyWsEvent(
                                    sessionId,
                                    seriesKey,
                                    ready.scriptInstanceId(),
                                    ready.bootstrapJobId(),
                                    ready.snapshotCursorSeq(),
                                    ready.startedAtEpochMs(),
                                    ready.completedAtEpochMs(),
                                    ready.status().name(),
                                    ready.error()));
                });
        UiReplaceScriptResponse body = new UiReplaceScriptResponse(
                scriptInstanceId,
                result.attachment().instanceId().value(),
                result.attachment().scriptId().value(),
                result.attachment().kind().name(),
                result.attachment().executionMode().name(),
                sessionId,
                seriesKey,
                result.attachment().lifecycleState().name(),
                true,
                result.attachAcceptedAtEpochMs(),
                result.bootstrapJobId());
        log.info(
                "script_replace_accepted userId={} sessionId={} seriesKey={} replacedScriptInstanceId={} scriptInstanceId={} scriptId={} kind={} executionMode={} lifecycleState={} attachAcceptedAtEpochMs={} bootstrapJobId={}",
                userId,
                sessionId,
                seriesKey,
                body.replacedScriptInstanceId(),
                body.scriptInstanceId(),
                body.scriptId(),
                body.kind(),
                body.executionMode(),
                body.lifecycleState(),
                body.attachAcceptedAtEpochMs(),
                body.bootstrapJobId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    @GetMapping("/{sessionId}/series/{seriesKey:.+}/scripts")
    public List<UiScriptInstanceView> listScripts(
            @PathVariable String sessionId,
            @PathVariable String seriesKey) {
        String userId = sessionIdentityService.requireCurrentUserId();
        UiSession session = coordinator.getSession(userId, new UiSessionId(sessionId));
        var key = SeriesKeyCodec.decode(seriesKey);
        if (!session.attachedSeries().contains(key)) {
            throw new IllegalArgumentException("seriesKey is not attached to session: " + seriesKey);
        }
        return runtimeManager.listScripts(key).stream()
                .map(item -> new UiScriptInstanceView(
                        item.instanceId().value(),
                        item.scriptId().value(),
                        item.kind().name(),
                        item.executionMode().name(),
                        item.lifecycleState().name(),
                        item.ownerSessionId(),
                        item.ownerUserId()))
                .toList();
    }

    @PostMapping("/{sessionId}/series/{seriesKey:.+}/scripts/{scriptInstanceId}/snapshot-ack")
    public ScriptSnapshotAckResponse snapshotAck(
            @PathVariable String sessionId,
            @PathVariable String seriesKey,
            @PathVariable String scriptInstanceId,
            @RequestBody(required = false) ScriptSnapshotAckRequest request) {
        String userId = sessionIdentityService.requireCurrentUserId();
        UiSession session = coordinator.getSession(userId, new UiSessionId(sessionId));
        var key = SeriesKeyCodec.decode(seriesKey);
        if (!session.attachedSeries().contains(key)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "seriesKey is not attached to session");
        }
        if (!isOwnedScriptInstance(key, sessionId, scriptInstanceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "script instance not found for session and series");
        }

        Long cursor = request == null ? null : request.snapshotCursorSeq();
        log.info("script_snapshot_ack_request userId={} sessionId={} seriesKey={} scriptInstanceId={} snapshotCursorSeq={}",
                userId, sessionId, seriesKey, scriptInstanceId, cursor);
        var result = runtimeManager.markScriptSnapshotDelivered(key, sessionId, scriptInstanceId, cursor);
        broadcastScriptDeltasToSession(new UiSessionId(sessionId), seriesKey, result.replayDeltas());
        log.info("script_snapshot_ack_result userId={} sessionId={} seriesKey={} scriptInstanceId={} activated={} replayedEvents={} fromSeq={} toSeq={}",
                userId,
                sessionId,
                seriesKey,
                scriptInstanceId,
                result.activated(),
                result.replayedEvents(),
                result.fromSeq(),
                result.toSeq());
        return new ScriptSnapshotAckResponse(
                result.activated(),
                result.replayedEvents(),
                result.fromSeq(),
                result.toSeq());
    }

    @GetMapping("/{sessionId}/series/{seriesKey:.+}/scripts/{scriptInstanceId}/registries/{registryType}/snapshot")
    public Object snapshotScriptRegistry(
            @PathVariable String sessionId,
            @PathVariable String seriesKey,
            @PathVariable String scriptInstanceId,
            @PathVariable String registryType) {
        String userId = sessionIdentityService.requireCurrentUserId();
        UiSession session = coordinator.getSession(userId, new UiSessionId(sessionId));
        var key = SeriesKeyCodec.decode(seriesKey);
        if (!session.attachedSeries().contains(key)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "seriesKey is not attached to session");
        }

        RegistryType type;
        try {
            type = RegistryType.valueOf(registryType.toUpperCase());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported registryType. use plot|drawing");
        }
        if (type != RegistryType.PLOT && type != RegistryType.DRAWING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported registryType. use plot|drawing");
        }

        if (!isOwnedScriptInstance(key, sessionId, scriptInstanceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "script instance not found for session and series");
        }

        log.info("script_registry_snapshot_request userId={} sessionId={} seriesKey={} scriptInstanceId={} registryType={}",
                userId, sessionId, seriesKey, scriptInstanceId, type.name());
        Object snapshot = runtimeManager.snapshotForScript(key, type, scriptInstanceId);
        if (snapshot instanceof java.util.Map<?, ?> map) {
            log.info("script_registry_snapshot_response sessionId={} seriesKey={} scriptInstanceId={} registryType={} size={}",
                    sessionId, seriesKey, scriptInstanceId, type.name(), map.size());
        } else {
            log.info("script_registry_snapshot_response sessionId={} seriesKey={} scriptInstanceId={} registryType={} payloadClass={}",
                    sessionId, seriesKey, scriptInstanceId, type.name(), snapshot == null ? "null" : snapshot.getClass().getSimpleName());
        }
        return snapshot;
    }

    private void broadcastScriptDeltasToSession(
            UiSessionId target,
            String seriesKey,
            com.readywealth.trading.trade_engine.engine.application.runtime.model.DeltaResponse deltas) {
        if (deltas == null || deltas.events() == null || deltas.events().isEmpty()) {
            return;
        }
        for (var event : deltas.events()) {
            Object payload = event.payload();
            String scriptInstanceId = null;
            String registryType = null;
            if (payload instanceof java.util.Map<?, ?> map) {
                Object sid = map.get("scriptInstanceId");
                if (sid != null) {
                    scriptInstanceId = sid.toString();
                }
            } else if (payload instanceof com.readywealth.trading.trade_engine.engine.application.runtime.model.TradeIntent intent) {
                scriptInstanceId = intent.scriptInstanceId();
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
            ScriptDeltaWsEvent wsEvent = new ScriptDeltaWsEvent(
                    target.value(),
                    seriesKey,
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
        }
    }

    private UiSessionResponse toResponse(UiSession session) {
        var keys = new ArrayList<String>();
        for (var key : session.attachedSeries()) {
            keys.add(SeriesKeyCodec.encode(key));
        }
        var bootstrap = coordinator.bootstrapSummary(session);
        return new UiSessionResponse(
                session.sessionId().value(),
                session.userId(),
                session.destroyOnClose(),
                keys,
                session.createdAt(),
                session.updatedAt(),
                session.lastSeenAt(),
                session.connectedClients(),
                session.state(),
                bootstrap.status(),
                bootstrap.startedAt(),
                bootstrap.completedAt(),
                bootstrap.cutoffAt(),
                bootstrap.error(),
                bootstrap.seededBars(),
                bootstrap.replayedTicks(),
                bootstrap.droppedTicks());
    }

    private DebugSessionView toDebugSessionView(
            UiSession session,
            Map<String, List<ScriptAttachment>> scriptsBySeries) {
        List<String> attachedSeriesKeys = session.attachedSeries().stream()
                .map(SeriesKeyCodec::encode)
                .sorted()
                .toList();

        List<DebugAttachedScriptView> attachedScripts = new ArrayList<>();
        for (String seriesKey : attachedSeriesKeys) {
            List<ScriptAttachment> scripts = scriptsBySeries.getOrDefault(seriesKey, List.of());
            for (ScriptAttachment script : scripts) {
                if (!session.sessionId().value().equals(script.ownerSessionId())) {
                    continue;
                }
                attachedScripts.add(new DebugAttachedScriptView(
                        seriesKey,
                        script.instanceId().value(),
                        script.scriptId().value(),
                        script.kind().name(),
                        script.executionMode().name(),
                        script.lifecycleState().name()));
            }
        }
        attachedScripts.sort(Comparator
                .comparing(DebugAttachedScriptView::seriesKey)
                .thenComparing(DebugAttachedScriptView::scriptInstanceId));

        return new DebugSessionView(
                session.sessionId().value(),
                session.userId(),
                session.state().name(),
                session.destroyOnClose(),
                session.connectedClients(),
                session.createdAt(),
                session.updatedAt(),
                session.lastSeenAt(),
                attachedSeriesKeys,
                List.copyOf(attachedScripts));
    }

    private DebugSeriesRuntimeView toDebugSeriesRuntimeView(SeriesKey key) {
        String encodedSeriesKey = SeriesKeyCodec.encode(key);
        var runtimeOpt = runtimeManager.get(key);
        if (runtimeOpt.isEmpty()) {
            return new DebugSeriesRuntimeView(
                    encodedSeriesKey,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of());
        }

        var runtime = runtimeOpt.get();
        var health = runtime.health();
        RegistryHub.RegistryDebugState registryHubState = runtime.registryDebugState();
        List<DebugScriptInstanceView> scripts = runtime.listScripts().stream()
                .map(item -> new DebugScriptInstanceView(
                        item.instanceId().value(),
                        item.scriptId().value(),
                        item.kind().name(),
                        item.executionMode().name(),
                        item.lifecycleState().name(),
                        item.ownerSessionId(),
                        item.ownerUserId()))
                .sorted(Comparator
                        .comparing((DebugScriptInstanceView item) -> valueOrEmpty(item.ownerSessionId()))
                        .thenComparing(DebugScriptInstanceView::scriptInstanceId))
                .toList();

        return new DebugSeriesRuntimeView(
                encodedSeriesKey,
                true,
                health.state().name(),
                health.queueDepth(),
                health.lastSeq(),
                health.scriptCount(),
                health.lastError(),
                health.formingBarExecutionBlocked(),
                registryHubState,
                scripts);
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean isOwnedScriptInstance(SeriesKey key, String sessionId, String scriptInstanceId) {
        return runtimeManager.listScripts(key).stream()
                .anyMatch(item -> item.instanceId().value().equals(scriptInstanceId)
                        && sessionId.equals(item.ownerSessionId()));
    }

    public record BackendDebugStateResponse(
            Instant generatedAt,
            String requestedByUserId,
            int activeSessionCount,
            long activeSeriesRuntimeCount,
            List<DebugSessionView> sessions,
            List<DebugSeriesRuntimeView> seriesRuntimes) {
    }

    public record DebugSessionView(
            String sessionId,
            String userId,
            String state,
            boolean destroyOnClose,
            int connectedClients,
            Instant createdAt,
            Instant updatedAt,
            Instant lastSeenAt,
            List<String> attachedSeriesKeys,
            List<DebugAttachedScriptView> attachedScripts) {
    }

    public record DebugAttachedScriptView(
            String seriesKey,
            String scriptInstanceId,
            String scriptId,
            String kind,
            String executionMode,
            String lifecycleState) {
    }

    public record DebugSeriesRuntimeView(
            String seriesKey,
            boolean runtimePresent,
            String runtimeState,
            Integer queueDepth,
            Long lastSeq,
            Integer scriptCount,
            String lastError,
            Boolean formingBarExecutionBlocked,
            RegistryHub.RegistryDebugState registryHub,
            List<DebugScriptInstanceView> scripts) {
    }

    public record DebugScriptInstanceView(
            String scriptInstanceId,
            String scriptId,
            String kind,
            String executionMode,
            String lifecycleState,
            String ownerSessionId,
            String ownerUserId) {
    }
}
