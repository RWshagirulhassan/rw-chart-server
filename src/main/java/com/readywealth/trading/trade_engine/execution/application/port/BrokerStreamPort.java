package com.readywealth.trading.trade_engine.execution.application.port;

public interface BrokerStreamPort {
    BrokerStreamSubscription subscribe(String accountId, BrokerEventListener listener);
}
