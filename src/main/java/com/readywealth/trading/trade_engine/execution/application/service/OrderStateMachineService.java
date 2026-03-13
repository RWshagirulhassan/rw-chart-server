package com.readywealth.trading.trade_engine.execution.application.service;

import com.readywealth.trading.trade_engine.execution.domain.TradingOrderStatus;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
public class OrderStateMachineService {

    private static final Map<TradingOrderStatus, Set<TradingOrderStatus>> TRANSITIONS = Map.of(
            TradingOrderStatus.NEW, Set.of(TradingOrderStatus.OPEN, TradingOrderStatus.REJECTED),
            TradingOrderStatus.OPEN, Set.of(TradingOrderStatus.PARTIAL, TradingOrderStatus.FILLED,
                    TradingOrderStatus.CANCELLED, TradingOrderStatus.REJECTED),
            TradingOrderStatus.PARTIAL, Set.of(TradingOrderStatus.FILLED, TradingOrderStatus.CANCELLED),
            TradingOrderStatus.FILLED, Set.of(),
            TradingOrderStatus.CANCELLED, Set.of(),
            TradingOrderStatus.REJECTED, Set.of());

    public boolean canTransition(TradingOrderStatus from, TradingOrderStatus to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return true;
        }
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public void ensureTransition(TradingOrderStatus from, TradingOrderStatus to, String orderId) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException(
                    "Invalid order transition orderId=" + orderId + " from=" + from + " to=" + to);
        }
    }
}
