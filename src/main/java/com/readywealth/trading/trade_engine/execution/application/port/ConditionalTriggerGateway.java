package com.readywealth.trading.trade_engine.execution.application.port;

import com.readywealth.trading.trade_engine.execution.domain.Trigger;

public interface ConditionalTriggerGateway {
    Trigger register(Trigger trigger);

    Trigger modify(Trigger trigger);

    void cancel(String accountId, String triggerId);
}
