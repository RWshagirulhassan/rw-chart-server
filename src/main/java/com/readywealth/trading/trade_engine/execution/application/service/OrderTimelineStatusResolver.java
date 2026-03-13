package com.readywealth.trading.trade_engine.execution.application.service;

import com.readywealth.trading.trade_engine.execution.domain.ExitPlan;
import com.readywealth.trading.trade_engine.execution.domain.ExitPlanStatus;
import com.readywealth.trading.trade_engine.execution.domain.OcoGroup;
import com.readywealth.trading.trade_engine.execution.domain.OrderTimelineStatus;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrder;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderStatus;
import com.readywealth.trading.trade_engine.execution.domain.Trigger;
import com.readywealth.trading.trade_engine.execution.domain.TriggerStatus;
import org.springframework.stereotype.Service;

@Service
public class OrderTimelineStatusResolver {

    public Resolution resolve(ExitPlan plan, Trigger trigger, TradingOrder linkedOrder, OcoGroup ocoGroup) {
        if (linkedOrder != null) {
            if (linkedOrder.status() == TradingOrderStatus.FILLED) {
                return new Resolution(OrderTimelineStatus.FILLED, "linked_order_filled");
            }
            if (linkedOrder.status() == TradingOrderStatus.PARTIAL) {
                return new Resolution(OrderTimelineStatus.PARTIALLY_FILLED, "linked_order_partial");
            }
            if (linkedOrder.status() == TradingOrderStatus.OPEN || linkedOrder.status() == TradingOrderStatus.NEW) {
                return new Resolution(OrderTimelineStatus.OPEN, "linked_order_open");
            }
            if (linkedOrder.status() == TradingOrderStatus.REJECTED) {
                return new Resolution(OrderTimelineStatus.REJECTED, defaultReason(
                        linkedOrder.rejectionReason(),
                        "linked_order_rejected"));
            }
            if (linkedOrder.status() == TradingOrderStatus.CANCELLED && plan.status() == ExitPlanStatus.REARM_REQUIRED) {
                return new Resolution(OrderTimelineStatus.REARM_REQUIRED, defaultReason(
                        plan.rearmReason(),
                        "rearm_required"));
            }
        }

        if (plan.status() == ExitPlanStatus.REARM_REQUIRED) {
            return new Resolution(OrderTimelineStatus.REARM_REQUIRED, defaultReason(
                    plan.rearmReason(),
                    "rearm_required"));
        }

        if (trigger != null && trigger.status() == TriggerStatus.ORDER_REJECTED) {
            return new Resolution(OrderTimelineStatus.REJECTED, defaultReason(
                    trigger.rejectionReason(),
                    "trigger_order_rejected"));
        }

        if (trigger != null && trigger.status() == TriggerStatus.ORDER_PLACED) {
            return new Resolution(OrderTimelineStatus.OPEN, "linked_order_placed");
        }

        if (trigger != null && trigger.status() == TriggerStatus.TRIGGERED) {
            return new Resolution(OrderTimelineStatus.TRIGGERED, "trigger_hit");
        }

        if (trigger != null && trigger.status() == TriggerStatus.ACTIVE && plan.status() == ExitPlanStatus.ACTIVE) {
            return new Resolution(OrderTimelineStatus.ARMED, "trigger_active");
        }

        if (plan.status() == ExitPlanStatus.PENDING) {
            return new Resolution(OrderTimelineStatus.PENDING, "waiting_entry_fill");
        }

        if (trigger != null && trigger.status() == TriggerStatus.CANCELLED && siblingTriggered(trigger, ocoGroup)) {
            return new Resolution(OrderTimelineStatus.CANCELLED_OCO, "one_cancels_the_other");
        }

        if (plan.status() == ExitPlanStatus.CANCELLED || (trigger != null && trigger.status() == TriggerStatus.CANCELLED)) {
            return new Resolution(OrderTimelineStatus.CANCELLED, defaultReason(
                    trigger == null ? null : trigger.rejectionReason(),
                    "cancelled"));
        }

        if (plan.status() == ExitPlanStatus.ACTIVE) {
            return new Resolution(OrderTimelineStatus.ARMED, "exit_plan_active");
        }

        return new Resolution(OrderTimelineStatus.PENDING, "pending");
    }

    public String mapOrderStatus(TradingOrderStatus status) {
        if (status == null) {
            return OrderTimelineStatus.PENDING.name();
        }
        return switch (status) {
            case NEW -> OrderTimelineStatus.OPEN.name();
            case PARTIAL -> OrderTimelineStatus.PARTIALLY_FILLED.name();
            default -> status.name();
        };
    }

    private boolean siblingTriggered(Trigger trigger, OcoGroup ocoGroup) {
        if (trigger == null || ocoGroup == null || ocoGroup.triggeredTriggerId() == null) {
            return false;
        }
        return !ocoGroup.triggeredTriggerId().isBlank()
                && !ocoGroup.triggeredTriggerId().equals(trigger.triggerId());
    }

    private String defaultReason(String reason, String fallback) {
        if (reason == null || reason.isBlank()) {
            return fallback;
        }
        return reason;
    }

    public record Resolution(
            OrderTimelineStatus status,
            String reason) {
    }
}
