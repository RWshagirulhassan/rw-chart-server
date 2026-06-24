package com.readywealth.trading.trade_engine.engine.application;

import com.readywealth.trading.trade_engine.engine.domain.series.IntervalKind;
import com.readywealth.trading.trade_engine.engine.domain.series.IntervalDescriptors;

import java.time.Duration;

public final class IntervalKindMapper {
    private IntervalKindMapper() {
    }

    public static Duration toDuration(IntervalKind kind) {
        return IntervalDescriptors.of(kind).fixedDurationOrThrow();
    }
}
