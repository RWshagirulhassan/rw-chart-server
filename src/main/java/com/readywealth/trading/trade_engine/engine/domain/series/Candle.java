package com.readywealth.trading.trade_engine.engine.domain.series;

public record Candle(
                long instrumentToken,
                IntervalKind intervalKind,
                long startTimeEpochMs,
                long endTimeEpochMs,
                double open,
                double high,
                double low,
                double close,
                long volume,
                long tickCount,
                int barVersion,
                CandleCause cause) {
}
