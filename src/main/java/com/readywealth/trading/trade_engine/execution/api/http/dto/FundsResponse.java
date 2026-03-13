package com.readywealth.trading.trade_engine.execution.api.http.dto;

import com.readywealth.trading.trade_engine.execution.domain.TradingFundsView;

public record FundsResponse(
        double availableCash,
        double utilisedMargin,
        double availableMargin,
        double openingBalance,
        Double collateral,
        long updatedAtEpochMs) {

    public static FundsResponse from(TradingFundsView fundsView) {
        return new FundsResponse(
                fundsView.availableCash(),
                fundsView.utilisedMargin(),
                fundsView.availableMargin(),
                fundsView.openingBalance(),
                fundsView.collateral(),
                fundsView.updatedAtEpochMs());
    }
}
