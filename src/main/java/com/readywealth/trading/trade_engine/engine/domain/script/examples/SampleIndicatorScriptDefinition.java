package com.readywealth.trading.trade_engine.engine.domain.script.examples;

import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import com.readywealth.trading.trade_engine.engine.domain.script.IndicatorScriptDefinition;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptExecutionContext;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptId;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptInitContext;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptParamMetadata;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptParams;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptParamsBinder;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptRuntime;

@Component
public class SampleIndicatorScriptDefinition implements IndicatorScriptDefinition {

    private static final Logger log = LoggerFactory.getLogger(SampleIndicatorScriptDefinition.class);
    private static final ScriptId ID = new ScriptId("sample.indicator.close-plot");

    @Override
    public ScriptId id() {
        return ID;
    }

    @Override
    public ScriptParamsBinder<Params> paramsBinder() {
        return raw -> new Params(
                Math.max(1, raw.getInt("every", 1)),
                raw.getBoolean("alertEnabled", false),
                raw.getDouble("targetClosePrice", Double.NaN),
                AlertCondition.fromWireValue(readString(raw, "condition", "crossing")),
                TriggerMode.fromWireValue(readString(raw, "triggerMode", "every_time")),
                raw.getInt("expirySeconds", 3600));
    }

    @Override
    public String description() {
        return "Plots close price points and emits configurable close crossing alerts.";
    }

    @Override
    public java.util.List<ScriptParamMetadata> paramsMetadata() {
        return java.util.List.of(
                new ScriptParamMetadata(
                        "every",
                        "integer",
                        false,
                        1,
                        "Emit one close-point for every Nth bar."),
                new ScriptParamMetadata(
                        "alertEnabled",
                        "boolean",
                        false,
                        false,
                        "Enable close crossing alerts for this script instance."),
                new ScriptParamMetadata(
                        "targetClosePrice",
                        "number",
                        false,
                        null,
                        "Alert threshold used for crossing checks."),
                new ScriptParamMetadata(
                        "condition",
                        "string",
                        false,
                        "crossing",
                        "Crossing behavior: crossing, crossing_up, crossing_down.",
                        List.of("crossing", "crossing_up", "crossing_down")),
                new ScriptParamMetadata(
                        "triggerMode",
                        "string",
                        false,
                        "every_time",
                        "Emit every matching cross or only the first one.",
                        List.of("only_once", "every_time")),
                new ScriptParamMetadata(
                        "expirySeconds",
                        "integer",
                        false,
                        3600,
                        "Alert expiry from script attach time in seconds."));
    }

    @Override
    public ScriptRuntime createRuntime(ScriptInitContext init, Object typedParams) {
        Params params = (Params) typedParams;
        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(init.seriesContext().series());
        final long attachedAtEpochMs = System.currentTimeMillis();
        final long expiryAtEpochMs = computeExpiryEpochMs(attachedAtEpochMs, params.expirySeconds());
        final Num targetCloseNum = Double.isFinite(params.targetClosePrice())
                ? init.seriesContext().numFactory().numOf(params.targetClosePrice())
                : null;
        return new ScriptRuntime() {
            private boolean firedOnce = false;

            @Override
            public void onBar(ScriptExecutionContext ctx, int index) {
                if (index % Math.max(1, params.every()) != 0) {
                    return;
                }
                double close = closePriceIndicator.getValue(index).doubleValue();
                ctx.registries().appendPlotPoint(
                        "close",
                        Map.of("index", index, "close", close));

                if (!params.alertEnabled()) {
                    return;
                }
                if (targetCloseNum == null) {
                    return;
                }
                if (params.expirySeconds() <= 0) {
                    return;
                }
                if (params.triggerMode() == TriggerMode.ONLY_ONCE && firedOnce) {
                    return;
                }
                if (index <= ctx.seriesContext().series().getBeginIndex()) {
                    return;
                }

                long barEpochMs = ctx.seriesContext().series().getBar(index).getEndTime().toEpochMilli();
                if (barEpochMs <= attachedAtEpochMs) {
                    return;
                }
                if (barEpochMs > expiryAtEpochMs) {
                    return;
                }

                Num prevClose = closePriceIndicator.getValue(index - 1);
                Num currClose = closePriceIndicator.getValue(index);
                boolean crossedUp = prevClose.isLessThanOrEqual(targetCloseNum) && currClose.isGreaterThan(targetCloseNum);
                boolean crossedDown = prevClose.isGreaterThanOrEqual(targetCloseNum) && currClose.isLessThan(targetCloseNum);
                boolean shouldTrigger = switch (params.condition()) {
                    case CROSSING -> crossedUp || crossedDown;
                    case CROSSING_UP -> crossedUp;
                    case CROSSING_DOWN -> crossedDown;
                };
                if (!shouldTrigger) {
                    return;
                }

                LinkedHashMap<String, Object> alert = new LinkedHashMap<>();
                alert.put("messageType", "close_price_alert");
                alert.put("scriptId", ID.value());
                alert.put("scriptInstanceId", init.scriptInstanceId().value());
                alert.put("instrumentToken", ctx.seriesContext().key().instrumentToken());
                alert.put("condition", params.condition().wireValue());
                alert.put("triggerMode", params.triggerMode().wireValue());
                alert.put("targetClosePrice", params.targetClosePrice());
                alert.put("closePrice", currClose.doubleValue());
                alert.put("barIndex", index);
                alert.put("barTime", ctx.seriesContext().series().getBar(index).getEndTime().atOffset(ZoneOffset.UTC).toString());
                alert.put("expired", false);
                alert.put("text", buildAlertText(params.condition(), params.targetClosePrice(), currClose.doubleValue(),
                        ctx.seriesContext().key().timeframe().name()));
                ctx.registries().addAlert(alert);
                log.info(
                        "close_alert_triggered scriptInstanceId={} instrumentToken={} barIndex={} condition={} triggerMode={} targetClosePrice={} closePrice={} expirySeconds={}",
                        init.scriptInstanceId().value(),
                        ctx.seriesContext().key().instrumentToken(),
                        index,
                        params.condition().wireValue(),
                        params.triggerMode().wireValue(),
                        params.targetClosePrice(),
                        currClose.doubleValue(),
                        params.expirySeconds());
                if (params.triggerMode() == TriggerMode.ONLY_ONCE) {
                    firedOnce = true;
                }
            }
        };
    }

