package com.readywealth.trading.trade_engine.execution.domain;

public record TradingFundsView(
        double availableCash,
        double utilisedMargin,
        double availableMargin,
        double openingBalance,
        Double collateral,
        long updatedAtEpochMs) {

    public static TradingFundsView opening(double openingBalance, long updatedAtEpochMs) {
        return new TradingFundsView(openingBalance, 0.0d, openingBalance, openingBalance, null, updatedAtEpochMs);
    }
}
