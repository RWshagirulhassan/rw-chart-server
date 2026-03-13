package com.readywealth.trading.trade_engine.execution.application.service;

import com.readywealth.trading.trade_engine.execution.application.port.TriggerStorePort;
import com.readywealth.trading.trade_engine.execution.domain.ExitPlan;
import com.readywealth.trading.trade_engine.execution.domain.ExitPlanStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExitPlanQueryService {

    private final TriggerStorePort triggerStorePort;

    public ExitPlanQueryService(TriggerStorePort triggerStorePort) {
        this.triggerStorePort = triggerStorePort;
    }

    public List<ExitPlan> list(String accountId, ExitPlanStatus status) {
        return triggerStorePort.listExitPlans(accountId, status);
    }
}
