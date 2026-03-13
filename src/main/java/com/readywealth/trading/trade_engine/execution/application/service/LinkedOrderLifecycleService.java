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
import com.readywealth.trading.trade_engine.execution.domain.TradingOrder;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderRole;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderStatus;
import com.readywealth.trading.trade_engine.execution.domain.TradingPosition;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class LinkedOrderLifecycleService {

    private final ExitPlanArmingService exitPlanArmingService;
    private final TriggerStorePort triggerStorePort;
    private final TradingStorePort tradingStorePort;
    private final TradingEventPublisherPort tradingEventPublisherPort;

    public LinkedOrderLifecycleService(
            ExitPlanArmingService exitPlanArmingService,
            TriggerStorePort triggerStorePort,
            TradingStorePort tradingStorePort,
            TradingEventPublisherPort tradingEventPublisherPort) {
        this.exitPlanArmingService = exitPlanArmingService;
        this.triggerStorePort = triggerStorePort;
        this.tradingStorePort = tradingStorePort;
        this.tradingEventPublisherPort = tradingEventPublisherPort;
    }

    public void onOrderUpdated(TradingAccount account, TradingOrder order) {
        if (order == null) {
            return;
        }
        if (isEntryOrder(order) && order.status() == TradingOrderStatus.FILLED) {
            exitPlanArmingService.armPendingPlansForFilledEntry(account, order);
            return;
        }

        if (isEntryOrder(order)
                && order.status().terminal()
                && order.status() != TradingOrderStatus.FILLED) {
            cancelPendingPlansForEntry(account.accountId(), order.orderId());
            return;
        }

        if (isEntryOrder(order) || order.triggerId() == null
                || order.triggerId().isBlank()) {
            return;
        }

        if (!order.status().terminal()) {
            return;
        }

        long now = System.currentTimeMillis();
        triggerStorePort.markTriggerCompleted(account.accountId(), order.triggerId(), now);

        if (order.exitPlanId() == null || order.exitPlanId().isBlank()) {
            return;
        }

        ExitPlan plan = triggerStorePort.findExitPlan(account.accountId(), order.exitPlanId()).orElse(null);
        if (plan == null || plan.status() == ExitPlanStatus.CANCELLED || plan.status() == ExitPlanStatus.COMPLETED) {
            return;
        }

        if (order.status() == TradingOrderStatus.FILLED) {
            int remainingQty = remainingOpenQty(account.accountId(), plan);
            if (remainingQty <= 0) {
                ExitPlan completed = triggerStorePort.saveExitPlan(plan.withStatus(ExitPlanStatus.COMPLETED, null, null, now));
                publish(account.accountId(), TradingEventType.EXIT_PLAN_COMPLETED, completed);
            } else {
                ExitPlan rearm = triggerStorePort.saveExitPlan(plan.withStatus(
                        ExitPlanStatus.REARM_REQUIRED,
                        remainingQty,
                        "position_not_fully_closed_rearm_required",
                        now));
                publish(account.accountId(), TradingEventType.EXIT_PLAN_REARM_REQUIRED, rearm);
            }
            return;
        }

        if (order.status() == TradingOrderStatus.REJECTED) {
            ExitPlan rearm = triggerStorePort.saveExitPlan(plan.withStatus(
                    ExitPlanStatus.REARM_REQUIRED,
                    Math.max(1, remainingOpenQty(account.accountId(), plan)),
                    "triggered_order_rejected_rearm_required",
                    now));
            publish(account.accountId(), TradingEventType.EXIT_PLAN_REARM_REQUIRED, rearm);
        }
    }

    private boolean isEntryOrder(TradingOrder order) {
        return order.role() == null || order.role() == TradingOrderRole.ENTRY;
    }

    private void cancelPendingPlansForEntry(String accountId, String entryOrderId) {
        long now = System.currentTimeMillis();
        for (ExitPlan pending : triggerStorePort.findPendingExitPlansByEntryOrder(accountId, entryOrderId)) {
            triggerStorePort.cancelExitPlan(accountId, pending.exitPlanId(), now, "entry_not_filled");
            triggerStorePort.findExitPlan(accountId, pending.exitPlanId())
                    .ifPresent(cancelled -> publish(accountId, TradingEventType.TRIGGER_UPDATED, cancelled));
        }
    }

    private int remainingOpenQty(String accountId, ExitPlan plan) {
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
