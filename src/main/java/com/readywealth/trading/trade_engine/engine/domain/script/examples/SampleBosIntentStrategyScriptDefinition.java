package com.readywealth.trading.trade_engine.engine.domain.script.examples;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.readywealth.trading.trade_engine.engine.domain.module.boschoch.BosChochModule;
import com.readywealth.trading.trade_engine.engine.domain.module.boschoch.BreakQuery;
import com.readywealth.trading.trade_engine.engine.domain.module.spec.BosChochSpec;
import com.readywealth.trading.trade_engine.engine.domain.module.spec.ModuleSpec;
import com.readywealth.trading.trade_engine.engine.domain.script.AutoTradeContractProvider;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptDefinition;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptExecutionContext;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptId;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptInitContext;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptKind;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptParamMetadata;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptParamsBinder;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptRuntime;
import com.readywealth.trading.trade_engine.engine.domain.script.examples.support.PrimitiveDrawingPayloads;
import com.readywealth.trading.trade_engine.engine.application.runtime.model.TradeIntent;

@Component
public class SampleBosIntentStrategyScriptDefinition implements ScriptDefinition, AutoTradeContractProvider {

    private static final ScriptId ID = new ScriptId("sample.strategy.bos-intent");

    @Override
    public AutoTradeContract autoTradeContract() {
        return new AutoTradeContract("MARKET", true, "Directional BOS/CHOCH strategy contract");
    }

    @Override
    public ScriptId id() {
        return ID;
    }

    @Override
    public ScriptKind kind() {
        return ScriptKind.STRATEGY;
    }

    @Override
    public ScriptParamsBinder<Params> paramsBinder() {
        return raw -> new Params(raw.getInt("len", 20));
    }

    @Override
    public String description() {
        return "Generates BOS/CHOCH intents and drawing markers from structure-break signals.";
    }

    @Override
    public List<ScriptParamMetadata> paramsMetadata() {
        return List.of(new ScriptParamMetadata(
                "len",
                "integer",
                false,
                20,
                "Lookback length used by BOS/CHOCH module."));
    }

    @Override
    public List<ModuleSpec<?>> requiredModules(Object typedParams) {
        Params params = (Params) typedParams;
        return List.of(new BosChochSpec(params.len()));
    }

    @Override
    public ScriptRuntime createRuntime(ScriptInitContext init, Object typedParams) {
        Params params = (Params) typedParams;
        return new ScriptRuntime() {
            private int lastSignalledBreakBar = Integer.MIN_VALUE;

            @Override
            public void onBar(ScriptExecutionContext ctx, int index) {
                BosChochModule module = ctx.seriesContext().context().require(new BosChochSpec(params.len()));
                var recent = module.query(BreakQuery.all().limit(1));
                if (recent.isEmpty()) {
                    return;
                }
                var ev = recent.get(0);
                if (ev.barIndex() == lastSignalledBreakBar) {
                    return;
                }

                double close = ctx.seriesContext().series().getBar(index).getClosePrice().doubleValue();
                TradeIntent intent = new TradeIntent(
                        Instant.now().toEpochMilli(),
                        init.scriptInstanceId().value(),
                        ctx.seriesContext().key().instrumentToken(),
                        ev.dir().name(),
                        "bos_or_choch_" + ev.cls().name().toLowerCase(),
                        close,
                        index);
                ctx.emitIntent(intent);
                int markerIndex = Math.max(ctx.seriesContext().series().getBeginIndex(),
                        Math.min(ev.barIndex(), ctx.seriesContext().series().getEndIndex()));
                double markerPrice = ctx.seriesContext().series().getBar(markerIndex).getClosePrice().doubleValue();
                String shape = "UP".equals(ev.dir().name()) ? "triangle" : "diamond";
                ctx.registries().upsertDrawing(
                        "bos-" + ev.barIndex(),
                        PrimitiveDrawingPayloads.marker(
                                PrimitiveDrawingPayloads.point(markerIndex, markerPrice),
                                shape,
                                8,
                                ev.cls().name()));
                lastSignalledBreakBar = ev.barIndex();
            }
        };
    }

    private record Params(int len) {
    }
}
