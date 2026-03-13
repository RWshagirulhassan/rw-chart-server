package com.readywealth.trading.trade_engine.engine.application.runtime.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RegistryHubTest {

    @Test
    void snapshotForScriptFiltersPlotsAndDrawings() {
        RegistryHub hub = new RegistryHub();

        RegistryHub.Writer s1 = hub.writer("s1");
        RegistryHub.Writer s2 = hub.writer("s2");

        s1.appendPlotPoint("close-s1", Map.of("index", 1, "close", 100.0));
        s2.appendPlotPoint("close-s2", Map.of("index", 2, "close", 200.0));

        s1.upsertDrawing("d-s1", Map.of("value", "a"));
        s2.upsertDrawing("d-s2", Map.of("value", "b"));

        @SuppressWarnings("unchecked")
        Map<String, List<Object>> s1Plots = (Map<String, List<Object>>) hub.snapshotForScript(RegistryType.PLOT, "s1");
        @SuppressWarnings("unchecked")
        Map<String, Object> s1Drawings = (Map<String, Object>) hub.snapshotForScript(RegistryType.DRAWING, "s1");

        assertTrue(s1Plots.containsKey("close-s1"));
        assertFalse(s1Plots.containsKey("close-s2"));
        assertTrue(s1Drawings.containsKey("d-s1"));
        assertFalse(s1Drawings.containsKey("d-s2"));
    }

    @Test
    void drawingIdsAreScopedPerScriptInstance() {
        RegistryHub hub = new RegistryHub();

        RegistryHub.Writer s1 = hub.writer("s1");
        RegistryHub.Writer s2 = hub.writer("s2");

        s1.upsertDrawing("shared", Map.of("owner", "s1"));
        s2.upsertDrawing("shared", Map.of("owner", "s2"));

        @SuppressWarnings("unchecked")
        Map<String, Object> s1Drawings = (Map<String, Object>) hub.snapshotForScript(RegistryType.DRAWING, "s1");
        @SuppressWarnings("unchecked")
        Map<String, Object> s2Drawings = (Map<String, Object>) hub.snapshotForScript(RegistryType.DRAWING, "s2");

        assertTrue(s1Drawings.containsKey("shared"));
        assertTrue(s2Drawings.containsKey("shared"));
        @SuppressWarnings("unchecked")
        Map<String, String> payload = (Map<String, String>) s2Drawings.get("shared");
        assertEquals("s2", payload.get("owner"));
    }

    @Test
    void removeDrawingEmitsDeltaAndRemovesFromSnapshot() {
        RegistryHub hub = new RegistryHub();
        RegistryHub.Writer s1 = hub.writer("s1");

        s1.upsertDrawing("d1", Map.of("value", 1));
        s1.removeDrawing("d1");

        @SuppressWarnings("unchecked")
        Map<String, Object> drawings = (Map<String, Object>) hub.snapshotForScript(RegistryType.DRAWING, "s1");
        assertTrue(drawings.isEmpty());

        var deltas = hub.deltasSince(0, 100).events();
        assertEquals(2, deltas.size());
        assertEquals("DRAWING_UPSERT", deltas.get(0).type());
        assertEquals("DRAWING_REMOVE", deltas.get(1).type());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) deltas.get(1).payload();
        assertEquals("s1", payload.get("scriptInstanceId"));
        assertEquals("d1", payload.get("drawingId"));
    }

    @Test
    void clearDrawingsEmitsDeltaAndRemovesScriptDrawings() {
        RegistryHub hub = new RegistryHub();
        RegistryHub.Writer s1 = hub.writer("s1");

        s1.upsertDrawing("d1", Map.of("value", 1));
        s1.upsertDrawing("d2", Map.of("value", 2));
        s1.clearDrawings();

        @SuppressWarnings("unchecked")
        Map<String, Object> drawings = (Map<String, Object>) hub.snapshotForScript(RegistryType.DRAWING, "s1");
        assertTrue(drawings.isEmpty());

        var deltas = hub.deltasSince(0, 100).events();
        assertEquals(3, deltas.size());
        assertEquals("DRAWING_CLEAR", deltas.get(2).type());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) deltas.get(2).payload();
        assertEquals("s1", payload.get("scriptInstanceId"));
    }

    @Test
    void clearResetsScriptIndexes() {
        RegistryHub hub = new RegistryHub();
        RegistryHub.Writer s1 = hub.writer("s1");

        s1.appendPlotPoint("p1", Map.of("index", 1));
        s1.upsertDrawing("d1", Map.of("v", 1));

        hub.clear();

        @SuppressWarnings("unchecked")
        Map<String, List<Object>> plots = (Map<String, List<Object>>) hub.snapshotForScript(RegistryType.PLOT, "s1");
        @SuppressWarnings("unchecked")
        Map<String, Object> drawings = (Map<String, Object>) hub.snapshotForScript(RegistryType.DRAWING, "s1");

        assertTrue(plots.isEmpty());
        assertTrue(drawings.isEmpty());
    }
}
