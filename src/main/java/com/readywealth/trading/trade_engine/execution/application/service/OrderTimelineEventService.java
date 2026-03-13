package com.readywealth.trading.trade_engine.execution.application.service;

import com.readywealth.trading.trade_engine.execution.application.port.TriggerStorePort;
import com.readywealth.trading.trade_engine.execution.domain.ExitLegType;
import com.readywealth.trading.trade_engine.execution.domain.ExitPlan;
import com.readywealth.trading.trade_engine.execution.domain.OrderTimelineIds;
import com.readywealth.trading.trade_engine.execution.domain.OrderTimelineUpdatePayload;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventEnvelope;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventType;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrder;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderRole;
import com.readywealth.trading.trade_engine.execution.domain.Trigger;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class OrderTimelineEventService {

    private final TriggerStorePort triggerStorePort;

    public OrderTimelineEventService(TriggerStorePort triggerStorePort) {
        this.triggerStorePort = triggerStorePort;
    }

    public Optional<OrderTimelineUpdatePayload> toTimelineUpdate(TradingEventEnvelope envelope) {
        if (envelope == null || envelope.type() == null || envelope.type() == TradingEventType.ORDER_TIMELINE_UPDATED) {
            return Optional.empty();
        }
        if (!supports(envelope.type())) {
            return Optional.empty();
        }

        List<String> rowIds = new ArrayList<>();
        List<String> parentOrderIds = new ArrayList<>();
        boolean fullRefresh = false;

        Object payload = envelope.payload();
        if (payload instanceof TradingOrder order) {
            if (order.role() == null || order.role() == TradingOrderRole.ENTRY) {
                rowIds.add(OrderTimelineIds.entryRowId(order.orderId()));
                parentOrderIds.add(order.orderId());
            } else if (order.exitPlanId() != null && !order.exitPlanId().isBlank()) {
                ExitLegType legType = toLegType(order.role());
                if (legType != null) {
                    rowIds.add(OrderTimelineIds.linkedRowId(order.exitPlanId(), legType));
                    if (order.parentOrderId() != null && !order.parentOrderId().isBlank()) {
                        parentOrderIds.add(order.parentOrderId());
                    }
                } else {
                    fullRefresh = true;
                }
            } else {
                fullRefresh = true;
            }
        } else if (payload instanceof ExitPlan plan) {
            if (plan.entryOrderId() == null || plan.entryOrderId().isBlank()) {
                return Optional.empty();
            }
            parentOrderIds.add(plan.entryOrderId());
            if (plan.takeProfit() != null) {
                rowIds.add(OrderTimelineIds.linkedRowId(plan.exitPlanId(), ExitLegType.TAKE_PROFIT));
            }
            if (plan.stopLoss() != null) {
                rowIds.add(OrderTimelineIds.linkedRowId(plan.exitPlanId(), ExitLegType.STOP_LOSS));
            }
            if (rowIds.isEmpty()) {
                fullRefresh = true;
            }
        } else if (payload instanceof Trigger trigger) {
            if (trigger.legType() == null || trigger.exitPlanId() == null || trigger.exitPlanId().isBlank()) {
                fullRefresh = true;
            } else {
                ExitPlan plan = triggerStorePort.findExitPlan(envelope.accountId(), trigger.exitPlanId()).orElse(null);
                if (plan == null || plan.entryOrderId() == null || plan.entryOrderId().isBlank()) {
                    return Optional.empty();
                }
                rowIds.add(OrderTimelineIds.linkedRowId(trigger.exitPlanId(), trigger.legType()));
                parentOrderIds.add(plan.entryOrderId());
            }
        } else {
            fullRefresh = true;
        }

        if (!fullRefresh && rowIds.isEmpty() && parentOrderIds.isEmpty()) {
            fullRefresh = true;
        }

        return Optional.of(new OrderTimelineUpdatePayload(
                rowIds,
                parentOrderIds,
                envelope.type().name().toLowerCase(Locale.ROOT),
                fullRefresh,
                System.currentTimeMillis()));
    }

    private boolean supports(TradingEventType type) {
        return type == TradingEventType.ORDER_ACCEPTED
                || type == TradingEventType.ORDER_UPDATED
                || type == TradingEventType.ORDER_REJECTED
                || type == TradingEventType.EXIT_PLAN_CREATED
                || type == TradingEventType.EXIT_PLAN_ARMED
                || type == TradingEventType.TRIGGER_UPDATED
                || type == TradingEventType.TRIGGER_TRIGGERED
                || type == TradingEventType.OCO_LEG_CANCELLED
                || type == TradingEventType.EXIT_PLAN_COMPLETED
                || type == TradingEventType.EXIT_PLAN_REARM_REQUIRED;
    }

    private ExitLegType toLegType(TradingOrderRole role) {
        if (role == TradingOrderRole.EXIT_TP) {
            return ExitLegType.TAKE_PROFIT;
        }
        if (role == TradingOrderRole.EXIT_SL) {
            return ExitLegType.STOP_LOSS;
        }
        return null;
    }
}
