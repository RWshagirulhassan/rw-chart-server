package com.readywealth.trading.trade_engine.execution.domain;

public record TradingAccount(
        String accountId,
        String userId,
        TradingMode mode,
        BrokerType brokerType,
        String baseCurrency,
        boolean autoTradeEnabled,
        double openingBalance,
        long createdAtEpochMs,
        long updatedAtEpochMs) {

    public TradingAccount withUpdated(
            TradingMode mode,
            BrokerType brokerType,
            boolean autoTradeEnabled,
            double openingBalance,
            long updatedAtEpochMs) {
        return new TradingAccount(
                accountId,
                userId,
                mode,
                brokerType,
                baseCurrency,
                autoTradeEnabled,
                openingBalance,
                createdAtEpochMs,
                updatedAtEpochMs);
    }
}
