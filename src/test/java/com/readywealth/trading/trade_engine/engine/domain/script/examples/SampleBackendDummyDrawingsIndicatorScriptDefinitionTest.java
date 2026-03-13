package com.readywealth.trading.trade_engine.engine.domain.script.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class SampleBackendDummyDrawingsIndicatorScriptDefinitionTest {

    @Test
    void emitsExpectedPrimitiveDrawingSet() {
        SampleBackendDummyDrawingsIndicatorScriptDefinition definition = new SampleBackendDummyDrawingsIndicatorScriptDefinition();
        BarSeriesContext seriesContext = testSeriesContext(80);
        Object typedParams = definition.paramsBinder().bind(new ScriptParams(Map.of("minBars", 50)));

        var runtime = definition.createRuntime(
                new ScriptInitContext(seriesContext, new ScriptInstanceId("script-1")),
                typedParams);

        RegistryHub hub = new RegistryHub();
        ScriptExecutionContext execCtx = new ScriptExecutionContext(seriesContext, hub.writer("script-1"));
        int lastIndex = seriesContext.series().getEndIndex();
        runtime.onBar(execCtx, lastIndex);

        @SuppressWarnings("unchecked")
        Map<String, Object> drawings = (Map<String, Object>) hub.snapshotForScript(RegistryType.DRAWING, "script-1");
        assertEquals(4, drawings.size());
        assertTrue(drawings.containsKey("rect"));
        assertTrue(drawings.containsKey("market"));
        assertTrue(drawings.containsKey("target"));
        assertTrue(drawings.containsKey("text"));

        @SuppressWarnings("unchecked")
        Map<String, Object> rect = (Map<String, Object>) drawings.get("rect");
        assertEquals("rect", rect.get("kind"));
        @SuppressWarnings("unchecked")
        Map<String, Object> rectP1 = (Map<String, Object>) rect.get("p1");
        assertTrue(rectP1.containsKey("index"));
        assertTrue(rectP1.containsKey("price"));

        @SuppressWarnings("unchecked")
        Map<String, Object> target = (Map<String, Object>) drawings.get("target");
        assertEquals("line", target.get("kind"));
        @SuppressWarnings("unchecked")
        Map<String, Object> targetStroke = (Map<String, Object>) target.get("stroke");
        assertTrue(targetStroke.containsKey("dash"));
    }

    @Test
    void clearsDrawingsOnDetach() {
        SampleBackendDummyDrawingsIndicatorScriptDefinition definition = new SampleBackendDummyDrawingsIndicatorScriptDefinition();
        BarSeriesContext seriesContext = testSeriesContext(80);
        Object typedParams = definition.paramsBinder().bind(new ScriptParams(Map.of("minBars", 50)));

        var runtime = definition.createRuntime(
                new ScriptInitContext(seriesContext, new ScriptInstanceId("script-2")),
                typedParams);

        RegistryHub hub = new RegistryHub();
        ScriptExecutionContext execCtx = new ScriptExecutionContext(seriesContext, hub.writer("script-2"));
        int lastIndex = seriesContext.series().getEndIndex();
        runtime.onBar(execCtx, lastIndex);
        runtime.onDetach(execCtx);

        @SuppressWarnings("unchecked")
        Map<String, Object> drawings = (Map<String, Object>) hub.snapshotForScript(RegistryType.DRAWING, "script-2");
        assertTrue(drawings.isEmpty());
    }

    private static BarSeriesContext testSeriesContext(int barCount) {
        var series = new ConcurrentBarSeriesBuilder()
                .withName("test-series")
                .withNumFactory(DecimalNumFactory.getInstance())
                .build();

        Instant start = Instant.parse("2026-01-01T09:15:00Z");
        for (int i = 0; i < barCount; i++) {
            double close = 100.0 + i;
            double high = close + 0.8;
            double low = close - 0.8;
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
