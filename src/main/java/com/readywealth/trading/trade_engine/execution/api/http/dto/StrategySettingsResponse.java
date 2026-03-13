package com.readywealth.trading.trade_engine.execution.api.http.dto;

import com.readywealth.trading.trade_engine.execution.domain.StrategyExecutionSettings;

public record StrategySettingsResponse(
        String accountId,
        String scriptId,
        boolean enabled,
        int fixedQty,
        String product,
        long updatedAtEpochMs) {

    public static StrategySettingsResponse from(StrategyExecutionSettings settings) {
        return new StrategySettingsResponse(
                settings.accountId(),
                settings.scriptId(),
                settings.enabled(),
                settings.fixedQty(),
                settings.product().name(),
                settings.updatedAtEpochMs());
    }
}
