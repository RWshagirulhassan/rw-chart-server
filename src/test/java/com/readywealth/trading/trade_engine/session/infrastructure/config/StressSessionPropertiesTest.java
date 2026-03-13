package com.readywealth.trading.trade_engine.session.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StressSessionPropertiesTest {

    @Test
    void normalizesConfiguredTokensAndSymbols() {
        StressSessionProperties properties = new StressSessionProperties();
        properties.setInstrumentTokens(Arrays.asList(256265L, null, -1L, 408065L, 256265L));
        properties.setSymbols(List.of(" infy ", "", "TCS", "infy"));

        assertEquals(List.of(256265L, 408065L), properties.normalizedInstrumentTokens());
        assertEquals(List.of("INFY", "TCS"), properties.normalizedSymbols());
    }

    @Test
    void clampsMinimumLimits() {
        StressSessionProperties properties = new StressSessionProperties();
        properties.setMaxBarCount(0);
        properties.setMaxInstruments(-50);

        assertEquals(1, properties.resolvedMaxBarCount());
        assertEquals(1, properties.resolvedMaxInstruments());
    }
}
