package com.readywealth.trading.trade_engine.session.application.port;

import java.util.List;

public interface UpstreamSubscriptionPort {
    void subscribeInternal(String subscriberId, List<Long> tokens);

    void unsubscribeInternal(String subscriberId, List<Long> tokens);
}
