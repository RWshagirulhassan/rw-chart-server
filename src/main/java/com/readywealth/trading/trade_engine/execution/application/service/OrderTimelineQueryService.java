package com.readywealth.trading.trade_engine.execution.application.service;

import com.readywealth.trading.trade_engine.execution.application.port.TradingStorePort;
import com.readywealth.trading.trade_engine.execution.application.port.TriggerStorePort;
import com.readywealth.trading.trade_engine.execution.domain.ExitLegSpec;
import com.readywealth.trading.trade_engine.execution.domain.ExitLegType;
import com.readywealth.trading.trade_engine.execution.domain.ExitPlan;
import com.readywealth.trading.trade_engine.execution.domain.OcoGroup;
import com.readywealth.trading.trade_engine.execution.domain.OrderTimelineIds;
import com.readywealth.trading.trade_engine.execution.domain.OrderTimelineRow;
import com.readywealth.trading.trade_engine.execution.domain.OrderTimelineRowType;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrder;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderRole;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderStatus;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderType;
import com.readywealth.trading.trade_engine.execution.domain.TradingSide;
import com.readywealth.trading.trade_engine.execution.domain.Trigger;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class OrderTimelineQueryService {

    private final TradingStorePort tradingStorePort;
    private final TriggerStorePort triggerStorePort;
    private final OrderTimelineStatusResolver orderTimelineStatusResolver;

    public OrderTimelineQueryService(
            TradingStorePort tradingStorePort,
            TriggerStorePort triggerStorePort,
            OrderTimelineStatusResolver orderTimelineStatusResolver) {
        this.tradingStorePort = tradingStorePort;
        this.triggerStorePort = triggerStorePort;
        this.orderTimelineStatusResolver = orderTimelineStatusResolver;
    }

    public List<OrderTimelineRow> timeline(String accountId, boolean includeTerminal, int limit) {
        int safeLimit = Math.max(1, Math.min(1000, limit));

        List<TradingOrder> allOrders = tradingStorePort.listOrders(accountId, null);
        Map<String, TradingOrder> ordersById = new HashMap<>();
        for (TradingOrder order : allOrders) {
            ordersById.put(order.orderId(), order);
        }

        List<TradingOrder> entryOrders = allOrders.stream()
                .filter(this::isEntryOrder)
                .sorted(Comparator.comparingLong(TradingOrder::createdAtEpochMs).reversed())
                .toList();

        List<ExitPlan> plans = triggerStorePort.listExitPlans(accountId, null).stream()
                .filter(item -> item.entryOrderId() != null && !item.entryOrderId().isBlank())
                .toList();

        Map<String, List<ExitPlan>> plansByEntry = new HashMap<>();
        for (ExitPlan plan : plans) {
            plansByEntry.computeIfAbsent(plan.entryOrderId(), ignored -> new ArrayList<>()).add(plan);
        }
        for (List<ExitPlan> bucket : plansByEntry.values()) {
            bucket.sort(Comparator.comparingLong(ExitPlan::createdAtEpochMs));
        }

        Map<TriggerKey, Trigger> triggerByPlanLeg = latestTriggerByPlanLeg(triggerStorePort.listTriggers(accountId, null));

        List<OrderTimelineRow> rows = new ArrayList<>();
        for (TradingOrder entry : entryOrders) {
            rows.add(buildEntryRow(entry));

            List<ExitPlan> entryPlans = plansByEntry.getOrDefault(entry.orderId(), List.of());
            for (ExitPlan plan : entryPlans) {
                if (plan.takeProfit() != null) {
                    rows.add(buildLinkedRow(entry, plan, ExitLegType.TAKE_PROFIT, triggerByPlanLeg, ordersById));
                }
                if (plan.stopLoss() != null) {
                    rows.add(buildLinkedRow(entry, plan, ExitLegType.STOP_LOSS, triggerByPlanLeg, ordersById));
                }
            }
        }

        List<OrderTimelineRow> filtered = includeTerminal ? rows : filterTerminal(rows);
        if (filtered.size() <= safeLimit) {
            return filtered;
        }
        return filtered.subList(0, safeLimit);
    }

    private OrderTimelineRow buildEntryRow(TradingOrder entry) {
        String status = orderTimelineStatusResolver.mapOrderStatus(entry.status());
        return new OrderTimelineRow(
                OrderTimelineIds.entryRowId(entry.orderId()),
                OrderTimelineRowType.ENTRY_ORDER,
                null,
                entry.orderId(),
                entry.exitPlanId(),
                entry.triggerId(),
                null,
                null,
                entry.instrumentToken(),
                entry.tradingsymbol(),
                entry.exchange(),
                entry.side(),
                entry.qty(),
                entry.orderType(),
                entry.price(),
                entry.triggerPrice(),
                null,
                com.readywealth.trading.trade_engine.execution.domain.OrderTimelineStatus.valueOf(status),
                entry.rejectionReason(),
                entry.createdAtEpochMs(),
                entry.updatedAtEpochMs(),
                entry.createdAtEpochMs());
    }

    private OrderTimelineRow buildLinkedRow(
            TradingOrder entry,
            ExitPlan plan,
            ExitLegType legType,
            Map<TriggerKey, Trigger> triggerByPlanLeg,
            Map<String, TradingOrder> ordersById) {
        ExitLegSpec legSpec = legType == ExitLegType.TAKE_PROFIT ? plan.takeProfit() : plan.stopLoss();
        Trigger trigger = triggerByPlanLeg.get(new TriggerKey(plan.exitPlanId(), legType));
        TradingOrder linkedOrder = trigger == null || trigger.linkedOrderId() == null
                ? null
                : ordersById.get(trigger.linkedOrderId());
        OcoGroup ocoGroup = trigger != null && trigger.ocoGroupId() != null && !trigger.ocoGroupId().isBlank()
                ? triggerStorePort.findOcoGroup(plan.accountId(), trigger.ocoGroupId()).orElse(null)
                : null;

        OrderTimelineStatusResolver.Resolution resolution = orderTimelineStatusResolver.resolve(plan, trigger, linkedOrder, ocoGroup);

        TradingSide side = linkedOrder != null
                ? linkedOrder.side()
                : trigger != null ? trigger.orderSide() : legSpec.orderSide();
        int qty = linkedOrder != null
                ? linkedOrder.qty()
                : trigger != null ? trigger.plannedQty() : legSpec.qty();
        Double price = linkedOrder != null ? linkedOrder.price() : plannedLimitPrice(trigger, legSpec);
        Double triggerPrice = trigger != null ? trigger.triggerValue() : legSpec.triggerPrice();
        Double limitOffset = trigger != null ? trigger.limitOffset() : legSpec.limitOffset();
        long createdAt = trigger == null ? plan.createdAtEpochMs() : trigger.createdAtEpochMs();
        long updatedAt = max(plan.updatedAtEpochMs(),
                trigger == null ? 0L : trigger.updatedAtEpochMs(),
                linkedOrder == null ? 0L : linkedOrder.updatedAtEpochMs());

        return new OrderTimelineRow(
                OrderTimelineIds.linkedRowId(plan.exitPlanId(), legType),
                OrderTimelineRowType.LINKED_EXIT,
                entry.orderId(),
                entry.orderId(),
                plan.exitPlanId(),
                trigger == null ? null : trigger.triggerId(),
                linkedOrder == null ? null : linkedOrder.orderId(),
                legType,
                plan.instrumentToken(),
                plan.tradingsymbol(),
                plan.exchange(),
                side,
                qty,
                TradingOrderType.LIMIT,
                price,
                triggerPrice,
                limitOffset,
                resolution.status(),
                resolution.reason(),
                createdAt,
                updatedAt,
                entry.createdAtEpochMs());
    }

    private List<OrderTimelineRow> filterTerminal(List<OrderTimelineRow> rows) {
        List<OrderTimelineRow> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            OrderTimelineRow row = rows.get(i);
            if (row.rowType() != OrderTimelineRowType.ENTRY_ORDER) {
                continue;
            }

            List<OrderTimelineRow> children = new ArrayList<>();
            int j = i + 1;
            while (j < rows.size() && rows.get(j).rowType() == OrderTimelineRowType.LINKED_EXIT
                    && Objects.equals(rows.get(j).parentOrderId(), row.entryOrderId())) {
                children.add(rows.get(j));
                j += 1;
            }

            List<OrderTimelineRow> visibleChildren = children.stream()
                    .filter(child -> !child.status().terminal())
                    .toList();

            boolean parentTerminal = parentTerminal(row.status());
            if (!parentTerminal || !visibleChildren.isEmpty()) {
                out.add(row);
                out.addAll(visibleChildren);
            }

            i = j - 1;
        }
        return out;
    }

    private boolean parentTerminal(com.readywealth.trading.trade_engine.execution.domain.OrderTimelineStatus status) {
        return status == com.readywealth.trading.trade_engine.execution.domain.OrderTimelineStatus.FILLED
                || status == com.readywealth.trading.trade_engine.execution.domain.OrderTimelineStatus.CANCELLED
                || status == com.readywealth.trading.trade_engine.execution.domain.OrderTimelineStatus.REJECTED;
    }

    private Map<TriggerKey, Trigger> latestTriggerByPlanLeg(List<Trigger> triggers) {
        Map<TriggerKey, Trigger> out = new HashMap<>();
        for (Trigger trigger : triggers) {
            TriggerKey key = new TriggerKey(trigger.exitPlanId(), trigger.legType());
            Trigger current = out.get(key);
            if (current == null || trigger.updatedAtEpochMs() > current.updatedAtEpochMs()) {
                out.put(key, trigger);
            }
        }
        return out;
    }

    private boolean isEntryOrder(TradingOrder order) {
        return order.role() == null || order.role() == TradingOrderRole.ENTRY;
    }

    private Double plannedLimitPrice(Trigger trigger, ExitLegSpec legSpec) {
        TradingSide side = trigger != null ? trigger.orderSide() : legSpec.orderSide();
        double triggerValue = trigger != null ? trigger.triggerValue() : legSpec.triggerPrice();
        double offset = trigger != null ? trigger.limitOffset() : legSpec.limitOffset();
        if (side == TradingSide.SELL) {
            return triggerValue - Math.max(0.0d, offset);
        }
        return triggerValue + Math.max(0.0d, offset);
    }

    private long max(long first, long second, long third) {
        return Math.max(first, Math.max(second, third));
    }

    private record TriggerKey(
            String exitPlanId,
            ExitLegType legType) {
    }
}
