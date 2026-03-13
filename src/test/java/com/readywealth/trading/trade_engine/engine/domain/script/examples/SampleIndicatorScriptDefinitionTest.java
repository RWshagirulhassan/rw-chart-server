package com.readywealth.trading.trade_engine.engine.domain.script.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

class SampleIndicatorScriptDefinitionTest {

    @Test
    void usesClosePriceIndicatorAndRespectsEveryFilter() {
        SampleIndicatorScriptDefinition definition = new SampleIndicatorScriptDefinition();
        BarSeriesContext seriesContext = testSeriesContext(List.of(100.0, 120.0, 140.0), Instant.now().minus(Duration.ofMinutes(30)));
        Object typedParams = definition.paramsBinder().bind(new ScriptParams(Map.of("every", 2)));

        var runtime = definition.createRuntime(
                new ScriptInitContext(seriesContext, new ScriptInstanceId("script-1")),
                typedParams);

        RegistryHub hub = new RegistryHub();
        ScriptExecutionContext execCtx = new ScriptExecutionContext(seriesContext, hub.writer("script-1"));

        runtime.onBar(execCtx, 0);
        runtime.onBar(execCtx, 1);
        runtime.onBar(execCtx, 2);

        @SuppressWarnings("unchecked")
        Map<String, List<Object>> plots = (Map<String, List<Object>>) hub.snapshot(RegistryType.PLOT);
        String plotKey = "script-1::close";
        assertTrue(plots.containsKey(plotKey));
        List<Object> closePoints = plots.get(plotKey);
        assertEquals(2, closePoints.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) closePoints.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> second = (Map<String, Object>) closePoints.get(1);

        assertEquals(0, first.get("index"));
        assertEquals(100.0, (Double) first.get("close"), 1e-9);
        assertEquals(2, second.get("index"));
        assertEquals(140.0, (Double) second.get("close"), 1e-9);
    }

    @Test
    void emitsAlertForCrossingUp() {
        SampleIndicatorScriptDefinition definition = new SampleIndicatorScriptDefinition();
        BarSeriesContext seriesContext = testSeriesContext(List.of(95.0, 105.0), Instant.now().plus(Duration.ofMinutes(2)));
        Object typedParams = definition.paramsBinder().bind(new ScriptParams(Map.of(
                "every", 1,
                "alertEnabled", true,
                "targetClosePrice", 100.0,
                "condition", "crossing_up",
                "triggerMode", "every_time",
                "expirySeconds", 3600)));

        var runtime = definition.createRuntime(
                new ScriptInitContext(seriesContext, new ScriptInstanceId("script-2")),
                typedParams);

        RegistryHub hub = new RegistryHub();
        ScriptExecutionContext execCtx = new ScriptExecutionContext(seriesContext, hub.writer("script-2"));

        runtime.onBar(execCtx, 0);
        runtime.onBar(execCtx, 1);

        @SuppressWarnings("unchecked")
        List<Object> alerts = (List<Object>) hub.snapshot(RegistryType.ALERT);
        assertFalse(alerts.isEmpty());

        @SuppressWarnings("unchecked")
        Map<String, Object> alert = (Map<String, Object>) alerts.get(0);
        assertEquals("close_price_alert", alert.get("messageType"));
        assertEquals("crossing_up", alert.get("condition"));
        assertEquals("every_time", alert.get("triggerMode"));
        assertEquals(100.0, (Double) alert.get("targetClosePrice"), 1e-9);
        assertEquals(105.0, (Double) alert.get("closePrice"), 1e-9);
        assertEquals(1, alert.get("barIndex"));
        assertNotNull(alert.get("barTime"));
        assertEquals(false, alert.get("expired"));
        assertTrue(alert.get("text").toString().contains("crossed up"));
    }

