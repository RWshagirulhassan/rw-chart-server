package com.readywealth.trading.trade_engine.engine.api.http;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.readywealth.trading.trade_engine.engine.api.http.dto.AttachScriptRequest;
import com.readywealth.trading.trade_engine.engine.api.http.dto.AttachScriptResponse;
import com.readywealth.trading.trade_engine.engine.api.http.dto.CandleIngestRequest;
import com.readywealth.trading.trade_engine.engine.api.http.dto.RuntimeHealthResponse;
import com.readywealth.trading.trade_engine.engine.api.http.dto.ScriptCatalogDetailsItem;
import com.readywealth.trading.trade_engine.engine.api.http.dto.ScriptCatalogItem;
import com.readywealth.trading.trade_engine.engine.api.http.dto.ScriptParamView;
import com.readywealth.trading.trade_engine.engine.api.http.dto.SessionCreateRequest;
import com.readywealth.trading.trade_engine.engine.api.http.dto.TradeIngestRequest;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptCatalog;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptDefinition;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptKind;
import com.readywealth.trading.trade_engine.engine.domain.series.Candle;
import com.readywealth.trading.trade_engine.engine.domain.series.CandleCause;
import com.readywealth.trading.trade_engine.engine.domain.series.IntervalKind;
import com.readywealth.trading.trade_engine.engine.domain.series.SeriesConfig;
import com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey;
import com.readywealth.trading.trade_engine.engine.application.runtime.ScriptExecutionMode;
import com.readywealth.trading.trade_engine.engine.application.runtime.RuntimeHealth;
import com.readywealth.trading.trade_engine.engine.application.runtime.SeriesKeyCodec;
import com.readywealth.trading.trade_engine.engine.application.runtime.SeriesRuntimeManager;
import com.readywealth.trading.trade_engine.engine.application.runtime.registry.RegistryType;

@RestController
@RequestMapping("/engine")
public class EngineRuntimeController {
    private static final int CATALOG_DETAILS_MAX_LIMIT = 500;


    private final SeriesRuntimeManager runtimeManager;
    private final ScriptCatalog scriptCatalog;

    public EngineRuntimeController(SeriesRuntimeManager runtimeManager, ScriptCatalog scriptCatalog) {
        this.runtimeManager = runtimeManager;
        this.scriptCatalog = scriptCatalog;
    }

    @PostMapping("/sessions")
    public ResponseEntity<?> createSession(@RequestBody SessionCreateRequest request) {
        SeriesKey key = new SeriesKey(request.instrumentToken(), IntervalKind.valueOf(request.timeframe()));
        int maxBarCount = request.maximumBarCount() == null ? SeriesConfig.liveDefault().maximumBarCount()
                : request.maximumBarCount();
        runtimeManager.createAndStart(key, new SeriesConfig(maxBarCount));
        var bootstrap = runtimeManager.bootstrapSnapshot(key);
        java.util.Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("seriesKey", SeriesKeyCodec.encode(key));
        response.put("state", "RUNNING");
        response.put("bootstrapStatus", bootstrap.status().name());
        response.put("bootstrapCutoffTime", bootstrap.cutoffAt());
        response.put("bootstrapSeededBars", bootstrap.seededBars());
        response.put("bootstrapReplayedTicks", bootstrap.replayedTicks());
        response.put("bootstrapDroppedTicks", bootstrap.droppedTicks());
        return ResponseEntity.ok().body(response);
    }

    @DeleteMapping("/sessions/{seriesKey:.+}")
    public ResponseEntity<?> deleteSession(@PathVariable String seriesKey) {
        boolean removed = runtimeManager.stopAndRemove(SeriesKeyCodec.decode(seriesKey));
        return ResponseEntity.ok(java.util.Map.of("removed", removed));
    }

    @PostMapping("/sessions/{seriesKey:.+}/trades")
    public ResponseEntity<?> ingestTrade(@PathVariable String seriesKey, @RequestBody TradeIngestRequest request) {
        return ResponseEntity.status(501).body(java.util.Map.of(
                "error",
                "Direct engine trade ingest is deprecated. Use marketdata tick ingestion and UI session series flow."));
    }

    @PostMapping("/sessions/{seriesKey:.+}/bars")
    public ResponseEntity<?> ingestBars(@PathVariable String seriesKey, @RequestBody CandleIngestRequest request) {
        return ResponseEntity.status(501).body(java.util.Map.of(
                "error",
                "Direct engine bar ingest is deprecated. Use bootstrap/tick pipeline and UI session series flow."));
    }

    @PostMapping("/sessions/{seriesKey:.+}/scripts/attach")
    public AttachScriptResponse attachScript(@PathVariable String seriesKey, @RequestBody AttachScriptRequest request) {
        var result = runtimeManager.attachScript(
                SeriesKeyCodec.decode(seriesKey),
                null,
                "legacy-engine-api",
                request.scriptId(),
                request.params(),
                ScriptExecutionMode.fromNullable(request.executionMode(), ScriptExecutionMode.ON_CANDLE_CLOSE));
        return new AttachScriptResponse(
                result.attachment().instanceId().value(),
                result.attachment().scriptId().value(),
                result.attachment().kind().name(),
                result.attachment().executionMode().name(),
                result.attachment().lifecycleState().name(),
                true,
                result.attachAcceptedAtEpochMs(),
                result.bootstrapJobId());
    }

