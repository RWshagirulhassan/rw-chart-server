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

class SampleBollingerBandsIndicatorScriptDefinitionTest {

    @Test
    void emitsThreePlotSeriesFromTa4jBollingerIndicators() {
        SampleBollingerBandsIndicatorScriptDefinition definition = new SampleBollingerBandsIndicatorScriptDefinition();
        BarSeriesContext seriesContext = testSeriesContext(List.of(100.0, 102.0, 101.0, 103.0, 104.0));
        Object typedParams = definition.paramsBinder().bind(new ScriptParams(Map.of(
                "length", 3,
                "mult", 2.0,
                "offset", 0)));

        var runtime = definition.createRuntime(
                new ScriptInitContext(seriesContext, new ScriptInstanceId("bb-1")),
                typedParams);
        RegistryHub hub = new RegistryHub();
        ScriptExecutionContext execCtx = new ScriptExecutionContext(seriesContext, hub.writer("bb-1"));

        for (int i = 0; i < 5; i++) {
            runtime.onBar(execCtx, i);
        }

        @SuppressWarnings("unchecked")
        Map<String, List<Object>> plots = (Map<String, List<Object>>) hub.snapshot(RegistryType.PLOT);
        assertTrue(plots.containsKey("bb-1::basis"));
        assertTrue(plots.containsKey("bb-1::upper"));
        assertTrue(plots.containsKey("bb-1::lower"));
        assertEquals(5, plots.get("bb-1::basis").size());
        assertEquals(5, plots.get("bb-1::upper").size());
        assertEquals(5, plots.get("bb-1::lower").size());
    }

    @Test
    void appliesPositiveOffsetToPlottedIndex() {
        SampleBollingerBandsIndicatorScriptDefinition definition = new SampleBollingerBandsIndicatorScriptDefinition();
        BarSeriesContext seriesContext = testSeriesContext(List.of(100.0, 101.0, 102.0));
        Object typedParams = definition.paramsBinder().bind(new ScriptParams(Map.of(
                "length", 2,
                "mult", 2.0,
                "offset", 2)));

        var runtime = definition.createRuntime(
                new ScriptInitContext(seriesContext, new ScriptInstanceId("bb-2")),
                typedParams);
        RegistryHub hub = new RegistryHub();
        ScriptExecutionContext execCtx = new ScriptExecutionContext(seriesContext, hub.writer("bb-2"));

        runtime.onBar(execCtx, 0);
        runtime.onBar(execCtx, 1);
        runtime.onBar(execCtx, 2);

        @SuppressWarnings("unchecked")
        Map<String, List<Object>> plots = (Map<String, List<Object>>) hub.snapshot(RegistryType.PLOT);
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) plots.get("bb-2::basis").get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> second = (Map<String, Object>) plots.get("bb-2::basis").get(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> third = (Map<String, Object>) plots.get("bb-2::basis").get(2);
        assertEquals(2, first.get("index"));
        assertEquals(3, second.get("index"));
        assertEquals(4, third.get("index"));
    }

    private static BarSeriesContext testSeriesContext(List<Double> closes) {
        var series = new ConcurrentBarSeriesBuilder()
                .withName("test-series")
                .withNumFactory(DecimalNumFactory.getInstance())
                .build();

        Instant start = Instant.parse("2026-01-01T09:15:00Z");
        for (int i = 0; i < closes.size(); i++) {
            double close = closes.get(i);
            Instant begin = start.plus(Duration.ofMinutes(i));
            Instant end = begin.plus(Duration.ofMinutes(1));
            series.addBar(new BaseBar(
                    Duration.ofMinutes(1),
                    begin,
                    end,
                    series.numFactory().numOf(close),
                    series.numFactory().numOf(close),
                    series.numFactory().numOf(close),
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
