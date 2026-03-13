package com.readywealth.trading.trade_engine.execution.application.port;

import com.readywealth.trading.trade_engine.execution.domain.PriceSnapshot;

import java.util.Optional;

public interface LastPricePort {
    Optional<PriceSnapshot> getPriceSnapshot(long instrumentToken);
}
