package com.readywealth.trading.trade_engine.marketdata.api.http.dto;

import java.util.List;

public record ClientHistoricalCandlesResponse(
        long instrumentToken,
        String intervalKind,
        String from,
        String to,
        int count,
        List<ClientCandleView> candles) {
}
