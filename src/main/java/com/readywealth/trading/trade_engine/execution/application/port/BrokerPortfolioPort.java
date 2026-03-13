package com.readywealth.trading.trade_engine.execution.application.port;

import com.readywealth.trading.trade_engine.execution.domain.TradingFundsView;
import com.readywealth.trading.trade_engine.execution.domain.TradingPosition;

import java.util.List;

public interface BrokerPortfolioPort {
    List<TradingPosition> getPositions(String accountId);

    TradingFundsView getFunds(String accountId);
}
