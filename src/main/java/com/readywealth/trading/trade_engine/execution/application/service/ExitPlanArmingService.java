package com.readywealth.trading.trade_engine.execution.application.service;

import com.readywealth.trading.trade_engine.execution.application.port.TradingEventPublisherPort;
import com.readywealth.trading.trade_engine.execution.application.port.TriggerStorePort;
import com.readywealth.trading.trade_engine.execution.domain.ExitLegSpec;
import com.readywealth.trading.trade_engine.execution.domain.ExitLegType;
import com.readywealth.trading.trade_engine.execution.domain.ExitPlan;
import com.readywealth.trading.trade_engine.execution.domain.ExitPlanStatus;
import com.readywealth.trading.trade_engine.execution.domain.OcoGroup;
import com.readywealth.trading.trade_engine.execution.domain.OcoGroupStatus;
import com.readywealth.trading.trade_engine.execution.domain.TradingAccount;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventEnvelope;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventSource;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventType;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrder;
import com.readywealth.trading.trade_engine.execution.domain.Trigger;
import com.readywealth.trading.trade_engine.execution.domain.TriggerConditionOp;
import com.readywealth.trading.trade_engine.execution.domain.TriggerStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ExitPlanArmingService {

    private final TriggerStorePort triggerStorePort;
    private final TradingEventPublisherPort tradingEventPublisherPort;

    public ExitPlanArmingService(
            TriggerStorePort triggerStorePort,
            TradingEventPublisherPort tradingEventPublisherPort) {
        this.triggerStorePort = triggerStorePort;
        this.tradingEventPublisherPort = tradingEventPublisherPort;
    }

    public void armPendingPlansForFilledEntry(TradingAccount account, TradingOrder entryOrder) {
        if (entryOrder == null || entryOrder.status() != com.readywealth.trading.trade_engine.execution.domain.TradingOrderStatus.FILLED) {
            return;
        }

        List<ExitPlan> pending = triggerStorePort.findPendingExitPlansByEntryOrder(account.accountId(), entryOrder.orderId());
        if (pending.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        for (ExitPlan plan : pending) {
            List<Trigger> triggers = new ArrayList<>();
            if (plan.takeProfit() != null) {
                triggers.add(buildTrigger(plan, plan.takeProfit(), ExitLegType.TAKE_PROFIT, now));
            }
            if (plan.stopLoss() != null) {
                triggers.add(buildTrigger(plan, plan.stopLoss(), ExitLegType.STOP_LOSS, now));
            }

            OcoGroup ocoGroup = null;
            if (triggers.size() == 2) {
                ocoGroup = new OcoGroup(
                        UUID.randomUUID().toString(),
                        account.accountId(),
                        plan.exitPlanId(),
                        triggers.get(0).triggerId(),
                        triggers.get(1).triggerId(),
                        null,
                        OcoGroupStatus.ACTIVE,
                        now,
                        now);
                triggerStorePort.saveOcoGroup(ocoGroup);
                String ocoGroupId = ocoGroup.ocoGroupId();

                triggers = triggers.stream()
                        .map(item -> new Trigger(
                                item.triggerId(),
                                item.accountId(),
                                item.exitPlanId(),
                                item.instrumentToken(),
                                item.tradingsymbol(),
                                item.exchange(),
                                item.product(),
                                item.legType(),
                                item.conditionOp(),
                                item.triggerValue(),
                                item.limitOffset(),
                                item.orderSide(),
                                item.plannedQty(),
                                item.status(),
                                item.lastObservedLtp(),
                                item.firedAtEpochMs(),
                                item.brokerTriggerId(),
                                ocoGroupId,
                                item.linkedOrderId(),
                                item.rejectionReason(),
                                item.createdAtEpochMs(),
                                item.updatedAtEpochMs()))
                        .toList();
            }

            triggerStorePort.saveTriggers(triggers);

            ExitPlan active = plan.withStatus(ExitPlanStatus.ACTIVE, null, null, now);
            if (ocoGroup != null) {
                active = active.withOcoGroupId(ocoGroup.ocoGroupId(), now);
            }
            triggerStorePort.saveExitPlan(active);

            publish(account.accountId(), TradingEventType.EXIT_PLAN_ARMED, active);
            for (Trigger trigger : triggers) {
                publish(account.accountId(), TradingEventType.TRIGGER_UPDATED, trigger);
            }
        }
    }

    private Trigger buildTrigger(ExitPlan plan, ExitLegSpec leg, ExitLegType type, long now) {
        TriggerConditionOp op;
        if (plan.entrySide() == com.readywealth.trading.trade_engine.execution.domain.TradingSide.BUY) {
            op = type == ExitLegType.TAKE_PROFIT ? TriggerConditionOp.GTE : TriggerConditionOp.LTE;
        } else {
            op = type == ExitLegType.TAKE_PROFIT ? TriggerConditionOp.LTE : TriggerConditionOp.GTE;
        }

        return new Trigger(
                UUID.randomUUID().toString(),
                plan.accountId(),
                plan.exitPlanId(),
                plan.instrumentToken(),
                plan.tradingsymbol(),
                plan.exchange(),
                plan.product(),
                type,
                op,
                leg.triggerPrice(),
                Math.max(0.0d, leg.limitOffset()),
                leg.orderSide(),
                leg.qty(),
                TriggerStatus.ACTIVE,
                null,
                null,
                null,
                null,
                null,
                null,
                now,
                now);
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
