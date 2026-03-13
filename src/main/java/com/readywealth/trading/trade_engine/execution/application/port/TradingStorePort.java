package com.readywealth.trading.trade_engine.execution.application.port;

import com.readywealth.trading.trade_engine.execution.domain.BrokerType;
import com.readywealth.trading.trade_engine.execution.domain.FillApplyCommand;
import com.readywealth.trading.trade_engine.execution.domain.FillApplyResult;
import com.readywealth.trading.trade_engine.execution.domain.MarginCheckResult;
import com.readywealth.trading.trade_engine.execution.domain.StrategyExecutionSettings;
import com.readywealth.trading.trade_engine.execution.domain.TradingAccount;
import com.readywealth.trading.trade_engine.execution.domain.TradingFundsView;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrder;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderStatus;
import com.readywealth.trading.trade_engine.execution.domain.TradingPosition;
import com.readywealth.trading.trade_engine.execution.domain.TradingProduct;
import com.readywealth.trading.trade_engine.execution.domain.TradingMode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TradingStorePort {
    TradingAccount saveAccount(TradingAccount account);

    Optional<TradingAccount> findAccountById(String accountId);

    Optional<TradingAccount> findAccountByUserId(String userId);

    List<TradingAccount> listAccountsByMode(TradingMode mode);

    TradingOrder saveOrder(TradingOrder order);

    Optional<TradingOrder> findOrder(String accountId, String orderId);

    List<TradingOrder> listOrders(String accountId, TradingOrderStatus status);

    TradingFundsView saveFunds(String accountId, TradingFundsView fundsView);

    Optional<TradingFundsView> findFunds(String accountId);

    TradingPosition savePosition(TradingPosition position);

    List<TradingPosition> listPositions(String accountId);

    Optional<TradingPosition> findPosition(String accountId, long instrumentToken, TradingProduct product);

    void saveLastMargin(String accountId, MarginCheckResult marginCheckResult);

    Optional<MarginCheckResult> getLastMargin(String accountId);

    StrategyExecutionSettings saveStrategySettings(StrategyExecutionSettings settings);

    Optional<StrategyExecutionSettings> getStrategySettings(String accountId, String scriptId);

    List<StrategyExecutionSettings> listStrategySettings(String accountId);

    FillApplyResult applyFillAtomically(FillApplyCommand fillApplyCommand);

    boolean registerIntentExecution(String accountId, String dedupeKey, int ttlSeconds);

    long countOpenOrders(String accountId);

    long incrementOrderRateCounter(String accountId, Instant at);
}
