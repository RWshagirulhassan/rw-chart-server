package com.readywealth.trading.trade_engine.execution.application.service;

import com.readywealth.trading.trade_engine.engine.domain.script.AutoTradeContractProvider;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptCatalog;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptId;
import com.readywealth.trading.trade_engine.execution.domain.StrategyOrderContract;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderType;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class StrategyContractResolverService {

    private final ScriptCatalog scriptCatalog;

    public StrategyContractResolverService(ScriptCatalog scriptCatalog) {
        this.scriptCatalog = scriptCatalog;
    }

    public Optional<StrategyOrderContract> resolve(String scriptId) {
        if (scriptId == null || scriptId.isBlank()) {
            return Optional.empty();
        }
        return scriptCatalog.get(new ScriptId(scriptId))
                .filter(definition -> definition instanceof AutoTradeContractProvider)
                .map(definition -> (AutoTradeContractProvider) definition)
                .flatMap(provider -> {
                    AutoTradeContractProvider.AutoTradeContract contract = provider.autoTradeContract();
                    TradingOrderType required;
                    try {
                        required = TradingOrderType.valueOf(contract.requiredOrderType());
                    } catch (Exception ex) {
                        return Optional.empty();
                    }
                    return Optional.of(new StrategyOrderContract(
                            scriptId,
                            required,
                            contract.autoTradable(),
                            contract.notes()));
                });
    }
}
