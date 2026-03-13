package com.readywealth.trading.trade_engine.execution.application.service;

import com.readywealth.trading.trade_engine.execution.application.port.BrokerGateway;
import com.readywealth.trading.trade_engine.execution.application.port.CancelOrderResult;
import com.readywealth.trading.trade_engine.execution.application.port.ModifyOrderResult;
import com.readywealth.trading.trade_engine.execution.application.port.PlaceOrderResult;
import com.readywealth.trading.trade_engine.execution.application.port.TradingEventPublisherPort;
import com.readywealth.trading.trade_engine.execution.application.port.TradingStorePort;
import com.readywealth.trading.trade_engine.execution.domain.MarginCheckResult;
import com.readywealth.trading.trade_engine.execution.domain.OrderDraft;
import com.readywealth.trading.trade_engine.execution.domain.TradingAccount;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventEnvelope;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventSource;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventType;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrder;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderStatus;
import com.readywealth.trading.trade_engine.execution.infrastructure.config.TradingProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TradeOrchestratorService {

    private final BrokerGatewayResolver brokerGatewayResolver;
    private final MarginGatewayService marginGatewayService;
    private final TradingStorePort tradingStorePort;
    private final TradingEventPublisherPort tradingEventPublisherPort;
    private final OrderStateMachineService orderStateMachineService;
    private final FillApplicationService fillApplicationService;
    private final LinkedOrderLifecycleService linkedOrderLifecycleService;
    private final TradingProperties tradingProperties;
    private final Counter orderAttempts;
    private final Counter orderRejects;

    public TradeOrchestratorService(
            BrokerGatewayResolver brokerGatewayResolver,
            MarginGatewayService marginGatewayService,
            TradingStorePort tradingStorePort,
            TradingEventPublisherPort tradingEventPublisherPort,
            OrderStateMachineService orderStateMachineService,
            FillApplicationService fillApplicationService,
            LinkedOrderLifecycleService linkedOrderLifecycleService,
            TradingProperties tradingProperties,
            MeterRegistry meterRegistry) {
        this.brokerGatewayResolver = brokerGatewayResolver;
        this.marginGatewayService = marginGatewayService;
        this.tradingStorePort = tradingStorePort;
        this.tradingEventPublisherPort = tradingEventPublisherPort;
        this.orderStateMachineService = orderStateMachineService;
        this.fillApplicationService = fillApplicationService;
        this.linkedOrderLifecycleService = linkedOrderLifecycleService;
        this.tradingProperties = tradingProperties;
        this.orderAttempts = meterRegistry.counter("trading_order_attempts_total");
        this.orderRejects = meterRegistry.counter("trading_order_rejects_total");
    }

    public TradingOrder placeOrder(TradingAccount account, OrderDraft draft) {
        validateDraft(draft);
        orderAttempts.increment();

        TradingOrder rejected = guardrailsRejectIfNeeded(account, draft);
        if (rejected != null) {
            return rejected;
        }

        if (!draft.reduceOnly()) {
            MarginCheckResult marginCheckResult = marginGatewayService.checkMargins(account, List.of(draft));
            if (marginCheckResult.degraded()) {
                publishEvent(account, TradingEventType.MARGIN_DEGRADED, TradingEventSource.CORE, marginCheckResult);
            }
            if (!marginCheckResult.allowed()) {
                return rejectDraft(account, draft, marginCheckResult.message());
            }
        }

        long now = Instant.now().toEpochMilli();
        TradingOrder canonicalOrder = new TradingOrder(
                UUID.randomUUID().toString(),
                null,
                account.accountId(),
                draft.instrumentToken(),
                draft.tradingsymbol(),
                draft.exchange(),
                draft.product(),
                draft.orderType(),
                draft.side(),
                draft.qty(),
                draft.price(),
                draft.triggerPrice(),
                TradingOrderStatus.NEW,
                0,
                0.0d,
                now,
                now,
                null,
                draft.source(),
                draft.scriptId(),
                draft.reason(),
                draft.parentOrderId(),
                draft.exitPlanId(),
                draft.triggerId(),
                draft.ocoGroupId(),
                draft.role(),
                draft.reduceOnly());

        BrokerGateway brokerGateway = brokerGatewayResolver.resolve(account);
        PlaceOrderResult placeOrderResult = brokerGateway.placeOrder(draft, canonicalOrder);
        TradingOrder accepted = placeOrderResult.order() == null
                ? canonicalOrder.withStatus(TradingOrderStatus.REJECTED, now, "broker returned null order")
                : placeOrderResult.order();

        orderStateMachineService.ensureTransition(canonicalOrder.status(), accepted.status(), canonicalOrder.orderId());
        TradingOrder saved = tradingStorePort.saveOrder(accepted);

        if (saved.status() == TradingOrderStatus.REJECTED) {
            orderRejects.increment();
            publishEvent(account, TradingEventType.ORDER_REJECTED, resolveSource(account), saved);
        } else {
            publishEvent(account, TradingEventType.ORDER_ACCEPTED, resolveSource(account), saved);
        }

        if (placeOrderResult.fills() != null) {
            for (var fill : placeOrderResult.fills()) {
                fillApplicationService.applyFill(account, saved, fill, fill.price());
            }
        }

        return tradingStorePort.findOrder(account.accountId(), saved.orderId()).orElse(saved);
    }

    public TradingOrder cancelOrder(TradingAccount account, String orderId, String reason) {
        TradingOrder current = tradingStorePort.findOrder(account.accountId(), orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
        if (current.status().terminal()) {
            throw new IllegalArgumentException("order is already terminal: " + current.status());
        }

        BrokerGateway brokerGateway = brokerGatewayResolver.resolve(account);
        CancelOrderResult cancelOrderResult = brokerGateway.cancelOrder(current, reason);
        TradingOrder updated = cancelOrderResult.order();
        orderStateMachineService.ensureTransition(current.status(), updated.status(), orderId);
        TradingOrder saved = tradingStorePort.saveOrder(updated);
        publishEvent(account, TradingEventType.ORDER_UPDATED, resolveSource(account), saved);
        linkedOrderLifecycleService.onOrderUpdated(account, saved);
        return saved;
    }

    public TradingOrder modifyOrder(
            TradingAccount account,
            String orderId,
            Integer qty,
            Double price,
            Double triggerPrice) {
        TradingOrder current = tradingStorePort.findOrder(account.accountId(), orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
        if (current.status().terminal()) {
            throw new IllegalArgumentException("order is already terminal: " + current.status());
        }

        BrokerGateway brokerGateway = brokerGatewayResolver.resolve(account);
        ModifyOrderResult modifyOrderResult = brokerGateway.modifyOrder(current, qty, price, triggerPrice);
        TradingOrder updated = modifyOrderResult.order();
        orderStateMachineService.ensureTransition(current.status(), updated.status(), orderId);
        TradingOrder saved = tradingStorePort.saveOrder(updated);
        publishEvent(account, TradingEventType.ORDER_UPDATED, resolveSource(account), saved);
        return saved;
    }

    private TradingOrder guardrailsRejectIfNeeded(TradingAccount account, OrderDraft draft) {
        long openOrders = tradingStorePort.countOpenOrders(account.accountId());
        if (openOrders >= tradingProperties.getGuardrails().getMaxOpenOrdersPerAccount()) {
            return rejectDraft(account, draft, "open_order_limit_exceeded");
        }
        long rate = tradingStorePort.incrementOrderRateCounter(account.accountId(), Instant.now());
        if (rate > tradingProperties.getGuardrails().getMaxOrdersPerMinute()) {
            return rejectDraft(account, draft, "order_rate_limit_exceeded");
        }
        return null;
    }

    private TradingOrder rejectDraft(TradingAccount account, OrderDraft draft, String reason) {
        long now = Instant.now().toEpochMilli();
        TradingOrder rejected = new TradingOrder(
                UUID.randomUUID().toString(),
                null,
                account.accountId(),
                draft.instrumentToken(),
                draft.tradingsymbol(),
                draft.exchange(),
                draft.product(),
                draft.orderType(),
                draft.side(),
                draft.qty(),
                draft.price(),
                draft.triggerPrice(),
                TradingOrderStatus.REJECTED,
                0,
                0.0d,
                now,
                now,
                reason,
                TradingEventSource.CORE,
                draft.scriptId(),
                draft.reason(),
                draft.parentOrderId(),
                draft.exitPlanId(),
                draft.triggerId(),
                draft.ocoGroupId(),
                draft.role(),
                draft.reduceOnly());
        tradingStorePort.saveOrder(rejected);
        orderRejects.increment();
        publishEvent(account, TradingEventType.ORDER_REJECTED, TradingEventSource.CORE, rejected);
        return rejected;
    }

    private void validateDraft(OrderDraft draft) {
        if (draft == null) {
            throw new IllegalArgumentException("order draft is required");
        }
        if (draft.instrumentToken() <= 0) {
            throw new IllegalArgumentException("instrumentToken must be positive");
        }
        if (draft.qty() <= 0) {
            throw new IllegalArgumentException("qty must be positive");
        }
        if (draft.orderType() == null) {
            throw new IllegalArgumentException("orderType is required");
        }
        if (draft.orderType() == com.readywealth.trading.trade_engine.execution.domain.TradingOrderType.LIMIT
                && draft.price() == null) {
            throw new IllegalArgumentException("price is required for limit orders");
        }
        if ((draft.orderType() == com.readywealth.trading.trade_engine.execution.domain.TradingOrderType.SL
                || draft.orderType() == com.readywealth.trading.trade_engine.execution.domain.TradingOrderType.SLM)
                && draft.triggerPrice() == null) {
            throw new IllegalArgumentException("triggerPrice is required for stop orders");
        }
        if (draft.exchange() == null || draft.exchange().isBlank()) {
            throw new IllegalArgumentException("exchange is required");
        }
    }

    private TradingEventSource resolveSource(TradingAccount account) {
        return switch (account.brokerType()) {
            case PAPER -> TradingEventSource.PAPER;
            case KITE -> TradingEventSource.KITE;
        };
    }

    private void publishEvent(
            TradingAccount account,
            TradingEventType type,
            TradingEventSource source,
            Object payload) {
        tradingEventPublisherPort.publish(new TradingEventEnvelope(
                UUID.randomUUID().toString(),
                account.accountId(),
                type,
                Instant.now().toEpochMilli(),
                source,
                payload));
    }
}
