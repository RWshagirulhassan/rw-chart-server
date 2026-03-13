package com.readywealth.trading.trade_engine.session.infrastructure.memory;

import com.readywealth.trading.trade_engine.engine.domain.series.IntervalKind;
import com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryConcurrentBarSeriesRegistryTest {

    @Test
    void createReuseAndDiscardByRefs() {
        InMemoryConcurrentBarSeriesRegistry registry = new InMemoryConcurrentBarSeriesRegistry();
        SeriesKey key = new SeriesKey(256265L, IntervalKind.TIME_1M);

        var first = registry.acquire(key, 1000);
        assertTrue(first.created());
        assertEquals(1, first.entry().seriesRefCount().get());

        var second = registry.acquire(key, 1000);
        assertFalse(second.created());
        assertEquals(2, second.entry().seriesRefCount().get());
        assertEquals(1, registry.size());

        var rel1 = registry.release(key);
        assertTrue(rel1.existed());
        assertFalse(rel1.removed());
        assertEquals(1, rel1.remainingRefs());

        var rel2 = registry.release(key);
        assertTrue(rel2.existed());
        assertTrue(rel2.removed());
        assertEquals(0, rel2.remainingRefs());
        assertTrue(registry.get(key).isEmpty());
    }
}
