package com.readywealth.trading.trade_engine.execution.application.service;

import com.readywealth.trading.trade_engine.execution.application.port.LastPricePort;
import com.readywealth.trading.trade_engine.execution.application.port.TradingEventPublisherPort;
import com.readywealth.trading.trade_engine.execution.application.port.TradingStorePort;
import com.readywealth.trading.trade_engine.execution.domain.PriceSnapshot;
import com.readywealth.trading.trade_engine.execution.domain.TradingAccount;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventEnvelope;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventSource;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventType;
import com.readywealth.trading.trade_engine.execution.domain.TradingFundsView;
import com.readywealth.trading.trade_engine.execution.domain.TradingMode;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrder;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderStatus;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderType;
import com.readywealth.trading.trade_engine.execution.domain.TradingPosition;
import com.readywealth.trading.trade_engine.execution.domain.TradingProduct;
import com.readywealth.trading.trade_engine.execution.infrastructure.config.TradingProperties;
import com.readywealth.trading.trade_engine.execution.infrastructure.paper.PaperExecutionSimulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class PaperExecutionCycleService {

    private static final Logger log = LoggerFactory.getLogger(PaperExecutionCycleService.class);

    private final TradingStorePort tradingStorePort;
    private final LastPricePort lastPricePort;
    private final PaperExecutionSimulator paperExecutionSimulator;
    private final FillApplicationService fillApplicationService;
    private final TradingEventPublisherPort tradingEventPublisherPort;
    private final TradingProperties tradingProperties;

    public PaperExecutionCycleService(
            TradingStorePort tradingStorePort,
            LastPricePort lastPricePort,
            PaperExecutionSimulator paperExecutionSimulator,
            FillApplicationService fillApplicationService,
            TradingEventPublisherPort tradingEventPublisherPort,
            TradingProperties tradingProperties) {
        this.tradingStorePort = tradingStorePort;
        this.lastPricePort = lastPricePort;
        this.paperExecutionSimulator = paperExecutionSimulator;
        this.fillApplicationService = fillApplicationService;
        this.tradingEventPublisherPort = tradingEventPublisherPort;
        this.tradingProperties = tradingProperties;
    }

    @Scheduled(fixedDelayString = "${trading.paper.execution-cycle-fixed-delay-ms:500}")
    public void runExecutionCycle() {
        if (!tradingProperties.getPaper().isExecutionCycleEnabled()) {
            return;
        }
        try {
            List<TradingAccount> accounts = tradingStorePort.listAccountsByMode(TradingMode.PAPER);
            for (TradingAccount account : accounts) {
                Map<Long, Optional<PriceSnapshot>> priceCache = new HashMap<>();
                applyPendingLimitOrderFills(account, priceCache);
                refreshUnrealizedPnl(account, priceCache);
            }
        } catch (Exception ex) {
            log.debug("paper_execution_cycle_skipped reason='{}'", ex.getMessage());
        }
    }

    private void applyPendingLimitOrderFills(
            TradingAccount account,
            Map<Long, Optional<PriceSnapshot>> priceCache) {
        List<TradingOrder> openOrders = tradingStorePort.listOrders(account.accountId(), TradingOrderStatus.OPEN);
        for (TradingOrder openOrder : openOrders) {
            if (openOrder.orderType() != TradingOrderType.LIMIT) {
                continue;
            }
            TradingOrder latestOrder = tradingStorePort.findOrder(account.accountId(), openOrder.orderId()).orElse(openOrder);
            if (latestOrder.status().terminal()) {
                continue;
            }
            PriceSnapshot snapshot = resolveSnapshot(latestOrder.instrumentToken(), priceCache).orElse(null);
            if (snapshot == null) {
                continue;
            }
            paperExecutionSimulator.simulateOpenLimitOrder(latestOrder, snapshot)
                    .ifPresent(simulated -> fillApplicationService.applyFill(
                            account,
                            latestOrder,
                            simulated.fill(),
                            simulated.markPrice()));
        }
    }

    private void refreshUnrealizedPnl(
            TradingAccount account,
            Map<Long, Optional<PriceSnapshot>> priceCache) {
        List<TradingPosition> positions = tradingStorePort.listPositions(account.accountId());
        TradingFundsView currentFunds = tradingStorePort.findFunds(account.accountId())
                .orElse(TradingFundsView.opening(account.openingBalance(), System.currentTimeMillis()));
        long now = System.currentTimeMillis();
        double epsilon = Math.max(0.0d, tradingProperties.getPaper().getPnlEpsilon());

        double utilisedMargin = 0.0d;
        for (TradingPosition position : positions) {
            if (position.netQty() == 0) {
                continue;
            }
            PriceSnapshot snapshot = resolveSnapshot(position.instrumentToken(), priceCache).orElse(null);
            if (snapshot == null) {
                continue;
            }
            double mark = snapshot.lastPrice();
            double unrealized = (mark - position.avgPrice()) * position.netQty();
            double positionMargin = Math.abs(position.netQty()) * mark * marginRatio(position.product());
            utilisedMargin += positionMargin;

            if (differs(position.lastPrice(), mark, epsilon)
                    || differs(position.unrealizedPnl(), unrealized, epsilon)) {
                TradingPosition updated = new TradingPosition(
                        position.accountId(),
                        position.instrumentToken(),
                        position.tradingsymbol(),
                        position.product(),
                        position.netQty(),
                        position.avgPrice(),
                        position.realizedPnl(),
                        unrealized,
                        mark,
                        now);
                tradingStorePort.savePosition(updated);
                publish(account, TradingEventType.POSITION_UPDATED, updated);
            }
        }

        double availableCash = currentFunds.availableCash();
        double availableMargin = availableCash - utilisedMargin;
        if (differs(currentFunds.utilisedMargin(), utilisedMargin, epsilon)
                || differs(currentFunds.availableMargin(), availableMargin, epsilon)) {
            TradingFundsView updatedFunds = new TradingFundsView(
                    availableCash,
                    utilisedMargin,
                    availableMargin,
                    currentFunds.openingBalance(),
                    currentFunds.collateral(),
                    now);
            tradingStorePort.saveFunds(account.accountId(), updatedFunds);
            publish(account, TradingEventType.FUNDS_UPDATED, updatedFunds);
        }
    }

    private Optional<PriceSnapshot> resolveSnapshot(
            long instrumentToken,
            Map<Long, Optional<PriceSnapshot>> priceCache) {
        return priceCache.computeIfAbsent(instrumentToken, lastPricePort::getPriceSnapshot);
    }

    private void publish(TradingAccount account, TradingEventType type, Object payload) {
        tradingEventPublisherPort.publish(new TradingEventEnvelope(
                UUID.randomUUID().toString(),
                account.accountId(),
                type,
                System.currentTimeMillis(),
                TradingEventSource.PAPER,
                payload));
    }

    private double marginRatio(TradingProduct product) {
        return switch (product) {
            case MIS -> tradingProperties.getMargin().getMisPercent();
            case NRML -> tradingProperties.getMargin().getNrmlPercent();
            case CNC -> 1.0d;
        };
    }

    private boolean differs(double left, double right, double epsilon) {
        return Math.abs(left - right) > epsilon;
    }
}
