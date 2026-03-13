package com.readywealth.trading.trade_engine.engine.application.runtime.registry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.readywealth.trading.trade_engine.engine.application.runtime.model.DeltaResponse;
import com.readywealth.trading.trade_engine.engine.application.runtime.model.TradeIntent;

public final class RegistryHub {

    private static final Logger log = LoggerFactory.getLogger(RegistryHub.class);

    private final SessionEventLog eventLog = new SessionEventLog();
    private final Map<String, Map<String, Object>> drawingsByScriptInstance = new LinkedHashMap<>();
    private final Map<String, Map<String, List<Object>>> plotsByScriptInstance = new LinkedHashMap<>();
    private final List<AlertEntry> alerts = new ArrayList<>();
    private final List<TradeIntent> intents = new ArrayList<>();

    private record AlertEntry(String scriptInstanceId, Object payload) {
    }

    public record RegistryState(
            long lastSeq,
            int drawingCount,
            int drawingOwnerCount,
            int plotSeriesCount,
            int plotOwnerCount,
            int totalPlotPointCount,
            int alertCount,
            int intentCount) {
    }

    public record PlotSeriesState(
            String plotId,
            int pointCount) {
    }

    public record ScriptRegistryState(
            String scriptInstanceId,
            int drawingCount,
            List<String> drawingIds,
            int plotSeriesCount,
            int totalPlotPointCount,
            List<PlotSeriesState> plotSeries,
            int alertCount,
            int intentCount) {
    }

    public record RegistryDebugState(
            long lastSeq,
            int drawingCount,
            int drawingOwnerCount,
            int plotSeriesCount,
            int plotOwnerCount,
            int totalPlotPointCount,
            int alertCount,
            int intentCount,
            List<ScriptRegistryState> byScriptInstance) {
    }

    public Writer writer(String scriptInstanceId) {
        return new Writer(scriptInstanceId);
    }

    public synchronized Object snapshot(RegistryType type) {
        return switch (type) {
            case DRAWING -> deepCopyDrawings();
            case PLOT -> deepCopyPlots();
            case ALERT -> deepCopyAlerts();
            case INTENT -> List.copyOf(intents);
        };
    }

    public synchronized Object snapshotForScript(RegistryType type, String scriptInstanceId) {
        return switch (type) {
            case DRAWING -> drawingSnapshotForScript(scriptInstanceId);
            case PLOT -> plotSnapshotForScript(scriptInstanceId);
            case ALERT, INTENT -> throw new IllegalArgumentException(
                    "snapshotForScript supports only DRAWING and PLOT");
        };
    }

    public synchronized DeltaResponse deltasSince(long sinceSeq, int limit) {
        return eventLog.deltasSince(sinceSeq, limit);
    }

    public synchronized long lastSeq() {
        return eventLog.lastSeq();
    }

    public synchronized RegistryState stateSnapshot() {
        int drawingCount = 0;
        for (Map<String, Object> drawingMap : drawingsByScriptInstance.values()) {
            drawingCount += drawingMap.size();
        }

        int plotSeriesCount = 0;
        int totalPlotPointCount = 0;
        for (Map<String, List<Object>> plotMap : plotsByScriptInstance.values()) {
            plotSeriesCount += plotMap.size();
            for (List<Object> points : plotMap.values()) {
                totalPlotPointCount += points.size();
            }
        }

        return new RegistryState(
                eventLog.lastSeq(),
                drawingCount,
                drawingsByScriptInstance.size(),
                plotSeriesCount,
                plotsByScriptInstance.size(),
                totalPlotPointCount,
                alerts.size(),
                intents.size());
    }

