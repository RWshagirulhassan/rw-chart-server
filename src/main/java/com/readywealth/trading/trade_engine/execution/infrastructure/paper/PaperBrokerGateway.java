package com.readywealth.trading.trade_engine.execution.infrastructure.paper;

import com.readywealth.trading.trade_engine.execution.application.port.BrokerEventListener;
import com.readywealth.trading.trade_engine.execution.application.port.BrokerGateway;
import com.readywealth.trading.trade_engine.execution.application.port.BrokerStreamSubscription;
import com.readywealth.trading.trade_engine.execution.application.port.CancelOrderResult;
import com.readywealth.trading.trade_engine.execution.application.port.ModifyOrderResult;
import com.readywealth.trading.trade_engine.execution.application.port.PlaceOrderResult;
import com.readywealth.trading.trade_engine.execution.domain.OrderDraft;
import com.readywealth.trading.trade_engine.execution.domain.TradingFundsView;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrder;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderStatus;
import com.readywealth.trading.trade_engine.execution.domain.TradingPosition;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component("paperBrokerGateway")
public class PaperBrokerGateway implements BrokerGateway {

    private final PaperExecutionSimulator paperExecutionSimulator;

    public PaperBrokerGateway(PaperExecutionSimulator paperExecutionSimulator) {
        this.paperExecutionSimulator = paperExecutionSimulator;
    }

    @Override
    public PlaceOrderResult placeOrder(OrderDraft orderDraft, TradingOrder canonicalOrder) {
        long now = Instant.now().toEpochMilli();
        TradingOrder accepted = canonicalOrder.withBrokerOrderId("PAPER-ORD-" + UUID.randomUUID(), now)
                .withStatus(TradingOrderStatus.OPEN, now, null);
        return new PlaceOrderResult(
                accepted,
                paperExecutionSimulator.simulate(orderDraft, accepted),
                "paper_order_accepted");
    }

    @Override
    public CancelOrderResult cancelOrder(TradingOrder currentOrder, String reason) {
        TradingOrder cancelled = currentOrder.withStatus(
                TradingOrderStatus.CANCELLED,
                Instant.now().toEpochMilli(),
                reason == null ? "cancelled" : reason);
        return new CancelOrderResult(cancelled, "paper_order_cancelled");
    }

    @Override
    public ModifyOrderResult modifyOrder(TradingOrder currentOrder, Integer qty, Double price, Double triggerPrice) {
        TradingOrder modified = currentOrder.withMutableFields(qty, price, triggerPrice, Instant.now().toEpochMilli());
        return new ModifyOrderResult(modified, "paper_order_modified");
    }

    @Override
    public List<TradingOrder> getOrders(String accountId) {
        return List.of();
    }

    @Override
    public List<TradingPosition> getPositions(String accountId) {
        return List.of();
    }

    @Override
    public TradingFundsView getFunds(String accountId) {
        return TradingFundsView.opening(0.0d, Instant.now().toEpochMilli());
    }

    @Override
    public BrokerStreamSubscription subscribe(String accountId, BrokerEventListener listener) {
        return () -> {
            // no-op in paper adapter v1
        };
    }
}
