package com.readywealth.trading.trade_engine.engine.domain.series;

import java.time.Duration;
import java.util.Objects;

public record IntervalDescriptor(
        IntervalKind kind,
        IntervalFamily family,
        Duration fixedDuration,
        Duration defaultBarBuilderDuration,
        IntervalKind sourceIntervalKind,
        String kiteInterval,
        int defaultSourceLookbackBusinessDays) {

    public IntervalDescriptor {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(defaultBarBuilderDuration, "defaultBarBuilderDuration");
        if (defaultSourceLookbackBusinessDays < 0) {
            throw new IllegalArgumentException("defaultSourceLookbackBusinessDays must be >= 0");
        }
    }

    public boolean isTick() {
        return family == IntervalFamily.TICK;
    }

    public boolean isIntraday() {
        return family == IntervalFamily.INTRADAY;
    }

    public boolean isCalendar() {
        return family == IntervalFamily.CALENDAR;
    }

    public boolean isDaily() {
        return family == IntervalFamily.DAILY;
    }

    public boolean hasFixedDuration() {
        return fixedDuration != null;
    }

    public boolean supportsVendorHistorical() {
        return kiteInterval != null && !kiteInterval.isBlank();
    }

    public boolean isDerivedFromSource() {
        return sourceIntervalKind != null && sourceIntervalKind != kind;
    }

    public IntervalKind sourceIntervalKindOrSelf() {
        return sourceIntervalKind == null ? kind : sourceIntervalKind;
    }

    public Duration fixedDurationOrThrow() {
        if (fixedDuration == null) {
            throw new IllegalArgumentException("Interval " + kind + " does not have a fixed duration");
        }
        return fixedDuration;
    }
}