    @Test
    void emitsAlertForCrossingDown() {
        SampleIndicatorScriptDefinition definition = new SampleIndicatorScriptDefinition();
        BarSeriesContext seriesContext = testSeriesContext(List.of(105.0, 95.0), Instant.now().plus(Duration.ofMinutes(2)));
        Object typedParams = definition.paramsBinder().bind(new ScriptParams(Map.of(
                "alertEnabled", true,
                "targetClosePrice", 100.0,
                "condition", "crossing_down",
                "triggerMode", "every_time",
                "expirySeconds", 3600)));

        var runtime = definition.createRuntime(
                new ScriptInitContext(seriesContext, new ScriptInstanceId("script-3")),
                typedParams);

        RegistryHub hub = new RegistryHub();
        ScriptExecutionContext execCtx = new ScriptExecutionContext(seriesContext, hub.writer("script-3"));
        runtime.onBar(execCtx, 0);
        runtime.onBar(execCtx, 1);

        @SuppressWarnings("unchecked")
        List<Object> alerts = (List<Object>) hub.snapshot(RegistryType.ALERT);
        assertEquals(1, alerts.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> alert = (Map<String, Object>) alerts.get(0);
        assertEquals("crossing_down", alert.get("condition"));
        assertTrue(alert.get("text").toString().contains("crossed down"));
    }

    @Test
    void emitsAlertForBidirectionalCrossing() {
        SampleIndicatorScriptDefinition definition = new SampleIndicatorScriptDefinition();
        BarSeriesContext seriesContext = testSeriesContext(List.of(95.0, 105.0), Instant.now().plus(Duration.ofMinutes(2)));
        Object typedParams = definition.paramsBinder().bind(new ScriptParams(Map.of(
                "alertEnabled", true,
                "targetClosePrice", 100.0,
                "condition", "crossing",
                "triggerMode", "every_time",
                "expirySeconds", 3600)));

        var runtime = definition.createRuntime(
                new ScriptInitContext(seriesContext, new ScriptInstanceId("script-4")),
                typedParams);

        RegistryHub hub = new RegistryHub();
        ScriptExecutionContext execCtx = new ScriptExecutionContext(seriesContext, hub.writer("script-4"));
        runtime.onBar(execCtx, 0);
        runtime.onBar(execCtx, 1);

        @SuppressWarnings("unchecked")
        List<Object> alerts = (List<Object>) hub.snapshot(RegistryType.ALERT);
        assertEquals(1, alerts.size());
    }

    @Test
    void emitsOnlyOnceWhenConfigured() {
        SampleIndicatorScriptDefinition definition = new SampleIndicatorScriptDefinition();
        BarSeriesContext seriesContext = testSeriesContext(List.of(95.0, 105.0, 95.0, 105.0), Instant.now().plus(Duration.ofMinutes(2)));
        Object typedParams = definition.paramsBinder().bind(new ScriptParams(Map.of(
                "alertEnabled", true,
                "targetClosePrice", 100.0,
                "condition", "crossing",
                "triggerMode", "only_once",
                "expirySeconds", 3600)));

        var runtime = definition.createRuntime(
                new ScriptInitContext(seriesContext, new ScriptInstanceId("script-5")),
                typedParams);

        RegistryHub hub = new RegistryHub();
        ScriptExecutionContext execCtx = new ScriptExecutionContext(seriesContext, hub.writer("script-5"));
        runtime.onBar(execCtx, 0);
        runtime.onBar(execCtx, 1);
        runtime.onBar(execCtx, 2);
        runtime.onBar(execCtx, 3);

        @SuppressWarnings("unchecked")
        List<Object> alerts = (List<Object>) hub.snapshot(RegistryType.ALERT);
        assertEquals(1, alerts.size());
    }

    @Test
    void emitsEveryTimeWhenConfigured() {
        SampleIndicatorScriptDefinition definition = new SampleIndicatorScriptDefinition();
        BarSeriesContext seriesContext = testSeriesContext(List.of(95.0, 105.0, 95.0, 105.0), Instant.now().plus(Duration.ofMinutes(2)));
        Object typedParams = definition.paramsBinder().bind(new ScriptParams(Map.of(
                "alertEnabled", true,
                "targetClosePrice", 100.0,
                "condition", "crossing",
                "triggerMode", "every_time",
                "expirySeconds", 3600)));

        var runtime = definition.createRuntime(
                new ScriptInitContext(seriesContext, new ScriptInstanceId("script-6")),
                typedParams);

        RegistryHub hub = new RegistryHub();
        ScriptExecutionContext execCtx = new ScriptExecutionContext(seriesContext, hub.writer("script-6"));
        runtime.onBar(execCtx, 0);
        runtime.onBar(execCtx, 1);
        runtime.onBar(execCtx, 2);
        runtime.onBar(execCtx, 3);

        @SuppressWarnings("unchecked")
        List<Object> alerts = (List<Object>) hub.snapshot(RegistryType.ALERT);
        assertEquals(3, alerts.size());
    }

    @Test
    void doesNotEmitWhenExpired() {
        SampleIndicatorScriptDefinition definition = new SampleIndicatorScriptDefinition();
        BarSeriesContext seriesContext = testSeriesContext(List.of(95.0, 105.0), Instant.now().plus(Duration.ofHours(2)));
        Object typedParams = definition.paramsBinder().bind(new ScriptParams(Map.of(
                "alertEnabled", true,
                "targetClosePrice", 100.0,
                "condition", "crossing",
                "triggerMode", "every_time",
                "expirySeconds", 1)));

        var runtime = definition.createRuntime(
                new ScriptInitContext(seriesContext, new ScriptInstanceId("script-7")),
                typedParams);

        RegistryHub hub = new RegistryHub();
        ScriptExecutionContext execCtx = new ScriptExecutionContext(seriesContext, hub.writer("script-7"));
        runtime.onBar(execCtx, 0);
        runtime.onBar(execCtx, 1);

        @SuppressWarnings("unchecked")
        List<Object> alerts = (List<Object>) hub.snapshot(RegistryType.ALERT);
        assertTrue(alerts.isEmpty());
    }

    @Test
    void doesNotEmitOnTouchWithoutCross() {
        SampleIndicatorScriptDefinition definition = new SampleIndicatorScriptDefinition();
        BarSeriesContext seriesContext = testSeriesContext(List.of(99.0, 100.0, 100.0), Instant.now().plus(Duration.ofMinutes(2)));
        Object typedParams = definition.paramsBinder().bind(new ScriptParams(Map.of(
                "alertEnabled", true,
                "targetClosePrice", 100.0,
                "condition", "crossing",
                "triggerMode", "every_time",
                "expirySeconds", 3600)));

        var runtime = definition.createRuntime(
                new ScriptInitContext(seriesContext, new ScriptInstanceId("script-8")),
                typedParams);

        RegistryHub hub = new RegistryHub();
        ScriptExecutionContext execCtx = new ScriptExecutionContext(seriesContext, hub.writer("script-8"));
        runtime.onBar(execCtx, 0);
        runtime.onBar(execCtx, 1);
        runtime.onBar(execCtx, 2);

        @SuppressWarnings("unchecked")
        List<Object> alerts = (List<Object>) hub.snapshot(RegistryType.ALERT);
        assertTrue(alerts.isEmpty());
    }

    private static BarSeriesContext testSeriesContext(List<Double> closes, Instant start) {
        var series = new ConcurrentBarSeriesBuilder()
                .withName("test-series")
                .withNumFactory(DecimalNumFactory.getInstance())
                .build();

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
