package com.readywealth.trading.trade_engine.execution.application.port;

import com.readywealth.trading.trade_engine.engine.application.runtime.model.TradeIntent;

public interface IntentSinkPort {
    void accept(TradeIntent intent);
}
