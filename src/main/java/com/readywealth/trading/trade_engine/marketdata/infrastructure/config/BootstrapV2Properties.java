package com.readywealth.trading.trade_engine.marketdata.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;

@ConfigurationProperties(prefix = "bootstrap.v2")
public class BootstrapV2Properties {

    public enum Scope {
        STRESS_ONLY,
        ALL
    }

    private boolean enabled = true;
    private String scope = Scope.ALL.name().toLowerCase(Locale.ROOT);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public Scope resolvedScope() {
        if (scope == null || scope.isBlank()) {
            return Scope.STRESS_ONLY;
        }
        try {
            String normalized = scope.trim()
                    .replace('-', '_')
                    .toUpperCase(Locale.ROOT);
            return Scope.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return Scope.STRESS_ONLY;
        }
    }
}
