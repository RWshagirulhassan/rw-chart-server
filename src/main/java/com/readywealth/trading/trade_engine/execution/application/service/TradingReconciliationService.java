package com.readywealth.trading.trade_engine.execution.application.service;

import com.readywealth.trading.trade_engine.execution.application.port.TradingEventPublisherPort;
import com.readywealth.trading.trade_engine.execution.application.port.TradingStorePort;
import com.readywealth.trading.trade_engine.execution.domain.TradingAccount;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventEnvelope;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventSource;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventType;
import com.readywealth.trading.trade_engine.execution.domain.TradingMode;
import com.readywealth.trading.trade_engine.execution.infrastructure.config.TradingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class TradingReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(TradingReconciliationService.class);

    private final TradingStorePort tradingStorePort;
    private final TradingEventPublisherPort tradingEventPublisherPort;
    private final TradingProperties tradingProperties;

    public TradingReconciliationService(
            TradingStorePort tradingStorePort,
            TradingEventPublisherPort tradingEventPublisherPort,
            TradingProperties tradingProperties) {
        this.tradingStorePort = tradingStorePort;
        this.tradingEventPublisherPort = tradingEventPublisherPort;
        this.tradingProperties = tradingProperties;
    }

    @Scheduled(fixedDelayString = "${trading.reconciliation.fixed-delay-ms:5000}")
    public void reconcileLiveAccounts() {
        if (!tradingProperties.getReconciliation().isEnabled()) {
            return;
        }
        List<TradingAccount> liveAccounts = tradingStorePort.listAccountsByMode(TradingMode.LIVE);
        for (TradingAccount account : liveAccounts) {
            // v1 skeleton: establish cadence and stream shape.
            log.debug("trading_reconciliation_tick accountId={}", account.accountId());
            tradingEventPublisherPort.publish(new TradingEventEnvelope(
                    UUID.randomUUID().toString(),
                    account.accountId(),
                    TradingEventType.RECONCILED,
                    System.currentTimeMillis(),
                    TradingEventSource.KITE,
                    Map.of("accountId", account.accountId(), "status", "scheduled_tick")));
        }
    }
}