    @PostMapping("/sessions/{seriesKey:.+}/scripts/{scriptInstanceId}/detach")
    public ResponseEntity<?> detachScript(
            @PathVariable String seriesKey,
            @PathVariable String scriptInstanceId) {
        boolean detached = runtimeManager.detachScript(SeriesKeyCodec.decode(seriesKey), null, scriptInstanceId);
        
        return ResponseEntity.ok(java.util.Map.of("detached", detached));
    }

    @GetMapping("/scripts/catalog")
    public List<ScriptCatalogItem> catalog() {
        List<ScriptCatalogItem> out = new ArrayList<>();
        for (ScriptDefinition definition : scriptCatalog.list()) {
            out.add(new ScriptCatalogItem(definition.id().value(), definition.kind().name()));
        }
        return out;
    }

    @GetMapping("/scripts/catalog/details")
    public List<ScriptCatalogDetailsItem> catalogDetails(
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        if (limit < 1 || limit > CATALOG_DETAILS_MAX_LIMIT) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "limit must be between 1 and " + CATALOG_DETAILS_MAX_LIMIT);
        }
        if (offset < 0) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "offset must be greater than or equal to 0");
        }

        ScriptKind parsedKind = parseScriptKind(kind);
        String normalizedQuery = q == null ? null : q.trim().toLowerCase(Locale.ROOT);
        final String finalNormalizedQuery = (normalizedQuery == null || normalizedQuery.isBlank())
                ? null
                : normalizedQuery;

        return scriptCatalog.list().stream()
                .filter(definition -> parsedKind == null || definition.kind() == parsedKind)
                .filter(definition -> {
                    if (finalNormalizedQuery == null) {
                        return true;
                    }
                    String name = definition.name() == null ? "" : definition.name();
                    String scriptId = definition.id() == null || definition.id().value() == null
                            ? ""
                            : definition.id().value();
                    return name.toLowerCase(Locale.ROOT).contains(finalNormalizedQuery)
                            || scriptId.toLowerCase(Locale.ROOT).contains(finalNormalizedQuery);
                })
                .map(this::toCatalogDetailsItem)
                .sorted(Comparator
                        .comparing((ScriptCatalogDetailsItem item) -> item.name().toLowerCase(Locale.ROOT))
                        .thenComparing(item -> item.scriptId().toLowerCase(Locale.ROOT)))
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @GetMapping("/sessions/{seriesKey:.+}/registries/{registryType}/snapshot")
    public Object snapshot(@PathVariable String seriesKey, @PathVariable String registryType) {
        return runtimeManager.snapshot(
                SeriesKeyCodec.decode(seriesKey),
                RegistryType.valueOf(registryType.toUpperCase()));
    }

    @GetMapping("/sessions/{seriesKey:.+}/registries/deltas")
    public Object deltas(
            @PathVariable String seriesKey,
            @RequestParam(defaultValue = "0") long sinceSeq,
            @RequestParam(defaultValue = "200") int limit) {
        return runtimeManager.deltasSince(SeriesKeyCodec.decode(seriesKey), sinceSeq, limit);
    }

    @GetMapping("/sessions/{seriesKey:.+}/health")
    public RuntimeHealthResponse health(@PathVariable String seriesKey) {
        SeriesKey key = SeriesKeyCodec.decode(seriesKey);
        RuntimeHealth health = runtimeManager.health(key);
        var bootstrap = runtimeManager.bootstrapSnapshot(key);
        return new RuntimeHealthResponse(
                health.state().name(),
                health.queueDepth(),
                health.lastSeq(),
                health.scriptCount(),
                health.lastError(),
                health.formingBarExecutionBlocked(),
                bootstrap.status().name(),
                bootstrap.error(),
                bootstrap.startedAt(),
                bootstrap.completedAt(),
                bootstrap.cutoffAt(),
                bootstrap.seededBars(),
                bootstrap.replayedTicks(),
                bootstrap.droppedTicks());
    }

    private ScriptKind parseScriptKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return null;
        }
        try {
            return ScriptKind.valueOf(kind.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "invalid kind. use INDICATOR or STRATEGY");
        }
    }

    private ScriptCatalogDetailsItem toCatalogDetailsItem(ScriptDefinition definition) {
        List<ScriptParamView> params = new ArrayList<>();
        for (var p : definition.paramsMetadata()) {
            params.add(new ScriptParamView(
                    p.name(),
                    p.type(),
                    p.required(),
                    p.defaultValue(),
                    p.description(),
                    p.options()));
        }
        return new ScriptCatalogDetailsItem(
                definition.id().value(),
                definition.name(),
                definition.kind().name(),
                definition.description(),
                params);
    }
}
