package com.readywealth.trading.trade_engine.execution.api.http.dto;

import com.readywealth.trading.trade_engine.execution.domain.TradingPosition;

public record PositionResponse(
        String accountId,
        long instrumentToken,
        String tradingsymbol,
        String product,
        int netQty,
        double avgPrice,
        double realizedPnl,
        double unrealizedPnl,
        double lastPrice,
        long updatedAtEpochMs) {

    public static PositionResponse from(TradingPosition position) {
        return new PositionResponse(
                position.accountId(),
                position.instrumentToken(),
                position.tradingsymbol(),
                position.product().name(),
                position.netQty(),
                position.avgPrice(),
                position.realizedPnl(),
                position.unrealizedPnl(),
                position.lastPrice(),
                position.updatedAtEpochMs());
    }
}
