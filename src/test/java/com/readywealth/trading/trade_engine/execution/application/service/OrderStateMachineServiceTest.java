package com.readywealth.trading.trade_engine.execution.application.service;

import com.readywealth.trading.trade_engine.execution.domain.TradingOrderStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OrderStateMachineServiceTest {

    private final OrderStateMachineService service = new OrderStateMachineService();

    @Test
    void shouldAllowOpenToFilled() {
        Assertions.assertTrue(service.canTransition(TradingOrderStatus.OPEN, TradingOrderStatus.FILLED));
    }

    @Test
    void shouldRejectFilledToOpen() {
        Assertions.assertFalse(service.canTransition(TradingOrderStatus.FILLED, TradingOrderStatus.OPEN));
        Assertions.assertThrows(IllegalStateException.class,
                () -> service.ensureTransition(TradingOrderStatus.FILLED, TradingOrderStatus.OPEN, "o-1"));
    }
}
