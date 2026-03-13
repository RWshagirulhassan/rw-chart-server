package com.readywealth.trading.trade_engine.engine.domain.script.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BaseBar;
import org.ta4j.core.ConcurrentBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;

import com.readywealth.trading.trade_engine.engine.domain.script.ScriptExecutionContext;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptInitContext;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptInstanceId;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptParams;
import com.readywealth.trading.trade_engine.engine.domain.series.BarSeriesContext;
import com.readywealth.trading.trade_engine.engine.domain.series.IntervalKind;
import com.readywealth.trading.trade_engine.engine.domain.series.SeriesConfig;
import com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey;
import com.readywealth.trading.trade_engine.engine.application.runtime.registry.RegistryHub;
import com.readywealth.trading.trade_engine.engine.application.runtime.registry.RegistryType;

class SamplePivotMarkersIndicatorScriptDefinitionTest {

    @Test
    void emitsPivotHighAndLowMarkers() {
        SamplePivotMarkersIndicatorScriptDefinition definition = new SamplePivotMarkersIndicatorScriptDefinition();
        BarSeriesContext seriesContext = testSeriesContext(List.of(1d, 2d, 3d, 2d, 1d, 2d, 4d, 2d, 1d));
        Object typedParams = definition.paramsBinder()
                .bind(new ScriptParams(Map.of("leftBars", 2, "rightBars", 2, "maxMarkers", 10)));

        var runtime = definition.createRuntime(
                new ScriptInitContext(seriesContext, new ScriptInstanceId("script-1")),
                typedParams);

        RegistryHub hub = new RegistryHub();
        ScriptExecutionContext execCtx = new ScriptExecutionContext(seriesContext, hub.writer("script-1"));
        int begin = seriesContext.series().getBeginIndex();
        int end = seriesContext.series().getEndIndex();
        for (int i = begin; i <= end; i++) {
            runtime.onBar(execCtx, i);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> drawings = (Map<String, Object>) hub.snapshotForScript(RegistryType.DRAWING, "script-1");
        assertTrue(drawings.containsKey("ph-2"));
        assertTrue(drawings.containsKey("pl-4"));
        assertTrue(drawings.containsKey("ph-6"));

        @SuppressWarnings("unchecked")
        Map<String, Object> pivotHigh = (Map<String, Object>) drawings.get("ph-2");
        assertEquals("marker", pivotHigh.get("kind"));
        assertEquals("triangleDown", pivotHigh.get("shape"));
        @SuppressWarnings("unchecked")
        Map<String, Object> point = (Map<String, Object>) pivotHigh.get("point");
        assertEquals(2, point.get("index"));
    }

    @Test
    void capsMarkersUsingMaxMarkers() {
        SamplePivotMarkersIndicatorScriptDefinition definition = new SamplePivotMarkersIndicatorScriptDefinition();
        BarSeriesContext seriesContext = testSeriesContext(List.of(1d, 2d, 3d, 2d, 1d, 2d, 4d, 2d, 1d));
        Object typedParams = definition.paramsBinder()
                .bind(new ScriptParams(Map.of("leftBars", 2, "rightBars", 2, "maxMarkers", 2)));

        var runtime = definition.createRuntime(
                new ScriptInitContext(seriesContext, new ScriptInstanceId("script-2")),
                typedParams);

        RegistryHub hub = new RegistryHub();
        ScriptExecutionContext execCtx = new ScriptExecutionContext(seriesContext, hub.writer("script-2"));
        int begin = seriesContext.series().getBeginIndex();
        int end = seriesContext.series().getEndIndex();
        for (int i = begin; i <= end; i++) {
            runtime.onBar(execCtx, i);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> drawings = (Map<String, Object>) hub.snapshotForScript(RegistryType.DRAWING, "script-2");
        assertEquals(2, drawings.size());
        assertFalse(drawings.containsKey("ph-2"));
        assertTrue(drawings.containsKey("pl-4"));
        assertTrue(drawings.containsKey("ph-6"));
    }

    private static BarSeriesContext testSeriesContext(List<Double> closes) {
        var series = new ConcurrentBarSeriesBuilder()
                .withName("test-series")
                .withNumFactory(DecimalNumFactory.getInstance())
                .build();

        Instant start = Instant.parse("2026-01-01T09:15:00Z");
        for (int i = 0; i < closes.size(); i++) {
            double close = closes.get(i);
            double high = close + 0.5;
            double low = close - 0.5;
            Instant begin = start.plus(Duration.ofMinutes(i));
            Instant end = begin.plus(Duration.ofMinutes(1));
            series.addBar(new BaseBar(
                    Duration.ofMinutes(1),
                    begin,
                    end,
                    series.numFactory().numOf(close),
                    series.numFactory().numOf(high),
                    series.numFactory().numOf(low),
                    series.numFactory().numOf(close),
                    series.numFactory().numOf(1.0),
                    series.numFactory().numOf(close),
                    1L));
        }

        return new BarSeriesContext(
                new SeriesKey(115626503L, IntervalKind.TIME_1M),
                series,
                series.numFactory(),
                new SeriesConfig(5000));
    }
}
