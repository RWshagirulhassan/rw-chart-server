package com.readywealth.trading.trade_engine.engine.domain.script.examples;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;

import com.readywealth.trading.trade_engine.engine.domain.script.IndicatorScriptDefinition;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptExecutionContext;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptId;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptInitContext;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptParamMetadata;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptParamsBinder;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptRuntime;
import com.readywealth.trading.trade_engine.engine.domain.script.examples.support.PrimitiveDrawingPayloads;

@Component
public class SampleBackendDummyDrawingsIndicatorScriptDefinition implements IndicatorScriptDefinition {

    private static final ScriptId ID = new ScriptId("sample.indicator.backend-dummy-drawings");

    @Override
    public ScriptId id() {
        return ID;
    }

    @Override
    public ScriptParamsBinder<Params> paramsBinder() {
        return raw -> new Params(Math.max(10, raw.getInt("minBars", 50)));
    }

    @Override
    public String description() {
        return "Backend-driven primitive drawings that mirror the demo demand/market/target/label overlays.";
    }

    @Override
    public List<ScriptParamMetadata> paramsMetadata() {
        return List.of(new ScriptParamMetadata(
                "minBars",
                "integer",
                false,
                50,
                "Minimum bars required before emitting demo primitive drawings."));
    }

    @Override
    public ScriptRuntime createRuntime(ScriptInitContext init, Object typedParams) {
        Params params = (Params) typedParams;
        return new ScriptRuntime() {
            @Override
            public void onBar(ScriptExecutionContext ctx, int index) {
                BarSeries series = ctx.seriesContext().series();
                int begin = series.getBeginIndex();
                int end = series.getEndIndex();
                if (begin < 0 || end < begin) {
                    return;
                }
                int currentIndex = Math.max(begin, Math.min(index, end));
                int availableBars = currentIndex - begin + 1;
                if (availableBars < params.minBars()) {
                    ctx.registries().clearDrawings();
                    return;
                }

                int fromIdx = Math.max(begin + 5, currentIndex - 44);
                int toIdx = Math.max(fromIdx + 6, currentIndex - 19);
                toIdx = Math.min(toIdx, currentIndex);
                if (toIdx <= fromIdx) {
                    return;
                }

                double minLow = Double.POSITIVE_INFINITY;
                double maxHigh = Double.NEGATIVE_INFINITY;
                for (int i = fromIdx; i <= toIdx; i++) {
                    var bar = series.getBar(i);
                    minLow = Math.min(minLow, bar.getLowPrice().doubleValue());
                    maxHigh = Math.max(maxHigh, bar.getHighPrice().doubleValue());
                }
                if (!Double.isFinite(minLow) || !Double.isFinite(maxHigh)) {
                    return;
                }

                double zoneLow = minLow * 0.998;
                double zoneHigh = maxHigh * 1.002;
                double lastPrice = series.getBar(currentIndex).getClosePrice().doubleValue();

                int marketStart = Math.max(begin, currentIndex - 29);
                int targetStart = Math.max(begin, currentIndex - 21);
                int textIndex = Math.max(begin, currentIndex - 15);

                ctx.registries().upsertDrawing(
                        "rect",
                        PrimitiveDrawingPayloads.rect(
                                PrimitiveDrawingPayloads.point(fromIdx, zoneLow),
                                PrimitiveDrawingPayloads.point(toIdx, zoneHigh),
                                PrimitiveDrawingPayloads.fill("rgba(33, 150, 243, 0.12)"),
                                PrimitiveDrawingPayloads.stroke("rgba(33, 150, 243, 0.9)", 2),
                                PrimitiveDrawingPayloads.label(
                                        "Demand Zone (Demo)",
                                        Map.of(
                                                "rectPos", "topLeft",
                                                "bg", "rgba(33, 150, 243, 0.95)",
                                                "fg", "#fff",
                                                "radius", 4))));

                ctx.registries().upsertDrawing(
                        "market",
                        PrimitiveDrawingPayloads.line(
                                PrimitiveDrawingPayloads.point(marketStart, lastPrice),
                                PrimitiveDrawingPayloads.point(currentIndex, lastPrice),
                                PrimitiveDrawingPayloads.stroke("rgba(15, 175, 160, 0.95)", 2, List.of(4, 4)),
                                PrimitiveDrawingPayloads.label(
                                        "Market Line",
                                        Map.of(
                                                "linePos", "end",
                                                "bg", "rgba(15, 175, 160, 0.95)",
                                                "fg", "#ffffff",
                                                "radius", 4,
                                                "size", "xs"))));

                ctx.registries().upsertDrawing(
                        "target",
                        PrimitiveDrawingPayloads.line(
                                PrimitiveDrawingPayloads.point(targetStart, lastPrice * 1.01),
                                PrimitiveDrawingPayloads.point(currentIndex, lastPrice * 1.01),
                                PrimitiveDrawingPayloads.stroke("rgba(0, 214, 214, 0.75)", 2, List.of(6, 6)),
                                PrimitiveDrawingPayloads.label(
                                        "Target (Demo)",
                                        Map.of(
                                                "linePos", "end",
                                                "orientation", "normal",
                                                "size", "xs",
                                                "bg", "rgba(80, 180, 185, 0.95)",
                                                "fg", "#ffffff",
                                                "offsetPx", 30,
                                                "paddingX", 12,
                                                "paddingY", 6,
                                                "radius", 0))));

                ctx.registries().upsertDrawing(
                        "text",
                        PrimitiveDrawingPayloads.text(
                                PrimitiveDrawingPayloads.point(textIndex, zoneHigh * 1.001),
                                PrimitiveDrawingPayloads.label(
                                        "Breakout Label",
                                        Map.of(
                                                "bg", "rgba(250, 204, 21, 0.95)",
                                                "fg", "#111827",
                                                "radius", 4,
                                                "size", "xs"))));
            }

            @Override
            public void onDetach(ScriptExecutionContext ctx) {
                ctx.registries().clearDrawings();
            }
        };
    }

    private record Params(int minBars) {
    }
}
