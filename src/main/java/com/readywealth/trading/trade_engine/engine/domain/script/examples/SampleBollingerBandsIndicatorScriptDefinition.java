package com.readywealth.trading.trade_engine.engine.domain.script.examples;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
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
public class SampleBollingerBandsIndicatorScriptDefinition implements IndicatorScriptDefinition {

    private static final ScriptId ID = new ScriptId("sample.indicator.bollinger-bands");

    @Override
    public ScriptId id() {
        return ID;
    }

    @Override
    public ScriptParamsBinder<Params> paramsBinder() {
        return raw -> new Params(
                Math.max(1, raw.getInt("length", 20)),
                raw.getDouble("mult", 2.0),
                raw.getInt("offset", 0));
    }

    @Override
    public String description() {
        return "Plots Bollinger Bands (basis, upper, lower) using ta4j.";
    }

    @Override
    public List<ScriptParamMetadata> paramsMetadata() {
        return List.of(
                new ScriptParamMetadata(
                        "length",
                        "integer",
                        false,
                        20,
                        "Lookback window for basis and standard deviation."),
                new ScriptParamMetadata(
                        "mult",
                        "number",
                        false,
                        2.0,
                        "Band multiplier applied on standard deviation."),
                new ScriptParamMetadata(
                        "offset",
                        "integer",
                        false,
                        0,
                        "Horizontal index offset applied to plotted points."));
    }

    @Override
    public ScriptRuntime createRuntime(ScriptInitContext init, Object typedParams) {
        Params params = (Params) typedParams;
        ClosePriceIndicator close = new ClosePriceIndicator(init.seriesContext().series());
        SMAIndicator sma = new SMAIndicator(close, params.length());
        BollingerBandsMiddleIndicator basis = new BollingerBandsMiddleIndicator(sma);
        StandardDeviationIndicator stdev = new StandardDeviationIndicator(close, params.length());
        Num k = init.seriesContext().numFactory().numOf(params.mult());
        BollingerBandsUpperIndicator upper = new BollingerBandsUpperIndicator(basis, stdev, k);
        BollingerBandsLowerIndicator lower = new BollingerBandsLowerIndicator(basis, stdev, k);

        return new ScriptRuntime() {
            @Override
            public void onBar(ScriptExecutionContext ctx, int index) {
                int plottedIndex = index + params.offset();
                if (plottedIndex < 0) {
                    return;
                }
                ctx.registries().appendPlotPoint(
                        "basis",
                        Map.of("index", plottedIndex, "value", basis.getValue(index).doubleValue()));
                ctx.registries().appendPlotPoint(
                        "upper",
                        Map.of("index", plottedIndex, "value", upper.getValue(index).doubleValue()));
                ctx.registries().appendPlotPoint(
                        "lower",
                        Map.of("index", plottedIndex, "value", lower.getValue(index).doubleValue()));
            }
        };
    }

    private record Params(int length, double mult, int offset) {
    }
}
