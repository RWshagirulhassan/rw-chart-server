package com.readywealth.trading.trade_engine.execution.application.service;

import com.readywealth.trading.trade_engine.execution.application.port.TradingStorePort;
import com.readywealth.trading.trade_engine.execution.domain.BrokerType;
import com.readywealth.trading.trade_engine.execution.domain.TradingAccount;
import com.readywealth.trading.trade_engine.execution.domain.TradingFundsView;
import com.readywealth.trading.trade_engine.execution.domain.TradingMode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class TradingAccountService {

    private static final String DEFAULT_CURRENCY = "INR";

    private final TradingStorePort tradingStorePort;

    public TradingAccountService(TradingStorePort tradingStorePort) {
        this.tradingStorePort = tradingStorePort;
    }

    public TradingAccount createOrUpdate(
            String userId,
            TradingMode mode,
            BrokerType brokerType,
            boolean autoTradeEnabled,
            Double openingBalance) {
        long now = Instant.now().toEpochMilli();
        double opening = openingBalance == null ? 100000.0d : Math.max(0.0d, openingBalance);
        Optional<TradingAccount> existing = tradingStorePort.findAccountByUserId(userId);
        TradingAccount account;
        if (existing.isPresent()) {
            TradingAccount current = existing.get();
            account = current.withUpdated(mode, brokerType, autoTradeEnabled, opening, now);
            account = tradingStorePort.saveAccount(account);
        } else {
            account = new TradingAccount(
                    UUID.randomUUID().toString(),
                    userId,
                    mode,
                    brokerType,
                    DEFAULT_CURRENCY,
                    autoTradeEnabled,
                    opening,
                    now,
                    now);
            account = tradingStorePort.saveAccount(account);
            tradingStorePort.saveFunds(account.accountId(), TradingFundsView.opening(opening, now));
        }
        return account;
    }

    public TradingAccount requireOwnedAccount(String accountId, String userId) {
        TradingAccount account = tradingStorePort.findAccountById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Trading account not found: " + accountId));
        if (!userId.equals(account.userId())) {
            throw new IllegalArgumentException("accountId does not belong to the current user");
        }
        return account;
    }

    public Optional<TradingAccount> findCurrent(String userId) {
        return tradingStorePort.findAccountByUserId(userId);
    }
}
