package com.readywealth.trading.trade_engine.execution.application.service;

import com.readywealth.trading.trade_engine.execution.domain.ArmPolicy;
import com.readywealth.trading.trade_engine.execution.domain.ExitLegSpec;
import com.readywealth.trading.trade_engine.execution.domain.ExitLegType;
import com.readywealth.trading.trade_engine.execution.domain.ExitPlan;
import com.readywealth.trading.trade_engine.execution.domain.ExitPlanMode;
import com.readywealth.trading.trade_engine.execution.domain.ExitPlanStatus;
import com.readywealth.trading.trade_engine.execution.domain.OcoGroup;
import com.readywealth.trading.trade_engine.execution.domain.OcoGroupStatus;
import com.readywealth.trading.trade_engine.execution.domain.OrderTimelineIds;
import com.readywealth.trading.trade_engine.execution.domain.OrderTimelineStatus;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventSource;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrder;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderRole;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderStatus;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderType;
import com.readywealth.trading.trade_engine.execution.domain.TradingProduct;
import com.readywealth.trading.trade_engine.execution.domain.TradingSide;
import com.readywealth.trading.trade_engine.execution.domain.Trigger;
import com.readywealth.trading.trade_engine.execution.domain.TriggerConditionOp;
import com.readywealth.trading.trade_engine.execution.domain.TriggerStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OrderTimelineStatusResolverTest {

    private final OrderTimelineStatusResolver resolver = new OrderTimelineStatusResolver();

    @Test
    void shouldResolveFilledWhenLinkedOrderFilled() {
        ExitPlan plan = basePlan(ExitPlanStatus.ACTIVE);
        Trigger trigger = baseTrigger(TriggerStatus.ORDER_PLACED);
        TradingOrder linkedOrder = linkedOrder(TradingOrderStatus.FILLED, null);

        var resolution = resolver.resolve(plan, trigger, linkedOrder, null);
        Assertions.assertEquals(OrderTimelineStatus.FILLED, resolution.status());
        Assertions.assertEquals("linked_order_filled", resolution.reason());
    }

    @Test
    void shouldResolveArmedWhenTriggerActiveAndPlanActive() {
        ExitPlan plan = basePlan(ExitPlanStatus.ACTIVE);
        Trigger trigger = baseTrigger(TriggerStatus.ACTIVE);

        var resolution = resolver.resolve(plan, trigger, null, null);
        Assertions.assertEquals(OrderTimelineStatus.ARMED, resolution.status());
        Assertions.assertEquals("trigger_active", resolution.reason());
    }

    @Test
    void shouldResolveCancelledOcoWhenSiblingTriggered() {
        ExitPlan plan = basePlan(ExitPlanStatus.ACTIVE);
        Trigger trigger = baseTrigger(TriggerStatus.CANCELLED);
        OcoGroup ocoGroup = new OcoGroup(
                "oco-1",
                "acct-1",
                plan.exitPlanId(),
                trigger.triggerId(),
                "trg-2",
                "trg-2",
                OcoGroupStatus.ONE_TRIGGERED,
                1L,
                2L);

        var resolution = resolver.resolve(plan, trigger, null, ocoGroup);
        Assertions.assertEquals(OrderTimelineStatus.CANCELLED_OCO, resolution.status());
        Assertions.assertEquals("one_cancels_the_other", resolution.reason());
    }

    @Test
    void shouldResolvePendingWhenPlanPending() {
        ExitPlan plan = basePlan(ExitPlanStatus.PENDING);

        var resolution = resolver.resolve(plan, null, null, null);
        Assertions.assertEquals(OrderTimelineStatus.PENDING, resolution.status());
        Assertions.assertEquals("waiting_entry_fill", resolution.reason());
    }

    @Test
    void shouldResolveRejectedReasonFromLinkedOrder() {
        ExitPlan plan = basePlan(ExitPlanStatus.ACTIVE);
        Trigger trigger = baseTrigger(TriggerStatus.ORDER_REJECTED);
        TradingOrder linkedOrder = linkedOrder(TradingOrderStatus.REJECTED, "price_out_of_band");

        var resolution = resolver.resolve(plan, trigger, linkedOrder, null);
        Assertions.assertEquals(OrderTimelineStatus.REJECTED, resolution.status());
        Assertions.assertEquals("price_out_of_band", resolution.reason());
    }

    @Test
    void shouldMapOrderStatusForNewAndPartial() {
        Assertions.assertEquals("OPEN", resolver.mapOrderStatus(TradingOrderStatus.NEW));
        Assertions.assertEquals("PARTIALLY_FILLED", resolver.mapOrderStatus(TradingOrderStatus.PARTIAL));
        Assertions.assertEquals("FILLED", resolver.mapOrderStatus(TradingOrderStatus.FILLED));
    }

    @Test
    void shouldGenerateStableTimelineIds() {
        Assertions.assertEquals("order:o-1", OrderTimelineIds.entryRowId("o-1"));
        Assertions.assertEquals("lexit:e-1:TP", OrderTimelineIds.linkedRowId("e-1", ExitLegType.TAKE_PROFIT));
        Assertions.assertEquals("lexit:e-1:SL", OrderTimelineIds.linkedRowId("e-1", ExitLegType.STOP_LOSS));
    }

    private ExitPlan basePlan(ExitPlanStatus status) {
        ExitLegSpec tp = new ExitLegSpec(ExitLegType.TAKE_PROFIT, 120.0d, 0.5d, TradingSide.SELL, 5);
        ExitLegSpec sl = new ExitLegSpec(ExitLegType.STOP_LOSS, 90.0d, 0.5d, TradingSide.SELL, 5);
        return new ExitPlan(
                "exp-1",
                "acct-1",
                "entry-1",
                123L,
                "INFY",
                "NSE",
                TradingProduct.MIS,
                TradingSide.BUY,
                5,
                status,
                ArmPolicy.AFTER_ENTRY_FILLED,
                ExitPlanMode.OCO,
                "oco-1",
                tp,
                sl,
                null,
                null,
                1L,
                1L);
    }

    private Trigger baseTrigger(TriggerStatus status) {
        return new Trigger(
                "trg-1",
                "acct-1",
                "exp-1",
                123L,
                "INFY",
                "NSE",
                TradingProduct.MIS,
                ExitLegType.TAKE_PROFIT,
                TriggerConditionOp.GTE,
                120.0d,
                0.5d,
                TradingSide.SELL,
                5,
                status,
                100.0d,
                null,
                null,
                "oco-1",
                "ord-linked-1",
                null,
                1L,
                1L);
    }

    private TradingOrder linkedOrder(TradingOrderStatus status, String rejectionReason) {
        return new TradingOrder(
                "ord-linked-1",
                "PAPER-1",
                "acct-1",
                123L,
                "INFY",
                "NSE",
                TradingProduct.MIS,
                TradingOrderType.LIMIT,
                TradingSide.SELL,
                5,
                119.5d,
                null,
                status,
                status == TradingOrderStatus.PARTIAL ? 2 : 0,
                0.0d,
                1L,
                1L,
                rejectionReason,
                TradingEventSource.PAPER,
                null,
                "exit",
                "entry-1",
                "exp-1",
                "trg-1",
                "oco-1",
                TradingOrderRole.EXIT_TP,
                true);
    }
}
