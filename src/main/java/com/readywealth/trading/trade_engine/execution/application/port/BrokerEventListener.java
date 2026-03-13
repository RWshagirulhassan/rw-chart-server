package com.readywealth.trading.trade_engine.execution.application.port;

import com.readywealth.trading.trade_engine.execution.domain.TradingFill;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrder;

public interface BrokerEventListener {
    void onOrderUpdate(TradingOrder order);

    void onFill(TradingOrder order, TradingFill fill);
}
