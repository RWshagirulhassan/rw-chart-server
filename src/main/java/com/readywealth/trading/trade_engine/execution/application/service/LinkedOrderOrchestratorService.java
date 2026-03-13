package com.readywealth.trading.trade_engine.execution.application.service;

import com.readywealth.trading.trade_engine.execution.application.port.ExitPlanOrchestratorPort;
import com.readywealth.trading.trade_engine.execution.application.port.LastPricePort;
import com.readywealth.trading.trade_engine.execution.application.port.TradingEventPublisherPort;
import com.readywealth.trading.trade_engine.execution.application.port.TriggerStorePort;
import com.readywealth.trading.trade_engine.execution.domain.ArmPolicy;
import com.readywealth.trading.trade_engine.execution.domain.ExitLegSpec;
import com.readywealth.trading.trade_engine.execution.domain.ExitLegType;
import com.readywealth.trading.trade_engine.execution.domain.ExitPlan;
import com.readywealth.trading.trade_engine.execution.domain.ExitPlanMode;
import com.readywealth.trading.trade_engine.execution.domain.ExitPlanStatus;
import com.readywealth.trading.trade_engine.execution.domain.LinkedExitSummary;
import com.readywealth.trading.trade_engine.execution.domain.OrderAttachments;
import com.readywealth.trading.trade_engine.execution.domain.OrderDraft;
import com.readywealth.trading.trade_engine.execution.domain.TradingAccount;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventEnvelope;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventSource;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventType;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrder;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderStatus;
import com.readywealth.trading.trade_engine.execution.domain.TradingSide;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class LinkedOrderOrchestratorService implements ExitPlanOrchestratorPort {

    private final TradeOrchestratorService tradeOrchestratorService;
    private final TriggerStorePort triggerStorePort;
    private final LastPricePort lastPricePort;
    private final TradingEventPublisherPort tradingEventPublisherPort;
    private final ExitPlanArmingService exitPlanArmingService;

    public LinkedOrderOrchestratorService(
            TradeOrchestratorService tradeOrchestratorService,
            TriggerStorePort triggerStorePort,
            LastPricePort lastPricePort,
            TradingEventPublisherPort tradingEventPublisherPort,
            ExitPlanArmingService exitPlanArmingService) {
        this.tradeOrchestratorService = tradeOrchestratorService;
        this.triggerStorePort = triggerStorePort;
        this.lastPricePort = lastPricePort;
        this.tradingEventPublisherPort = tradingEventPublisherPort;
        this.exitPlanArmingService = exitPlanArmingService;
    }

    @Override
    public PlaceOrderWithLinkedExitResult placeOrderWithLinkedExit(
            TradingAccount account,
            OrderDraft entryDraft,
            OrderAttachments attachments) {
        TradingOrder order = tradeOrchestratorService.placeOrder(account, entryDraft);
        if (attachments == null || attachments.empty()) {
            return new PlaceOrderWithLinkedExitResult(order, null);
        }

        if (entryDraft.scriptId() != null && !entryDraft.scriptId().isBlank()) {
            throw new IllegalArgumentException("attachments are allowed only for manual entry orders");
        }

        ArmPolicy armPolicy = attachments.armPolicy() == null ? ArmPolicy.AFTER_ENTRY_FILLED : attachments.armPolicy();
        double entryRefPrice = resolveEntryReferencePrice(entryDraft);
        validateAttachmentOrientation(entryDraft.side(), attachments, entryRefPrice);

        long now = Instant.now().toEpochMilli();
        TradingSide exitSide = opposite(entryDraft.side());

        ExitLegSpec tp = normalizeLeg(attachments.takeProfit(), ExitLegType.TAKE_PROFIT, exitSide, entryDraft.qty());
        ExitLegSpec sl = normalizeLeg(attachments.stopLoss(), ExitLegType.STOP_LOSS, exitSide, entryDraft.qty());

        ExitPlanMode mode = tp != null && sl != null
                ? ExitPlanMode.OCO
                : tp != null ? ExitPlanMode.TP_ONLY : ExitPlanMode.SL_ONLY;

        ExitPlan plan = new ExitPlan(
                UUID.randomUUID().toString(),
                account.accountId(),
                order.orderId(),
                entryDraft.instrumentToken(),
                entryDraft.tradingsymbol(),
                entryDraft.exchange(),
                entryDraft.product(),
                entryDraft.side(),
                entryDraft.qty(),
                ExitPlanStatus.PENDING,
                armPolicy,
                mode,
                null,
                tp,
                sl,
                null,
                null,
                now,
                now);
        plan = triggerStorePort.saveExitPlan(plan);

        publish(account, TradingEventType.EXIT_PLAN_CREATED, plan);

        if (order.status() == TradingOrderStatus.FILLED) {
            exitPlanArmingService.armPendingPlansForFilledEntry(account, order);
        }

        ExitPlan latestPlan = triggerStorePort.findExitPlan(account.accountId(), plan.exitPlanId()).orElse(plan);
        return new PlaceOrderWithLinkedExitResult(order, LinkedExitSummary.from(latestPlan));
    }

    private double resolveEntryReferencePrice(OrderDraft entryDraft) {
        if (entryDraft.price() != null && entryDraft.price() > 0) {
            return entryDraft.price();
        }
        if (entryDraft.triggerPrice() != null && entryDraft.triggerPrice() > 0) {
            return entryDraft.triggerPrice();
        }
        return lastPricePort.getPriceSnapshot(entryDraft.instrumentToken())
                .map(snapshot -> snapshot.lastPrice() > 0 ? snapshot.lastPrice() : snapshot.openPrice())
                .filter(item -> item != null && item > 0)
                .orElseThrow(() -> new IllegalArgumentException("unable to resolve entry reference price for attachments"));
    }

    private ExitLegSpec normalizeLeg(ExitLegSpec leg, ExitLegType type, TradingSide side, int qty) {
        if (leg == null) {
            return null;
        }
        if (leg.triggerPrice() <= 0) {
            throw new IllegalArgumentException(type + " triggerPrice must be positive");
        }
        if (leg.limitOffset() < 0) {
            throw new IllegalArgumentException(type + " limitOffset must be >= 0");
        }
        return new ExitLegSpec(type, leg.triggerPrice(), leg.limitOffset(), side, qty);
    }

    private void validateAttachmentOrientation(TradingSide entrySide, OrderAttachments attachments, double entryRefPrice) {
        if (attachments.takeProfit() != null) {
            double tp = attachments.takeProfit().triggerPrice();
            if (entrySide == TradingSide.BUY && tp <= entryRefPrice) {
                throw new IllegalArgumentException("takeProfit must be above entry reference price for BUY entries");
            }
            if (entrySide == TradingSide.SELL && tp >= entryRefPrice) {
                throw new IllegalArgumentException("takeProfit must be below entry reference price for SELL entries");
            }
        }
        if (attachments.stopLoss() != null) {
            double sl = attachments.stopLoss().triggerPrice();
            if (entrySide == TradingSide.BUY && sl >= entryRefPrice) {
                throw new IllegalArgumentException("stopLoss must be below entry reference price for BUY entries");
            }
            if (entrySide == TradingSide.SELL && sl <= entryRefPrice) {
                throw new IllegalArgumentException("stopLoss must be above entry reference price for SELL entries");
            }
        }
    }

    private TradingSide opposite(TradingSide side) {
        return side == TradingSide.BUY ? TradingSide.SELL : TradingSide.BUY;
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
}