    private static long computeExpiryEpochMs(long attachedAtEpochMs, int expirySeconds) {
        if (expirySeconds <= 0) {
            return attachedAtEpochMs;
        }
        try {
            return Math.addExact(attachedAtEpochMs, Math.multiplyExact((long) expirySeconds, 1000L));
        } catch (ArithmeticException ex) {
            return Long.MAX_VALUE;
        }
    }

    private static String buildAlertText(
            AlertCondition condition,
            double targetClosePrice,
            double currentClosePrice,
            String timeframe) {
        return String.format(
                Locale.ROOT,
                "Close Alert Triggered: CLOSE %s %.2f (current %.2f) on %s",
                condition.humanText(),
                targetClosePrice,
                currentClosePrice,
                timeframe);
    }

    private static String readString(ScriptParams raw, String key, String defaultValue) {
        Object value = raw.values().get(key);
        if (value == null) {
            return defaultValue;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? defaultValue : text;
    }

    private enum AlertCondition {
        CROSSING("crossing", "crossed"),
        CROSSING_UP("crossing_up", "crossed up"),
        CROSSING_DOWN("crossing_down", "crossed down");

        private final String wireValue;
        private final String humanText;

        AlertCondition(String wireValue, String humanText) {
            this.wireValue = wireValue;
            this.humanText = humanText;
        }

        private String wireValue() {
            return wireValue;
        }

        private String humanText() {
            return humanText;
        }

        private static AlertCondition fromWireValue(String value) {
            String normalized = normalizeToken(value, "crossing");
            return switch (normalized) {
                case "crossing_up" -> CROSSING_UP;
                case "crossing_down" -> CROSSING_DOWN;
                default -> CROSSING;
            };
        }
    }

    private enum TriggerMode {
        ONLY_ONCE("only_once"),
        EVERY_TIME("every_time");

        private final String wireValue;

        TriggerMode(String wireValue) {
            this.wireValue = wireValue;
        }

        private String wireValue() {
            return wireValue;
        }

        private static TriggerMode fromWireValue(String value) {
            String normalized = normalizeToken(value, "every_time");
            if ("only_once".equals(normalized)) {
                return ONLY_ONCE;
            }
            return EVERY_TIME;
        }
    }

    private static String normalizeToken(String value, String fallback) {
        String normalized = Objects.requireNonNullElse(value, fallback)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        return normalized.isEmpty() ? fallback : normalized;
    }

    private record Params(
            int every,
            boolean alertEnabled,
            double targetClosePrice,
            AlertCondition condition,
            TriggerMode triggerMode,
            int expirySeconds) {
    }
}
