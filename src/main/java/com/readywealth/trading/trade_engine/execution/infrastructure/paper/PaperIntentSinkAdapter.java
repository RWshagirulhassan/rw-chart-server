package com.readywealth.trading.trade_engine.execution.infrastructure.paper;

import com.readywealth.trading.trade_engine.engine.application.runtime.model.TradeIntent;
import com.readywealth.trading.trade_engine.execution.application.port.IntentSinkPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PaperIntentSinkAdapter implements IntentSinkPort {

    private static final Logger log = LoggerFactory.getLogger(PaperIntentSinkAdapter.class);

    @Override
    public void accept(TradeIntent intent) {
        log.info("paper_intent_received scriptInstanceId={} instrument={} side={} price={} reason={} barIndex={}",
                intent.scriptInstanceId(),
                intent.instrumentToken(),
                intent.side(),
                intent.price(),
                intent.reason(),
                intent.barIndex());
    }
}
