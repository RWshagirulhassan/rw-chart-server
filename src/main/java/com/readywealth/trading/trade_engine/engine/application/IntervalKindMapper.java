package com.readywealth.trading.trade_engine.engine.application;

import com.readywealth.trading.trade_engine.engine.domain.series.IntervalKind;

import java.time.Duration;

public final class IntervalKindMapper {
    private IntervalKindMapper() {
    }

    public static Duration toDuration(IntervalKind kind) {
        return switch (kind) {
            case TIME_1M -> Duration.ofMinutes(1);
            case TIME_2M -> Duration.ofMinutes(2);
            case TIME_3M -> Duration.ofMinutes(3);
            case TIME_5M -> Duration.ofMinutes(5);
            case TIME_10M -> Duration.ofMinutes(10);
            case TIME_15M -> Duration.ofMinutes(15);
            case TIME_30M -> Duration.ofMinutes(30);
            case TIME_45M -> Duration.ofMinutes(45);
            case TIME_1H -> Duration.ofHours(1);
            case TIME_2H -> Duration.ofHours(2);
            case TIME_3H -> Duration.ofHours(3);
            case TIME_4H -> Duration.ofHours(4);
            case TIME_1D -> Duration.ofDays(1);
            case TICK_10T, TICK_100T, TICK_1000T -> throw new IllegalArgumentException(
                    "Unsupported interval for session slice: " + kind + ". Supported: TIME_* only");
        };
    }
}
