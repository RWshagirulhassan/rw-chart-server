package com.readywealth.trading.trade_engine.engine.domain.script;

import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;

import com.readywealth.trading.trade_engine.engine.domain.series.BarSeriesContext;

public interface StrategyScriptDefinition extends ScriptDefinition {
    @Override
    default ScriptKind kind() {
        return ScriptKind.STRATEGY;
    }

    Strategy buildTa4jStrategy(BarSeriesContext ctx, Object typedParams);

    default TradingRecord createTradingRecord(BarSeriesContext ctx, Object typedParams) {
        return new BaseTradingRecord();
    }
}
