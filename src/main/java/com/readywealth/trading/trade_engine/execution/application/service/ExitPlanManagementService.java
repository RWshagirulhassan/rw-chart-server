package com.readywealth.trading.trade_engine.execution.application.service;

import com.readywealth.trading.trade_engine.execution.application.port.TradingEventPublisherPort;
import com.readywealth.trading.trade_engine.execution.application.port.TradingStorePort;
import com.readywealth.trading.trade_engine.execution.application.port.TriggerStorePort;
import com.readywealth.trading.trade_engine.execution.domain.ArmPolicy;
import com.readywealth.trading.trade_engine.execution.domain.ExitLegSpec;
import com.readywealth.trading.trade_engine.execution.domain.ExitLegType;
import com.readywealth.trading.trade_engine.execution.domain.ExitPlan;
import com.readywealth.trading.trade_engine.execution.domain.ExitPlanMode;
import com.readywealth.trading.trade_engine.execution.domain.ExitPlanStatus;
import com.readywealth.trading.trade_engine.execution.domain.OcoGroup;
import com.readywealth.trading.trade_engine.execution.domain.OcoGroupStatus;
import com.readywealth.trading.trade_engine.execution.domain.TradingAccount;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventEnvelope;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventSource;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventType;
import com.readywealth.trading.trade_engine.execution.domain.TradingPosition;
import com.readywealth.trading.trade_engine.execution.domain.TradingProduct;
import com.readywealth.trading.trade_engine.execution.domain.TradingSide;
import com.readywealth.trading.trade_engine.execution.domain.Trigger;
import com.readywealth.trading.trade_engine.execution.domain.TriggerConditionOp;
import com.readywealth.trading.trade_engine.execution.domain.TriggerStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ExitPlanManagementService {

    private final TriggerStorePort triggerStorePort;
    private final TradingStorePort tradingStorePort;
    private final TradingEventPublisherPort tradingEventPublisherPort;

    public ExitPlanManagementService(
            TriggerStorePort triggerStorePort,
            TradingStorePort tradingStorePort,
            TradingEventPublisherPort tradingEventPublisherPort) {
        this.triggerStorePort = triggerStorePort;
        this.tradingStorePort = tradingStorePort;
        this.tradingEventPublisherPort = tradingEventPublisherPort;
    }

    public ExitPlan createForPosition(
            TradingAccount account,
            long instrumentToken,
            TradingProduct product,
            ArmPolicy armPolicy,
            Double tpTrigger,
            Double tpOffset,
            Double slTrigger,
            Double slOffset) {
        TradingPosition position = tradingStorePort.findPosition(account.accountId(), instrumentToken, product)
                .orElseThrow(() -> new IllegalArgumentException("position not found for instrument/product"));

        int plannedQty = Math.abs(position.netQty());
        if (plannedQty <= 0) {
            throw new IllegalArgumentException("no open qty found for position");
        }

        TradingSide entrySide = position.netQty() > 0 ? TradingSide.BUY : TradingSide.SELL;
        TradingSide exitSide = entrySide == TradingSide.BUY ? TradingSide.SELL : TradingSide.BUY;

        ExitLegSpec tp = tpTrigger == null ? null : new ExitLegSpec(
                ExitLegType.TAKE_PROFIT,
                tpTrigger,
                tpOffset == null ? 0.0d : Math.max(0.0d, tpOffset),
                exitSide,
                plannedQty);
        ExitLegSpec sl = slTrigger == null ? null : new ExitLegSpec(
                ExitLegType.STOP_LOSS,
                slTrigger,
                slOffset == null ? 0.0d : Math.max(0.0d, slOffset),
                exitSide,
                plannedQty);

        if (tp == null && sl == null) {
            throw new IllegalArgumentException("at least one of takeProfit or stopLoss is required");
        }

        long now = Instant.now().toEpochMilli();
        ExitPlanMode mode = tp != null && sl != null ? ExitPlanMode.OCO : tp != null ? ExitPlanMode.TP_ONLY : ExitPlanMode.SL_ONLY;

        ExitPlan plan = new ExitPlan(
                UUID.randomUUID().toString(),
                account.accountId(),
                null,
                instrumentToken,
                position.tradingsymbol(),
                "NSE",
                product,
                entrySide,
                plannedQty,
                ExitPlanStatus.ACTIVE,
                armPolicy == null ? ArmPolicy.AFTER_ENTRY_FILLED : armPolicy,
                mode,
                null,
                tp,
                sl,
                null,
                null,
                now,
                now);
        plan = triggerStorePort.saveExitPlan(plan);

        List<Trigger> triggers = buildTriggers(plan, now);
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
            plan = triggerStorePort.saveExitPlan(plan.withOcoGroupId(ocoGroup.ocoGroupId(), now));
            String ocoGroupId = ocoGroup.ocoGroupId();
            triggers = triggers.stream().map(item -> new Trigger(
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
                    item.updatedAtEpochMs())).toList();
        }

        triggerStorePort.saveTriggers(triggers);
        publish(account.accountId(), TradingEventType.EXIT_PLAN_ARMED, plan);
        return plan;
    }

    public ExitPlan updatePlan(TradingAccount account, String exitPlanId, Double tpTrigger, Double tpOffset, Double slTrigger,
            Double slOffset) {
        ExitPlan plan = triggerStorePort.findExitPlan(account.accountId(), exitPlanId)
                .orElseThrow(() -> new IllegalArgumentException("exit plan not found: " + exitPlanId));

        if (!(plan.status() == ExitPlanStatus.PENDING || plan.status() == ExitPlanStatus.ACTIVE)) {
            throw new IllegalArgumentException("only PENDING or ACTIVE exit plans can be modified");
        }

        List<Trigger> allTriggers = triggerStorePort.listTriggers(account.accountId(), null).stream()
                .filter(item -> exitPlanId.equals(item.exitPlanId()))
                .toList();

        boolean hasFired = allTriggers.stream().anyMatch(item -> item.status() != TriggerStatus.ACTIVE && item.status() != TriggerStatus.CANCELLED);
        if (hasFired) {
            throw new IllegalArgumentException("exit plan cannot be modified after trigger is fired");
        }

        TradingSide exitSide = plan.entrySide() == TradingSide.BUY ? TradingSide.SELL : TradingSide.BUY;
        ExitLegSpec tp = tpTrigger == null ? null : new ExitLegSpec(
                ExitLegType.TAKE_PROFIT,
                tpTrigger,
                tpOffset == null ? 0.0d : Math.max(0.0d, tpOffset),
                exitSide,
                plan.plannedQty());
        ExitLegSpec sl = slTrigger == null ? null : new ExitLegSpec(
                ExitLegType.STOP_LOSS,
                slTrigger,
                slOffset == null ? 0.0d : Math.max(0.0d, slOffset),
                exitSide,
                plan.plannedQty());

        if (tp == null && sl == null) {
            throw new IllegalArgumentException("at least one of takeProfit or stopLoss is required");
        }

        long now = System.currentTimeMillis();
        ExitPlanMode mode = tp != null && sl != null ? ExitPlanMode.OCO : tp != null ? ExitPlanMode.TP_ONLY : ExitPlanMode.SL_ONLY;
        ExitPlan updated = plan.withLegs(tp, sl, mode, now);
        updated = triggerStorePort.saveExitPlan(updated);

        if (plan.status() == ExitPlanStatus.ACTIVE) {
            for (Trigger trigger : allTriggers) {
                if (trigger.status() == TriggerStatus.ACTIVE) {
                    triggerStorePort.saveTrigger(trigger.withStatus(TriggerStatus.CANCELLED, trigger.firedAtEpochMs(),
                            "plan_modified", now));
                }
            }
            List<Trigger> newTriggers = buildTriggers(updated, now);
            OcoGroup ocoGroup = null;
            if (newTriggers.size() == 2) {
                ocoGroup = new OcoGroup(
                        UUID.randomUUID().toString(),
                        account.accountId(),
                        updated.exitPlanId(),
                        newTriggers.get(0).triggerId(),
                        newTriggers.get(1).triggerId(),
                        null,
                        OcoGroupStatus.ACTIVE,
                        now,
                        now);
                triggerStorePort.saveOcoGroup(ocoGroup);
                updated = triggerStorePort.saveExitPlan(updated.withOcoGroupId(ocoGroup.ocoGroupId(), now));
                String ocoGroupId = ocoGroup.ocoGroupId();
                newTriggers = newTriggers.stream().map(item -> new Trigger(
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
                        item.updatedAtEpochMs())).toList();
            } else {
                updated = triggerStorePort.saveExitPlan(updated.withOcoGroupId(null, now));
            }
            triggerStorePort.saveTriggers(newTriggers);
        }

        publish(account.accountId(), TradingEventType.TRIGGER_UPDATED, updated);
        return updated;
    }

    public ExitPlan cancelPlan(TradingAccount account, String exitPlanId, String reason) {
        long now = System.currentTimeMillis();
        triggerStorePort.cancelExitPlan(account.accountId(), exitPlanId, now, reason == null ? "cancelled" : reason);
        ExitPlan cancelled = triggerStorePort.findExitPlan(account.accountId(), exitPlanId)
                .orElseThrow(() -> new IllegalStateException("exit plan missing after cancellation"));
        publish(account.accountId(), TradingEventType.TRIGGER_UPDATED, cancelled);
        return cancelled;
    }

    private List<Trigger> buildTriggers(ExitPlan plan, long now) {
        List<Trigger> out = new ArrayList<>();
        if (plan.takeProfit() != null) {
            out.add(buildTrigger(plan, plan.takeProfit(), ExitLegType.TAKE_PROFIT, now));
        }
        if (plan.stopLoss() != null) {
            out.add(buildTrigger(plan, plan.stopLoss(), ExitLegType.STOP_LOSS, now));
        }
        return out;
    }

    private Trigger buildTrigger(ExitPlan plan, ExitLegSpec leg, ExitLegType type, long now) {
        TriggerConditionOp op;
        if (plan.entrySide() == TradingSide.BUY) {
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
                plan.ocoGroupId(),
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
