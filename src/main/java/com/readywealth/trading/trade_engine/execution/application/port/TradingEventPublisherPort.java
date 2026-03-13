package com.readywealth.trading.trade_engine.execution.application.port;

import com.readywealth.trading.trade_engine.execution.domain.TradingEventEnvelope;

public interface TradingEventPublisherPort {
    void publish(TradingEventEnvelope envelope);
}
