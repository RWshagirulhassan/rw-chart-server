package com.readywealth.trading.trade_engine.engine.application;

import com.readywealth.trading.trade_engine.engine.domain.series.IntervalKind;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IntervalKindMapperTest {

    @Test
    void mapsTimeIntervals() {
        assertEquals(Duration.ofMinutes(1), IntervalKindMapper.toDuration(IntervalKind.TIME_1M));
        assertEquals(Duration.ofHours(1), IntervalKindMapper.toDuration(IntervalKind.TIME_1H));
        assertEquals(Duration.ofDays(1), IntervalKindMapper.toDuration(IntervalKind.TIME_1D));
    }

    @Test
    void rejectsTickIntervals() {
        assertThrows(IllegalArgumentException.class, () -> IntervalKindMapper.toDuration(IntervalKind.TICK_10T));
    }
}
