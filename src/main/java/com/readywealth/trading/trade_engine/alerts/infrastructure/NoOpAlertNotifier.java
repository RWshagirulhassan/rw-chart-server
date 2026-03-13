package com.readywealth.trading.trade_engine.alerts.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NoOpAlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(NoOpAlertNotifier.class);

    public void notify(String channel, String message) {
        log.info("alert_noop_notify channel={} message={}", channel, message);
    }
}
