package com.readywealth.trading.trade_engine.execution.application.service;

import com.readywealth.trading.trade_engine.execution.application.port.TradingEventPublisherPort;
import com.readywealth.trading.trade_engine.execution.application.port.TradingStorePort;
import com.readywealth.trading.trade_engine.execution.domain.FillApplyCommand;
import com.readywealth.trading.trade_engine.execution.domain.FillApplyResult;
import com.readywealth.trading.trade_engine.execution.domain.TradingAccount;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventEnvelope;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventSource;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventType;
import com.readywealth.trading.trade_engine.execution.domain.TradingFill;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrder;
import com.readywealth.trading.trade_engine.execution.infrastructure.config.TradingProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class FillApplicationService {

    private final TradingStorePort tradingStorePort;
    private final TradingEventPublisherPort tradingEventPublisherPort;
    private final TradingProperties tradingProperties;
    private final LinkedOrderLifecycleService linkedOrderLifecycleService;
    private final Counter fillCounter;

    public FillApplicationService(
            TradingStorePort tradingStorePort,
            TradingEventPublisherPort tradingEventPublisherPort,
            TradingProperties tradingProperties,
            LinkedOrderLifecycleService linkedOrderLifecycleService,
            MeterRegistry meterRegistry) {
        this.tradingStorePort = tradingStorePort;
        this.tradingEventPublisherPort = tradingEventPublisherPort;
        this.tradingProperties = tradingProperties;
        this.linkedOrderLifecycleService = linkedOrderLifecycleService;
        this.fillCounter = meterRegistry.counter("trading_fills_total");
    }

    public FillApplyResult applyFill(TradingAccount account, TradingOrder order, TradingFill fill, double markPrice) {
        FillApplyResult result = tradingStorePort.applyFillAtomically(new FillApplyCommand(
                account,
                order,
                fill,
                markPrice,
                tradingProperties.getRetention().getDedupeTtlSeconds()));
        if (result.duplicate()) {
            return result;
        }

        long now = System.currentTimeMillis();
        TradingEventSource source = resolveSource(account);
        tradingEventPublisherPort.publish(new TradingEventEnvelope(
                UUID.randomUUID().toString(),
                account.accountId(),
                TradingEventType.FILL_ADDED,
                now,
                source,
                result.fill()));
        tradingEventPublisherPort.publish(new TradingEventEnvelope(
                UUID.randomUUID().toString(),
                account.accountId(),
                TradingEventType.ORDER_UPDATED,
                now,
                source,
                result.order()));
        tradingEventPublisherPort.publish(new TradingEventEnvelope(
                UUID.randomUUID().toString(),
                account.accountId(),
                TradingEventType.POSITION_UPDATED,
                now,
                source,
                result.position()));
        tradingEventPublisherPort.publish(new TradingEventEnvelope(
                UUID.randomUUID().toString(),
                account.accountId(),
                TradingEventType.FUNDS_UPDATED,
                now,
                source,
                result.funds()));
        linkedOrderLifecycleService.onOrderUpdated(account, result.order());
        fillCounter.increment();
        return result;
    }

    private TradingEventSource resolveSource(TradingAccount account) {
        return switch (account.brokerType()) {
            case PAPER -> TradingEventSource.PAPER;
            case KITE -> TradingEventSource.KITE;
        };
    }
}
