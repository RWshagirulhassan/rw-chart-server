package com.readywealth.trading.trade_engine.execution.infrastructure.paper;

import com.readywealth.trading.trade_engine.execution.application.port.LastPricePort;
import com.readywealth.trading.trade_engine.execution.application.port.TradingStorePort;
import com.readywealth.trading.trade_engine.execution.domain.BrokerType;
import com.readywealth.trading.trade_engine.execution.domain.FillApplyCommand;
import com.readywealth.trading.trade_engine.execution.domain.FillApplyResult;
import com.readywealth.trading.trade_engine.execution.domain.MarginCheckResult;
import com.readywealth.trading.trade_engine.execution.domain.OrderDraft;
import com.readywealth.trading.trade_engine.execution.domain.PriceSnapshot;
import com.readywealth.trading.trade_engine.execution.domain.StrategyExecutionSettings;
import com.readywealth.trading.trade_engine.execution.domain.TradingAccount;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventSource;
import com.readywealth.trading.trade_engine.execution.domain.TradingFundsView;
import com.readywealth.trading.trade_engine.execution.domain.TradingMode;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrder;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderStatus;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderType;
import com.readywealth.trading.trade_engine.execution.domain.TradingPosition;
import com.readywealth.trading.trade_engine.execution.domain.TradingProduct;
import com.readywealth.trading.trade_engine.execution.domain.TradingSide;
import com.readywealth.trading.trade_engine.execution.infrastructure.config.TradingProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

class SimulatedMarginServiceTest {

    @Test
    void shouldRejectWhenCncBuyNeedsMoreCash() {
        TradingStorePort store = new InMemoryStore(500.0d);
        LastPricePort lastPricePort = token -> Optional.of(new PriceSnapshot(token, 100.0d, 100.0d, 100.0d, 100.0d,
                System.currentTimeMillis(), true));
        TradingProperties properties = new TradingProperties();
        SimulatedMarginService service = new SimulatedMarginService(store, lastPricePort, properties);

        MarginCheckResult result = service.checkMargins("a-1", List.of(new OrderDraft(
                "a-1",
                123L,
                "TCS",
                "NSE",
                TradingProduct.CNC,
                TradingOrderType.MARKET,
                TradingSide.BUY,
                10,
                null,
                null,
                TradingEventSource.CORE,
                null,
                "manual")));

        Assertions.assertFalse(result.allowed());
    }

    @Test
    void shouldAllowMisForSmallNotional() {
        TradingStorePort store = new InMemoryStore(5000.0d);
        LastPricePort lastPricePort = token -> Optional.of(new PriceSnapshot(token, 100.0d, 100.0d, 100.0d, 100.0d,
                System.currentTimeMillis(), true));
        TradingProperties properties = new TradingProperties();
        properties.getMargin().setMisPercent(0.2d);
        SimulatedMarginService service = new SimulatedMarginService(store, lastPricePort, properties);

        MarginCheckResult result = service.checkMargins("a-1", List.of(new OrderDraft(
                "a-1",
                123L,
                "TCS",
                "NSE",
                TradingProduct.MIS,
                TradingOrderType.MARKET,
                TradingSide.BUY,
                10,
                null,
                null,
                TradingEventSource.CORE,
                null,
                "manual")));

        Assertions.assertTrue(result.allowed());
    }

    private static final class InMemoryStore implements TradingStorePort {

        private final TradingFundsView funds;

        private InMemoryStore(double cash) {
            this.funds = TradingFundsView.opening(cash, Instant.now().toEpochMilli());
        }

        @Override
        public TradingAccount saveAccount(TradingAccount account) { return account; }

        @Override
        public Optional<TradingAccount> findAccountById(String accountId) { return Optional.empty(); }

        @Override
        public Optional<TradingAccount> findAccountByUserId(String userId) { return Optional.empty(); }

        @Override
        public List<TradingAccount> listAccountsByMode(TradingMode mode) { return List.of(); }

        @Override
        public TradingOrder saveOrder(TradingOrder order) { return order; }

        @Override
        public Optional<TradingOrder> findOrder(String accountId, String orderId) { return Optional.empty(); }

        @Override
        public List<TradingOrder> listOrders(String accountId, TradingOrderStatus status) { return List.of(); }

        @Override
        public TradingFundsView saveFunds(String accountId, TradingFundsView fundsView) { return fundsView; }

        @Override
        public Optional<TradingFundsView> findFunds(String accountId) { return Optional.of(funds); }

        @Override
        public TradingPosition savePosition(TradingPosition position) { return position; }

        @Override
        public List<TradingPosition> listPositions(String accountId) { return List.of(); }

        @Override
        public Optional<TradingPosition> findPosition(String accountId, long instrumentToken, TradingProduct product) {
            return Optional.empty();
        }

        @Override
        public void saveLastMargin(String accountId, com.readywealth.trading.trade_engine.execution.domain.MarginCheckResult marginCheckResult) {
        }

        @Override
        public Optional<com.readywealth.trading.trade_engine.execution.domain.MarginCheckResult> getLastMargin(String accountId) {
            return Optional.empty();
        }

        @Override
        public StrategyExecutionSettings saveStrategySettings(StrategyExecutionSettings settings) { return settings; }

        @Override
        public Optional<StrategyExecutionSettings> getStrategySettings(String accountId, String scriptId) {
            return Optional.empty();
        }

        @Override
        public List<StrategyExecutionSettings> listStrategySettings(String accountId) { return List.of(); }

        @Override
        public FillApplyResult applyFillAtomically(FillApplyCommand fillApplyCommand) { return null; }

        @Override
        public boolean registerIntentExecution(String accountId, String dedupeKey, int ttlSeconds) { return false; }

        @Override
        public long countOpenOrders(String accountId) { return 0; }

        @Override
        public long incrementOrderRateCounter(String accountId, Instant at) { return 0; }
    }
}
