package com.readywealth.trading.trade_engine.execution.application;

import com.readywealth.trading.trade_engine.engine.application.runtime.model.TradeIntent;
import com.readywealth.trading.trade_engine.execution.application.port.IntentSinkPort;
import org.springframework.stereotype.Service;

@Service
public class PaperExecutionService {

    private final IntentSinkPort intentSinkPort;

    public PaperExecutionService(IntentSinkPort intentSinkPort) {
        this.intentSinkPort = intentSinkPort;
    }

    public void submit(TradeIntent intent) {
        intentSinkPort.accept(intent);
    }
}
