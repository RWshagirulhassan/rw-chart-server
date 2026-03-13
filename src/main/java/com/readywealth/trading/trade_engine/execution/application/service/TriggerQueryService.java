package com.readywealth.trading.trade_engine.execution.application.service;

import com.readywealth.trading.trade_engine.execution.application.port.TriggerStorePort;
import com.readywealth.trading.trade_engine.execution.domain.Trigger;
import com.readywealth.trading.trade_engine.execution.domain.TriggerStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TriggerQueryService {

    private final TriggerStorePort triggerStorePort;

    public TriggerQueryService(TriggerStorePort triggerStorePort) {
        this.triggerStorePort = triggerStorePort;
    }

    public List<Trigger> triggers(String accountId, TriggerStatus status) {
        return triggerStorePort.listTriggers(accountId, status);
    }
}
