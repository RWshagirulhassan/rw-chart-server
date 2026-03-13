package com.readywealth.trading.trade_engine.execution.infrastructure.paper;

import com.readywealth.trading.trade_engine.execution.application.port.LastPricePort;
import com.readywealth.trading.trade_engine.execution.domain.OrderDraft;
import com.readywealth.trading.trade_engine.execution.domain.PriceSnapshot;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventSource;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrder;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderStatus;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderType;
import com.readywealth.trading.trade_engine.execution.domain.TradingProduct;
import com.readywealth.trading.trade_engine.execution.domain.TradingSide;
import com.readywealth.trading.trade_engine.execution.infrastructure.config.TradingProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class PaperExecutionSimulatorTest {

    @Test
    void shouldFillMarketOrderWhenPriceSnapshotPresent() {
        LastPricePort lastPricePort = token -> Optional.of(new PriceSnapshot(token, 100.0d, 99.5d, 101.0d, 98.5d,
                System.currentTimeMillis(), true));
        PaperExecutionSimulator simulator = new PaperExecutionSimulator(lastPricePort, new TradingProperties());

        OrderDraft draft = new OrderDraft(
                "a-1",
                123L,
                "TCS",
                "NSE",
                TradingProduct.MIS,
                TradingOrderType.MARKET,
                TradingSide.BUY,
                5,
                null,
                null,
                TradingEventSource.CORE,
                null,
                "manual");
        TradingOrder order = new TradingOrder(
                "o-1", null, "a-1", 123L, "TCS", "NSE", TradingProduct.MIS,
                TradingOrderType.MARKET, TradingSide.BUY, 5, null, null,
                TradingOrderStatus.OPEN, 0, 0.0d,
                System.currentTimeMillis(), System.currentTimeMillis(), null,
                TradingEventSource.PAPER, null, "manual");

        Assertions.assertFalse(simulator.simulate(draft, order).isEmpty());
    }

    @Test
    void shouldNotImmediatelyFillLimitOrderOnPlacement() {
        LastPricePort lastPricePort = token -> Optional.of(new PriceSnapshot(token, 99.0d, 99.5d, 101.0d, 98.5d,
                System.currentTimeMillis(), true));
        PaperExecutionSimulator simulator = new PaperExecutionSimulator(lastPricePort, new TradingProperties());

        OrderDraft draft = new OrderDraft(
                "a-1",
                123L,
                "TCS",
                "NSE",
                TradingProduct.MIS,
                TradingOrderType.LIMIT,
                TradingSide.BUY,
                5,
                98.0d,
                null,
                TradingEventSource.CORE,
                null,
                "manual");
        TradingOrder order = new TradingOrder(
                "o-1", null, "a-1", 123L, "TCS", "NSE", TradingProduct.MIS,
                TradingOrderType.LIMIT, TradingSide.BUY, 5, 98.0d, null,
                TradingOrderStatus.OPEN, 0, 0.0d,
                System.currentTimeMillis(), System.currentTimeMillis(), null,
                TradingEventSource.PAPER, null, "manual");

        Assertions.assertTrue(simulator.simulate(draft, order).isEmpty());
    }

    @Test
    void shouldFillOpenLimitOrderOnlyOnFreshTouch() {
        LastPricePort lastPricePort = token -> Optional.empty();
        PaperExecutionSimulator simulator = new PaperExecutionSimulator(lastPricePort, new TradingProperties());

        TradingOrder order = new TradingOrder(
                "o-1", null, "a-1", 123L, "TCS", "NSE", TradingProduct.MIS,
                TradingOrderType.LIMIT, TradingSide.BUY, 5, 100.0d, null,
                TradingOrderStatus.OPEN, 0, 0.0d,
                1_000L, 2_000L, null,
                TradingEventSource.PAPER, null, "manual");

        PriceSnapshot staleSnapshot = new PriceSnapshot(123L, 99.0d, 101.0d, 101.0d, 99.0d, 1_900L, true);
        Assertions.assertTrue(simulator.simulateOpenLimitOrder(order, staleSnapshot).isEmpty());

        PriceSnapshot freshNotTouched = new PriceSnapshot(123L, 101.0d, 101.0d, 102.0d, 100.0d, 2_100L, true);
        Assertions.assertTrue(simulator.simulateOpenLimitOrder(order, freshNotTouched).isEmpty());

        PriceSnapshot freshTouched = new PriceSnapshot(123L, 99.0d, 100.0d, 101.0d, 99.0d, 2_200L, true);
        var fill = simulator.simulateOpenLimitOrder(order, freshTouched);
        Assertions.assertTrue(fill.isPresent());
        Assertions.assertEquals(5, fill.get().fill().qty());
        Assertions.assertEquals(100.0d, fill.get().fill().price());
        Assertions.assertEquals(99.0d, fill.get().markPrice());
    }
}
