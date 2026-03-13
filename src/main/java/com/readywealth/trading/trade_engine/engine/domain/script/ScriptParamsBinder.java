package com.readywealth.trading.trade_engine.engine.domain.script;

public interface ScriptParamsBinder<T> {
    T bind(ScriptParams raw);
}