    public synchronized RegistryDebugState debugStateSnapshot() {
        RegistryState totals = stateSnapshot();
        Map<String, Integer> alertCountsByScript = new LinkedHashMap<>();
        for (AlertEntry entry : alerts) {
            alertCountsByScript.merge(entry.scriptInstanceId(), 1, Integer::sum);
        }
        Map<String, Integer> intentCountsByScript = new LinkedHashMap<>();
        for (TradeIntent intent : intents) {
            intentCountsByScript.merge(intent.scriptInstanceId(), 1, Integer::sum);
        }

        TreeSet<String> scriptInstanceIds = new TreeSet<>();
        scriptInstanceIds.addAll(drawingsByScriptInstance.keySet());
        scriptInstanceIds.addAll(plotsByScriptInstance.keySet());
        scriptInstanceIds.addAll(alertCountsByScript.keySet());
        scriptInstanceIds.addAll(intentCountsByScript.keySet());

        List<ScriptRegistryState> perInstance = new ArrayList<>();
        for (String scriptInstanceId : scriptInstanceIds) {
            Map<String, Object> drawingMap = drawingsByScriptInstance.getOrDefault(scriptInstanceId, Map.of());
            Map<String, List<Object>> plotMap = plotsByScriptInstance.getOrDefault(scriptInstanceId, Map.of());

            List<String> drawingIds = new ArrayList<>(drawingMap.keySet());
            drawingIds.sort(String::compareTo);

            List<PlotSeriesState> plotSeries = new ArrayList<>();
            int totalPlotPointCount = 0;
            for (Map.Entry<String, List<Object>> entry : plotMap.entrySet()) {
                int pointCount = entry.getValue() == null ? 0 : entry.getValue().size();
                plotSeries.add(new PlotSeriesState(entry.getKey(), pointCount));
                totalPlotPointCount += pointCount;
            }
            plotSeries.sort(Comparator.comparing(PlotSeriesState::plotId));

            perInstance.add(new ScriptRegistryState(
                    scriptInstanceId,
                    drawingMap.size(),
                    List.copyOf(drawingIds),
                    plotMap.size(),
                    totalPlotPointCount,
                    List.copyOf(plotSeries),
                    alertCountsByScript.getOrDefault(scriptInstanceId, 0),
                    intentCountsByScript.getOrDefault(scriptInstanceId, 0)));
        }

        return new RegistryDebugState(
                totals.lastSeq(),
                totals.drawingCount(),
                totals.drawingOwnerCount(),
                totals.plotSeriesCount(),
                totals.plotOwnerCount(),
                totals.totalPlotPointCount(),
                totals.alertCount(),
                totals.intentCount(),
                List.copyOf(perInstance));
    }

    public synchronized void clear() {
        drawingsByScriptInstance.clear();
        plotsByScriptInstance.clear();
        alerts.clear();
        intents.clear();
        eventLog.clear();
    }

    public synchronized void removeScriptArtifacts(String scriptInstanceId) {
        if (scriptInstanceId == null || scriptInstanceId.isBlank()) {
            return;
        }
        drawingsByScriptInstance.remove(scriptInstanceId);
        plotsByScriptInstance.remove(scriptInstanceId);
        alerts.removeIf(entry -> scriptInstanceId.equals(entry.scriptInstanceId()));
        intents.removeIf(intent -> scriptInstanceId.equals(intent.scriptInstanceId()));
    }

