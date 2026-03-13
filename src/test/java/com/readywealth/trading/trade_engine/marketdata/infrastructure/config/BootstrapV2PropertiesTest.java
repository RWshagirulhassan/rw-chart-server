package com.readywealth.trading.trade_engine.marketdata.infrastructure.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BootstrapV2PropertiesTest {

    @Test
    void resolvedScopeParsesHyphenatedValues() {
        BootstrapV2Properties properties = new BootstrapV2Properties();

        properties.setScope("stress-only");
        assertEquals(BootstrapV2Properties.Scope.STRESS_ONLY, properties.resolvedScope());

        properties.setScope("all");
        assertEquals(BootstrapV2Properties.Scope.ALL, properties.resolvedScope());

        properties.setScope("unexpected");
        assertEquals(BootstrapV2Properties.Scope.STRESS_ONLY, properties.resolvedScope());
    }
}
