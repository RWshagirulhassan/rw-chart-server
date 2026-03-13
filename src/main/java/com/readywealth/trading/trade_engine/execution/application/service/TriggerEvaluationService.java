package com.readywealth.trading.trade_engine.execution.application.service;

import com.readywealth.trading.trade_engine.execution.application.port.LastPricePort;
import com.readywealth.trading.trade_engine.execution.application.port.TradingEventPublisherPort;
import com.readywealth.trading.trade_engine.execution.application.port.TradingStorePort;
import com.readywealth.trading.trade_engine.execution.application.port.TriggerEnginePort;
import com.readywealth.trading.trade_engine.execution.application.port.TriggerStorePort;
import com.readywealth.trading.trade_engine.execution.domain.ExitPlan;
import com.readywealth.trading.trade_engine.execution.domain.ExitPlanStatus;
import com.readywealth.trading.trade_engine.execution.domain.PriceSnapshot;
import com.readywealth.trading.trade_engine.execution.domain.TradingAccount;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventEnvelope;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventSource;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventType;
import com.readywealth.trading.trade_engine.execution.domain.TradingMode;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrder;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderRole;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderType;
import com.readywealth.trading.trade_engine.execution.domain.TradingPosition;
import com.readywealth.trading.trade_engine.execution.domain.Trigger;
import com.readywealth.trading.trade_engine.execution.domain.TriggerConditionOp;
import com.readywealth.trading.trade_engine.execution.domain.TriggerFireResult;
import com.readywealth.trading.trade_engine.execution.domain.TriggerStatus;
import com.readywealth.trading.trade_engine.execution.infrastructure.config.TradingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TriggerEvaluationService implements TriggerEnginePort {

    private static final Logger log = LoggerFactory.getLogger(TriggerEvaluationService.class);

    private final TradingStorePort tradingStorePort;
    private final TriggerStorePort triggerStorePort;
    private final LastPricePort lastPricePort;
    private final TradeOrchestratorService tradeOrchestratorService;
    private final TradingEventPublisherPort tradingEventPublisherPort;
    private final TradingProperties tradingProperties;

    public TriggerEvaluationService(
            TradingStorePort tradingStorePort,
            TriggerStorePort triggerStorePort,
            LastPricePort lastPricePort,
            TradeOrchestratorService tradeOrchestratorService,
            TradingEventPublisherPort tradingEventPublisherPort,
            TradingProperties tradingProperties) {
        this.tradingStorePort = tradingStorePort;
        this.triggerStorePort = triggerStorePort;
        this.lastPricePort = lastPricePort;
        this.tradeOrchestratorService = tradeOrchestratorService;
        this.tradingEventPublisherPort = tradingEventPublisherPort;
        this.tradingProperties = tradingProperties;
    }

    @Scheduled(fixedDelayString = "${trading.paper.trigger-evaluation-fixed-delay-ms:100}")
    public void evaluatePaperTriggers() {
        if (!tradingProperties.getPaper().isExecutionCycleEnabled()) {
            return;
        }
        try {
            List<TradingAccount> paperAccounts = tradingStorePort.listAccountsByMode(TradingMode.PAPER);
            for (TradingAccount account : paperAccounts) {
                evaluateAccount(account.accountId());
            }
        } catch (Exception ex) {
            log.debug("trigger_evaluation_skipped reason='{}'", ex.getMessage());
        }
    }

    @Override
    public void evaluateAccount(String accountId) {
        TradingAccount account = tradingStorePort.findAccountById(accountId).orElse(null);
        if (account == null || account.mode() != TradingMode.PAPER) {
            return;
        }

        List<Trigger> activeTriggers = triggerStorePort.listActiveTriggers(accountId);
        if (activeTriggers.isEmpty()) {
            return;
        }

        Map<Long, PriceSnapshot> priceCache = new HashMap<>();
        for (Trigger trigger : activeTriggers) {
            PriceSnapshot snapshot = priceCache.computeIfAbsent(trigger.instrumentToken(), token -> lastPricePort
                    .getPriceSnapshot(token)
                    .orElse(null));
            if (snapshot == null) {
                continue;
            }

            if (!isTriggered(trigger.lastObservedLtp(), snapshot.lastPrice(), trigger.conditionOp(), trigger.triggerValue())) {
                triggerStorePort.updateTriggerLastObservedLtp(accountId, trigger.triggerId(), snapshot.lastPrice(),
                        System.currentTimeMillis());
                continue;
            }

            TriggerFireResult fireResult = triggerStorePort.fireTriggerAtomically(
                    accountId,
                    trigger.triggerId(),
                    snapshot.lastPrice(),
                    System.currentTimeMillis());
            if (!fireResult.fired() || fireResult.trigger() == null) {
                continue;
            }

            publish(accountId, TradingEventType.TRIGGER_TRIGGERED, fireResult.trigger());
            if (fireResult.cancelledSibling() != null) {
                publish(accountId, TradingEventType.OCO_LEG_CANCELLED, fireResult.cancelledSibling());
                publish(accountId, TradingEventType.TRIGGER_UPDATED, fireResult.cancelledSibling());
            }

            handleTriggeredOrderPlacement(account, fireResult.trigger(), snapshot.lastPrice());
        }
    }

    private void handleTriggeredOrderPlacement(TradingAccount account, Trigger trigger, double lastPrice) {
        ExitPlan plan = triggerStorePort.findExitPlan(account.accountId(), trigger.exitPlanId()).orElse(null);
        if (plan == null || plan.status() == ExitPlanStatus.CANCELLED || plan.status() == ExitPlanStatus.COMPLETED) {
            triggerStorePort.markTriggerCompleted(account.accountId(), trigger.triggerId(), System.currentTimeMillis());
            return;
        }

        int qty = resolveReduceOnlyQty(account.accountId(), trigger);
        if (qty <= 0) {
            triggerStorePort.markTriggerCompleted(account.accountId(), trigger.triggerId(), System.currentTimeMillis());
            ExitPlan completed = triggerStorePort.saveExitPlan(
                    plan.withStatus(ExitPlanStatus.COMPLETED, null, null, System.currentTimeMillis()));
            publish(account.accountId(), TradingEventType.EXIT_PLAN_COMPLETED, completed);
            return;
        }

        double limitPrice = toLimitPrice(trigger, 0.05d);
        String reason = trigger.legType() == com.readywealth.trading.trade_engine.execution.domain.ExitLegType.TAKE_PROFIT
                ? "exit_trigger_take_profit"
                : "exit_trigger_stop_loss";

        TradingOrderRole role = trigger.legType() == com.readywealth.trading.trade_engine.execution.domain.ExitLegType.TAKE_PROFIT
                ? TradingOrderRole.EXIT_TP
                : TradingOrderRole.EXIT_SL;

        com.readywealth.trading.trade_engine.execution.domain.OrderDraft draft = new com.readywealth.trading.trade_engine.execution.domain.OrderDraft(
                account.accountId(),
                trigger.instrumentToken(),
                trigger.tradingsymbol(),
                trigger.exchange() == null || trigger.exchange().isBlank() ? "NSE" : trigger.exchange(),
                trigger.product(),
                TradingOrderType.LIMIT,
                trigger.orderSide(),
                qty,
                limitPrice,
                null,
                TradingEventSource.PAPER,
                null,
                reason,
                plan.entryOrderId(),
                plan.exitPlanId(),
                trigger.triggerId(),
                trigger.ocoGroupId(),
                role,
                true);

        TradingOrder placed = tradeOrchestratorService.placeOrder(account, draft);
        TriggerStatus nextStatus = placed.status() == com.readywealth.trading.trade_engine.execution.domain.TradingOrderStatus.REJECTED
                ? TriggerStatus.ORDER_REJECTED
                : TriggerStatus.ORDER_PLACED;
        Trigger updatedTrigger = triggerStorePort.markTriggerOrderAttempt(
                account.accountId(),
                trigger.triggerId(),
                placed.orderId(),
                nextStatus,
                placed.rejectionReason(),
                System.currentTimeMillis());
        publish(account.accountId(), TradingEventType.TRIGGER_UPDATED, updatedTrigger);

        if (placed.status() == com.readywealth.trading.trade_engine.execution.domain.TradingOrderStatus.REJECTED) {
            ExitPlan rearm = triggerStorePort.saveExitPlan(plan.withStatus(
                    ExitPlanStatus.REARM_REQUIRED,
                    Math.max(1, qty),
                    "triggered_order_rejected_rearm_required",
                    System.currentTimeMillis()));
            publish(account.accountId(), TradingEventType.EXIT_PLAN_REARM_REQUIRED, rearm);
        }
    }

    private int resolveReduceOnlyQty(String accountId, Trigger trigger) {
        TradingPosition position = tradingStorePort.findPosition(accountId, trigger.instrumentToken(), trigger.product())
                .orElse(null);
        if (position == null) {
            return 0;
        }
        if (trigger.orderSide() == com.readywealth.trading.trade_engine.execution.domain.TradingSide.SELL) {
            if (position.netQty() <= 0) {
                return 0;
            }
            return Math.min(position.netQty(), trigger.plannedQty());
        }
        if (position.netQty() >= 0) {
            return 0;
        }
        return Math.min(-position.netQty(), trigger.plannedQty());
    }

    private double toLimitPrice(Trigger trigger, double tickSize) {
        double raw = trigger.orderSide() == com.readywealth.trading.trade_engine.execution.domain.TradingSide.SELL
                ? trigger.triggerValue() - trigger.limitOffset()
                : trigger.triggerValue() + trigger.limitOffset();
        double bounded = Math.max(tickSize, raw);
        return Math.round(bounded / tickSize) * tickSize;
    }

    private boolean isTriggered(Double previousLtp, double currentLtp, TriggerConditionOp op, double triggerValue) {
        if (op == TriggerConditionOp.GTE) {
            if (previousLtp == null) {
                return currentLtp >= triggerValue;
            }
            return previousLtp < triggerValue && currentLtp >= triggerValue;
        }
        if (previousLtp == null) {
            return currentLtp <= triggerValue;
        }
        return previousLtp > triggerValue && currentLtp <= triggerValue;
    }

    private void publish(String accountId, TradingEventType type, Object payload) {
        tradingEventPublisherPort.publish(new TradingEventEnvelope(
                UUID.randomUUID().toString(),
                accountId,
                type,
                Instant.now().toEpochMilli(),
                TradingEventSource.PAPER,
                payload));
    }
}
