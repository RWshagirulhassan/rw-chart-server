package com.readywealth.trading.trade_engine.execution.infrastructure.paper;

import com.readywealth.trading.trade_engine.execution.application.port.LastPricePort;
import com.readywealth.trading.trade_engine.execution.domain.OrderDraft;
import com.readywealth.trading.trade_engine.execution.domain.PriceSnapshot;
import com.readywealth.trading.trade_engine.execution.domain.TradingFill;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrder;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderStatus;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderType;
import com.readywealth.trading.trade_engine.execution.domain.TradingSide;
import com.readywealth.trading.trade_engine.execution.infrastructure.config.TradingProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PaperExecutionSimulator {

    private final LastPricePort lastPricePort;
    private final TradingProperties tradingProperties;

    public PaperExecutionSimulator(LastPricePort lastPricePort, TradingProperties tradingProperties) {
        this.lastPricePort = lastPricePort;
        this.tradingProperties = tradingProperties;
    }

    public List<TradingFill> simulate(OrderDraft orderDraft, TradingOrder canonicalOrder) {
        Optional<PriceSnapshot> snapshotOpt = lastPricePort.getPriceSnapshot(orderDraft.instrumentToken());
        if (snapshotOpt.isEmpty()) {
            return List.of();
        }
        PriceSnapshot snapshot = snapshotOpt.get();
        long now = Instant.now().toEpochMilli();

        if (orderDraft.orderType() == TradingOrderType.MARKET) {
            double base = snapshot.tickBased() ? snapshot.lastPrice() : snapshot.openPrice();
            double slip = tradingProperties.getPaper().getSlippageBps() / 10000.0d;
            double fillPrice = orderDraft.side() == TradingSide.BUY
                    ? base * (1.0d + slip)
                    : base * (1.0d - slip);
            double fees = (orderDraft.qty() * fillPrice) * (tradingProperties.getPaper().getFeeBps() / 10000.0d);
            return List.of(new TradingFill(
                    UUID.randomUUID().toString(),
                    canonicalOrder.orderId(),
                    "PAPER-TRD-" + UUID.randomUUID(),
                    orderDraft.qty(),
                    fillPrice,
                    fees,
                    now));
        }

        return List.of();
    }

    public Optional<SimulatedFill> simulateOpenLimitOrder(TradingOrder order, PriceSnapshot snapshot) {
        if (order == null || snapshot == null) {
            return Optional.empty();
        }
        if (order.status() == TradingOrderStatus.REJECTED
                || order.status() == TradingOrderStatus.CANCELLED
                || order.status() == TradingOrderStatus.FILLED) {
            return Optional.empty();
        }
        if (order.orderType() != TradingOrderType.LIMIT || order.price() == null) {
            return Optional.empty();
        }
        int remainingQty = order.qty() - order.filledQty();
        if (remainingQty <= 0) {
            return Optional.empty();
        }

        // Ignore stale snapshots that were already known when the order was accepted/modified.
        if (snapshot.asOfEpochMs() <= order.updatedAtEpochMs()) {
            return Optional.empty();
        }

        boolean touched = order.side() == TradingSide.BUY
                ? snapshot.lastPrice() <= order.price()
                : snapshot.lastPrice() >= order.price();
        if (!touched) {
            return Optional.empty();
        }

        long now = Instant.now().toEpochMilli();
        double fillPrice = order.price();
        double fees = (remainingQty * fillPrice) * (tradingProperties.getPaper().getFeeBps() / 10000.0d);
        TradingFill fill = new TradingFill(
                UUID.randomUUID().toString(),
                order.orderId(),
                "PAPER-TRD-" + UUID.randomUUID(),
                remainingQty,
                fillPrice,
                fees,
                now);
        return Optional.of(new SimulatedFill(fill, snapshot.lastPrice()));
    }

    public record SimulatedFill(
            TradingFill fill,
            double markPrice) {
    }
}
