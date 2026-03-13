package com.readywealth.trading.trade_engine.alerts.application;

import com.readywealth.trading.trade_engine.alerts.domain.AlertRule;
import com.readywealth.trading.trade_engine.alerts.infrastructure.NoOpAlertNotifier;
import org.springframework.stereotype.Service;

@Service
public class AlertRuleService {

    private final NoOpAlertNotifier notifier;

    public AlertRuleService(NoOpAlertNotifier notifier) {
        this.notifier = notifier;
    }

    public void evaluate(AlertRule rule, boolean triggered, String message) {
        if (rule == null || !rule.enabled() || !triggered) {
            return;
        }
        notifier.notify(rule.name(), message);
    }
}
