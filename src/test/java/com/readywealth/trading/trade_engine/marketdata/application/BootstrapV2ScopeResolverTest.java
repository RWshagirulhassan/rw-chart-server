package com.readywealth.trading.trade_engine.marketdata.application;

import com.readywealth.trading.trade_engine.marketdata.infrastructure.config.BootstrapV2Properties;
import com.readywealth.trading.trade_engine.session.application.StressSessionRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BootstrapV2ScopeResolverTest {

    @Test
    void resolvesScopeAgainstStressRegistry() {
        BootstrapV2Properties properties = new BootstrapV2Properties();
        properties.setEnabled(true);
        properties.setScope("stress-only");

        StressSessionRegistry stressSessionRegistry = new StressSessionRegistry();
        stressSessionRegistry.markStressSession("s1");

        @SuppressWarnings("unchecked")
        ObjectProvider<StressSessionRegistry> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.getIfAvailable()).thenReturn(stressSessionRegistry);

        BootstrapV2ScopeResolver resolver = new BootstrapV2ScopeResolver(properties, provider);

        assertTrue(resolver.isV2EnabledForSession("s1"));
        assertFalse(resolver.isV2EnabledForSession("s2"));
    }

    @Test
    void allScopeEnablesAnySession() {
        BootstrapV2Properties properties = new BootstrapV2Properties();
        properties.setEnabled(true);
        properties.setScope("all");

        @SuppressWarnings("unchecked")
        ObjectProvider<StressSessionRegistry> provider = Mockito.mock(ObjectProvider.class);
        BootstrapV2ScopeResolver resolver = new BootstrapV2ScopeResolver(properties, provider);

        assertTrue(resolver.isV2EnabledForSession("random-session"));
    }
}
