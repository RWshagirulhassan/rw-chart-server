package com.readywealth.trading.trade_engine.engine.domain.script;

public interface ScriptRuntime {
    void onBar(ScriptExecutionContext ctx, int index);

    default void onInvalidateFrom(ScriptExecutionContext ctx, int index) {
    }

    default void onDetach(ScriptExecutionContext ctx) {
    }
}
