package com.readywealth.trading.trade_engine.engine.domain.script;

public interface IndicatorScriptDefinition extends ScriptDefinition {
    @Override
    default ScriptKind kind() {
        return ScriptKind.INDICATOR;
    }
}
