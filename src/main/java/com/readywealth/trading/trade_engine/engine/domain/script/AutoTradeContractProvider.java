package com.readywealth.trading.trade_engine.engine.domain.script;

public interface AutoTradeContractProvider {

    AutoTradeContract autoTradeContract();

    record AutoTradeContract(String requiredOrderType, boolean autoTradable, String notes) {
    }
}
