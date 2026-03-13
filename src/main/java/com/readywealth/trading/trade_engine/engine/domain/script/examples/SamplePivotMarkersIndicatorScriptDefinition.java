package com.readywealth.trading.trade_engine.engine.domain.script.examples;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
public class SamplePivotMarkersIndicatorScriptDefinition implements IndicatorScriptDefinition {

    private static final ScriptId ID = new ScriptId("sample.indicator.pivot-markers");

    @Override
    public ScriptId id() {
        return ID;
    }

    @Override
    public ScriptParamsBinder<Params> paramsBinder() {
        return raw -> new Params(
                Math.max(1, raw.getInt("leftBars", 2)),
                Math.max(1, raw.getInt("rightBars", 2)),
                Math.max(1, raw.getInt("maxMarkers", 80)));
    }

    @Override
    public String description() {
        return "Marks confirmed pivot highs/lows using primitive marker drawings.";
    }

    @Override
    public List<ScriptParamMetadata> paramsMetadata() {
        return List.of(
                new ScriptParamMetadata(
                        "leftBars",
                        "integer",
                        false,
                        2,
                        "Bars to the left used to confirm a pivot."),
                new ScriptParamMetadata(
                        "rightBars",
                        "integer",
                        false,
                        2,
                        "Bars to the right used to confirm a pivot."),
                new ScriptParamMetadata(
                        "maxMarkers",
                        "integer",
                        false,
                        80,
                        "Maximum pivot markers retained on chart for this script instance."));
    }

    @Override
    public ScriptRuntime createRuntime(ScriptInitContext init, Object typedParams) {
        Params params = (Params) typedParams;
        return new ScriptRuntime() {
            private final Set<Integer> emittedHighPivots = new LinkedHashSet<>();
            private final Set<Integer> emittedLowPivots = new LinkedHashSet<>();
            private final Deque<String> emittedDrawingOrder = new ArrayDeque<>();

            @Override
            public void onBar(ScriptExecutionContext ctx, int index) {
                BarSeries series = ctx.seriesContext().series();
                int begin = series.getBeginIndex();
                int end = series.getEndIndex();
                if (begin < 0 || end < begin) {
                    return;
                }
                int currentIndex = Math.max(begin, Math.min(index, end));
                int pivotIndex = currentIndex - params.rightBars();
                if (pivotIndex < begin + params.leftBars()) {
                    return;
                }
                if (pivotIndex + params.rightBars() > end) {
                    return;
                }

                if (isPivotHigh(series, pivotIndex, params.leftBars(), params.rightBars())
                        && emittedHighPivots.add(pivotIndex)) {
                    String drawingId = "ph-" + pivotIndex;
                    double price = series.getBar(pivotIndex).getHighPrice().doubleValue();
                    ctx.registries().upsertDrawing(
                            drawingId,
                            PrimitiveDrawingPayloads.marker(
                                    PrimitiveDrawingPayloads.point(pivotIndex, price),
                                    "triangleDown",
                                    10.0,
                                    "PH"));
                    trackDrawing(ctx, drawingId, params.maxMarkers());
                }

                if (isPivotLow(series, pivotIndex, params.leftBars(), params.rightBars())
                        && emittedLowPivots.add(pivotIndex)) {
                    String drawingId = "pl-" + pivotIndex;
                    double price = series.getBar(pivotIndex).getLowPrice().doubleValue();
                    ctx.registries().upsertDrawing(
                            drawingId,
                            PrimitiveDrawingPayloads.marker(
                                    PrimitiveDrawingPayloads.point(pivotIndex, price),
                                    "triangleUp",
                                    10.0,
                                    "PL"));
                    trackDrawing(ctx, drawingId, params.maxMarkers());
                }
            }

            @Override
            public void onDetach(ScriptExecutionContext ctx) {
                ctx.registries().clearDrawings();
                emittedHighPivots.clear();
                emittedLowPivots.clear();
                emittedDrawingOrder.clear();
            }

            private void trackDrawing(ScriptExecutionContext ctx, String drawingId, int maxMarkers) {
                emittedDrawingOrder.addLast(drawingId);
                while (emittedDrawingOrder.size() > maxMarkers) {
                    String removedId = emittedDrawingOrder.removeFirst();
                    emittedHighPivots.remove(parsePivotIndex(removedId));
                    emittedLowPivots.remove(parsePivotIndex(removedId));
                    ctx.registries().removeDrawing(removedId);
                }
            }

            private Integer parsePivotIndex(String drawingId) {
                int dash = drawingId.indexOf('-');
                if (dash < 0 || dash + 1 >= drawingId.length()) {
                    return null;
                }
                try {
                    return Integer.parseInt(drawingId.substring(dash + 1));
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
        };
    }

    private static boolean isPivotHigh(BarSeries series, int pivotIndex, int leftBars, int rightBars) {
        double pivotHigh = series.getBar(pivotIndex).getHighPrice().doubleValue();
        for (int i = pivotIndex - leftBars; i <= pivotIndex + rightBars; i++) {
            if (i == pivotIndex) {
                continue;
            }
            double testHigh = series.getBar(i).getHighPrice().doubleValue();
            if (testHigh >= pivotHigh) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPivotLow(BarSeries series, int pivotIndex, int leftBars, int rightBars) {
        double pivotLow = series.getBar(pivotIndex).getLowPrice().doubleValue();
        for (int i = pivotIndex - leftBars; i <= pivotIndex + rightBars; i++) {
            if (i == pivotIndex) {
                continue;
            }
            double testLow = series.getBar(i).getLowPrice().doubleValue();
            if (testLow <= pivotLow) {
                return false;
            }
        }
        return true;
    }

    private record Params(int leftBars, int rightBars, int maxMarkers) {
    }
}
