package com.readywealth.trading.trade_engine.marketdata.application;

import com.readywealth.trading.trade_engine.marketdata.infrastructure.config.BootstrapV2Properties;
import com.readywealth.trading.trade_engine.session.application.StressSessionRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class BootstrapV2ScopeResolver {
    private final BootstrapV2Properties bootstrapV2Properties;
    private final ObjectProvider<StressSessionRegistry> stressSessionRegistryProvider;

    public BootstrapV2ScopeResolver(
            BootstrapV2Properties bootstrapV2Properties,
            ObjectProvider<StressSessionRegistry> stressSessionRegistryProvider) {
        this.bootstrapV2Properties = bootstrapV2Properties;
        this.stressSessionRegistryProvider = stressSessionRegistryProvider;
    }

    public boolean isV2EnabledForSession(String sessionId) {
        if (!bootstrapV2Properties.isEnabled()) {
            return false;
        }
        BootstrapV2Properties.Scope scope = bootstrapV2Properties.resolvedScope();
        if (scope == BootstrapV2Properties.Scope.ALL) {
            return true;
        }
        StressSessionRegistry stressSessionRegistry = stressSessionRegistryProvider.getIfAvailable();
        return stressSessionRegistry != null && stressSessionRegistry.isStressSession(sessionId);
    }
}
