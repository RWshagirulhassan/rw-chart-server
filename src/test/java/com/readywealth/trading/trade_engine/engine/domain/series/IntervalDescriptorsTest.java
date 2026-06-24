package com.readywealth.trading.trade_engine.engine.domain.series;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntervalDescriptorsTest {

    @Test
    void describesDirectAndDerivedIntervals() {
        IntervalDescriptor minute = IntervalDescriptors.of(IntervalKind.TIME_1M);
        assertTrue(minute.supportsVendorHistorical());
        assertFalse(minute.isDerivedFromSource());
        assertEquals(Duration.ofMinutes(1), minute.fixedDurationOrThrow());

        IntervalDescriptor week = IntervalDescriptors.of(IntervalKind.TIME_1W);
        assertTrue(week.isCalendar());
        assertTrue(week.isDerivedFromSource());
        assertEquals(IntervalKind.TIME_1D, week.sourceIntervalKindOrSelf());
        assertEquals(1500, week.defaultSourceLookbackBusinessDays());

        IntervalDescriptor month = IntervalDescriptors.of(IntervalKind.TIME_1MO);
        assertTrue(month.isCalendar());
        assertTrue(month.isDerivedFromSource());
        assertEquals(IntervalKind.TIME_1D, month.sourceIntervalKindOrSelf());
        assertEquals(2300, month.defaultSourceLookbackBusinessDays());
    }
}
