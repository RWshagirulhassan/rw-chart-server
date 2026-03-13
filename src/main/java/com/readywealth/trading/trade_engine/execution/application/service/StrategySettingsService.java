package com.readywealth.trading.trade_engine.execution.application.service;

import com.readywealth.trading.trade_engine.execution.application.port.TradingStorePort;
import com.readywealth.trading.trade_engine.execution.domain.StrategyExecutionSettings;
import com.readywealth.trading.trade_engine.execution.domain.TradingProduct;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class StrategySettingsService {

    private final TradingStorePort tradingStorePort;

    public StrategySettingsService(TradingStorePort tradingStorePort) {
        this.tradingStorePort = tradingStorePort;
    }

    public StrategyExecutionSettings upsert(
            String accountId,
            String scriptId,
            boolean enabled,
            int fixedQty,
            TradingProduct product) {
        if (scriptId == null || scriptId.isBlank()) {
            throw new IllegalArgumentException("scriptId is required");
        }
        if (fixedQty <= 0) {
            throw new IllegalArgumentException("fixedQty must be positive");
        }
        StrategyExecutionSettings settings = new StrategyExecutionSettings(
                accountId,
                scriptId.trim(),
                enabled,
                fixedQty,
                product == null ? TradingProduct.MIS : product,
                Instant.now().toEpochMilli());
        return tradingStorePort.saveStrategySettings(settings);
    }

    public Optional<StrategyExecutionSettings> get(String accountId, String scriptId) {
        return tradingStorePort.getStrategySettings(accountId, scriptId);
    }

    public List<StrategyExecutionSettings> list(String accountId) {
        return tradingStorePort.listStrategySettings(accountId);
    }
}
