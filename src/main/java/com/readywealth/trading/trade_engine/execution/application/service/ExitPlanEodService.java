package com.readywealth.trading.trade_engine.execution.application.service;

import com.readywealth.trading.trade_engine.execution.application.port.TradingEventPublisherPort;
import com.readywealth.trading.trade_engine.execution.application.port.TradingStorePort;
import com.readywealth.trading.trade_engine.execution.application.port.TriggerStorePort;
import com.readywealth.trading.trade_engine.execution.domain.ExitPlan;
import com.readywealth.trading.trade_engine.execution.domain.ExitPlanStatus;
import com.readywealth.trading.trade_engine.execution.domain.TradingAccount;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventEnvelope;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventSource;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventType;
import com.readywealth.trading.trade_engine.execution.domain.TradingMode;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrder;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderStatus;
import com.readywealth.trading.trade_engine.execution.domain.TradingPosition;
import com.readywealth.trading.trade_engine.execution.domain.Trigger;
import com.readywealth.trading.trade_engine.execution.domain.TriggerStatus;
import com.readywealth.trading.trade_engine.execution.infrastructure.config.TradingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ExitPlanEodService {

    private static final Logger log = LoggerFactory.getLogger(ExitPlanEodService.class);
    private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");

    private final TradingStorePort tradingStorePort;
    private final TriggerStorePort triggerStorePort;
    private final TradeOrchestratorService tradeOrchestratorService;
    private final TradingEventPublisherPort tradingEventPublisherPort;
    private final TradingProperties tradingProperties;

    private final AtomicReference<LocalDate> lastSweepDate = new AtomicReference<>();

    public ExitPlanEodService(
            TradingStorePort tradingStorePort,
            TriggerStorePort triggerStorePort,
            TradeOrchestratorService tradeOrchestratorService,
            TradingEventPublisherPort tradingEventPublisherPort,
            TradingProperties tradingProperties) {
        this.tradingStorePort = tradingStorePort;
        this.triggerStorePort = triggerStorePort;
        this.tradeOrchestratorService = tradeOrchestratorService;
        this.tradingEventPublisherPort = tradingEventPublisherPort;
        this.tradingProperties = tradingProperties;
    }

    @Scheduled(fixedDelayString = "${trading.paper.eod-sweep-fixed-delay-ms:15000}")
    public void runEodSweep() {
        if (!tradingProperties.getPaper().isEodSweepEnabled()) {
            return;
        }

        LocalDate today = LocalDate.now(INDIA_ZONE);
        LocalDate already = lastSweepDate.get();
        if (today.equals(already)) {
            return;
        }

        LocalTime cutoff;
        try {
            cutoff = LocalTime.parse(tradingProperties.getPaper().getEodCutoffTime());
        } catch (Exception ex) {
            cutoff = LocalTime.of(15, 29, 30);
        }

        if (LocalTime.now(INDIA_ZONE).isBefore(cutoff)) {
            return;
        }

        try {
            List<TradingAccount> paperAccounts = tradingStorePort.listAccountsByMode(TradingMode.PAPER);
            for (TradingAccount account : paperAccounts) {
                sweepAccount(account);
            }
            lastSweepDate.set(today);
        } catch (Exception ex) {
            log.debug("exit_plan_eod_sweep_failed reason='{}'", ex.getMessage());
        }
    }

    private void sweepAccount(TradingAccount account) {
        List<Trigger> orderPlaced = triggerStorePort.listTriggers(account.accountId(), TriggerStatus.ORDER_PLACED);
        for (Trigger trigger : orderPlaced) {
            if (trigger.linkedOrderId() == null || trigger.linkedOrderId().isBlank()) {
                continue;
            }
            TradingOrder order = tradingStorePort.findOrder(account.accountId(), trigger.linkedOrderId()).orElse(null);
            if (order == null) {
                continue;
            }
            if (order.status().terminal()) {
                continue;
            }

            TradingOrder cancelled = tradeOrchestratorService.cancelOrder(
                    account,
                    order.orderId(),
                    "paper_eod_cancel");

            ExitPlan plan = triggerStorePort.findExitPlan(account.accountId(), trigger.exitPlanId()).orElse(null);
            if (plan == null) {
                continue;
            }

            int remaining = remainingQty(account.accountId(), plan);
            if (remaining > 0) {
                triggerStorePort.markExitPlanRearmRequired(
                        account.accountId(),
                        plan.exitPlanId(),
                        remaining,
                        "triggered_exit_order_cancelled_at_eod_rearm_required",
                        System.currentTimeMillis());
                ExitPlan updated = triggerStorePort.findExitPlan(account.accountId(), plan.exitPlanId()).orElse(plan);
                publish(account.accountId(), TradingEventType.EXIT_PLAN_REARM_REQUIRED, updated);
            } else {
                ExitPlan completed = triggerStorePort.saveExitPlan(plan.withStatus(
                        ExitPlanStatus.COMPLETED,
                        null,
                        null,
                        System.currentTimeMillis()));
                publish(account.accountId(), TradingEventType.EXIT_PLAN_COMPLETED, completed);
            }

            publish(account.accountId(), TradingEventType.ORDER_UPDATED, cancelled);
        }
    }

    private int remainingQty(String accountId, ExitPlan plan) {
        TradingPosition position = tradingStorePort.findPosition(accountId, plan.instrumentToken(), plan.product())
                .orElse(null);
        if (position == null) {
            return 0;
        }
        if (plan.entrySide() == com.readywealth.trading.trade_engine.execution.domain.TradingSide.BUY) {
            return Math.max(0, position.netQty());
        }
        return Math.max(0, -position.netQty());
    }

    private void publish(String accountId, TradingEventType type, Object payload) {
        tradingEventPublisherPort.publish(new TradingEventEnvelope(
                UUID.randomUUID().toString(),
                accountId,
                type,
                System.currentTimeMillis(),
                TradingEventSource.PAPER,
                payload));
    }
}
