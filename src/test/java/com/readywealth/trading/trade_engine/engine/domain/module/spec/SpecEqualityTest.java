package com.readywealth.trading.trade_engine.engine.domain.module.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class SpecEqualityTest {

    @Test
    void recordSpecUsesValueEquality() {
        assertEquals(new SwingPivotsSpec(20), new SwingPivotsSpec(20));
        assertEquals(new BosChochSpec(50), new BosChochSpec(50));

        assertNotEquals(new SwingPivotsSpec(20), new SwingPivotsSpec(10));
        assertNotEquals(new BosChochSpec(20), new BosChochSpec(10));
    }
}
