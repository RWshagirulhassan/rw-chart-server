package com.readywealth.trading.trade_engine.execution.api.http.dto;

import com.readywealth.trading.trade_engine.execution.domain.TradingAccount;

public record TradingAccountResponse(
        String accountId,
        String userId,
        String mode,
        String brokerType,
        String baseCurrency,
        boolean autoTradeEnabled,
        double openingBalance,
        long createdAtEpochMs,
        long updatedAtEpochMs) {

    public static TradingAccountResponse from(TradingAccount tradingAccount) {
        return new TradingAccountResponse(
                tradingAccount.accountId(),
                tradingAccount.userId(),
                tradingAccount.mode().name(),
                tradingAccount.brokerType().name(),
                tradingAccount.baseCurrency(),
                tradingAccount.autoTradeEnabled(),
                tradingAccount.openingBalance(),
                tradingAccount.createdAtEpochMs(),
                tradingAccount.updatedAtEpochMs());
    }
}
