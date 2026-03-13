package com.readywealth.trading.trade_engine.execution.api.http.dto;

import com.readywealth.trading.trade_engine.execution.domain.ExitLegSpec;

public record ExitLegResponse(
        String legType,
        double triggerPrice,
        double limitOffset,
        String orderSide,
        int qty) {

    public static ExitLegResponse from(ExitLegSpec leg) {
        if (leg == null) {
            return null;
        }
        return new ExitLegResponse(
                leg.legType().name(),
                leg.triggerPrice(),
                leg.limitOffset(),
                leg.orderSide().name(),
                leg.qty());
    }
}