    private synchronized Map<String, Object> deepCopyDrawings() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> scriptEntry : drawingsByScriptInstance.entrySet()) {
            String scriptInstanceId = scriptEntry.getKey();
            for (Map.Entry<String, Object> drawingEntry : scriptEntry.getValue().entrySet()) {
                out.put(scriptInstanceId + "::" + drawingEntry.getKey(), drawingEntry.getValue());
            }
        }
        return out;
    }

    private synchronized Map<String, List<Object>> deepCopyPlots() {
        Map<String, List<Object>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, List<Object>>> scriptEntry : plotsByScriptInstance.entrySet()) {
            String scriptInstanceId = scriptEntry.getKey();
            for (Map.Entry<String, List<Object>> plotEntry : scriptEntry.getValue().entrySet()) {
                out.put(scriptInstanceId + "::" + plotEntry.getKey(), List.copyOf(plotEntry.getValue()));
            }
        }
        return out;
    }

    private synchronized List<Object> deepCopyAlerts() {
        List<Object> out = new ArrayList<>(alerts.size());
        for (AlertEntry entry : alerts) {
            out.add(entry.payload());
        }
        return List.copyOf(out);
    }

    private synchronized Map<String, Object> drawingSnapshotForScript(String scriptInstanceId) {
        Map<String, Object> drawingMap = drawingsByScriptInstance.get(scriptInstanceId);
        if (drawingMap == null || drawingMap.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(drawingMap);
    }

    private synchronized Map<String, List<Object>> plotSnapshotForScript(String scriptInstanceId) {
        Map<String, List<Object>> plotMap = plotsByScriptInstance.get(scriptInstanceId);
        if (plotMap == null || plotMap.isEmpty()) {
            return Map.of();
        }
        Map<String, List<Object>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<Object>> entry : plotMap.entrySet()) {
            out.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(out);
    }

    public final class Writer {
        private final String scriptInstanceId;

        private Writer(String scriptInstanceId) {
            this.scriptInstanceId = scriptInstanceId;
        }

        public void upsertDrawing(String drawingId, Object payload) {
            synchronized (RegistryHub.this) {
                drawingsByScriptInstance
                        .computeIfAbsent(scriptInstanceId, ignored -> new LinkedHashMap<>())
                        .put(drawingId, payload);
                eventLog.append("DRAWING_UPSERT", Map.of(
                        "scriptInstanceId", scriptInstanceId,
                        "drawingId", drawingId,
                        "payload", payload));
            }
        }

        public void removeDrawing(String drawingId) {
            synchronized (RegistryHub.this) {
                Map<String, Object> drawingMap = drawingsByScriptInstance.get(scriptInstanceId);
                if (drawingMap == null || drawingMap.isEmpty()) {
                    return;
                }
                Object removed = drawingMap.remove(drawingId);
                if (drawingMap.isEmpty()) {
                    drawingsByScriptInstance.remove(scriptInstanceId);
                }
                if (removed == null) {
                    return;
                }
                eventLog.append("DRAWING_REMOVE", Map.of(
                        "scriptInstanceId", scriptInstanceId,
                        "drawingId", drawingId));
            }
        }

        public void clearDrawings() {
            synchronized (RegistryHub.this) {
                Map<String, Object> drawingMap = drawingsByScriptInstance.get(scriptInstanceId);
                if (drawingMap == null || drawingMap.isEmpty()) {
                    return;
                }
                drawingsByScriptInstance.remove(scriptInstanceId);
                eventLog.append("DRAWING_CLEAR", Map.of(
                        "scriptInstanceId", scriptInstanceId));
            }
        }

        public void appendPlotPoint(String plotId, Object point) {
            synchronized (RegistryHub.this) {
                plotsByScriptInstance
                        .computeIfAbsent(scriptInstanceId, ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(plotId, key -> new ArrayList<>())
                        .add(point);
                eventLog.append("PLOT_POINT_ADD", Map.of(
                        "scriptInstanceId", scriptInstanceId,
                        "plotId", plotId,
                        "payload", point));
            }
        }

        public void addAlert(Object alert) {
            synchronized (RegistryHub.this) {
                alerts.add(new AlertEntry(scriptInstanceId, alert));
                eventLog.append("ALERT_ADD", Map.of(
                        "scriptInstanceId", scriptInstanceId,
                        "payload", alert));
            }
        }

        public void emitIntent(TradeIntent intent) {
            synchronized (RegistryHub.this) {
                intents.add(intent);
                eventLog.append("INTENT_ADD", intent);
            }
            log.info("trade_intent_created scriptInstanceId={} instrument={} side={} price={} reason={}",
                    intent.scriptInstanceId(), intent.instrumentToken(), intent.side(), intent.price(), intent.reason());
        }
    }
}
