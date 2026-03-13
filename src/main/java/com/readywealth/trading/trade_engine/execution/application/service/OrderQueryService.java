package com.readywealth.trading.trade_engine.execution.application.service;

import com.readywealth.trading.trade_engine.execution.application.port.TradingStorePort;
import com.readywealth.trading.trade_engine.execution.domain.TradingFundsView;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrder;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderStatus;
import com.readywealth.trading.trade_engine.execution.domain.TradingPosition;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderQueryService {

    private final TradingStorePort tradingStorePort;

    public OrderQueryService(TradingStorePort tradingStorePort) {
        this.tradingStorePort = tradingStorePort;
    }

    public List<TradingOrder> orders(String accountId, TradingOrderStatus status) {
        return tradingStorePort.listOrders(accountId, status);
    }

    public List<TradingPosition> positions(String accountId) {
        return tradingStorePort.listPositions(accountId);
    }

    public TradingFundsView funds(String accountId) {
        return tradingStorePort.findFunds(accountId)
                .orElseGet(() -> TradingFundsView.opening(0.0d, System.currentTimeMillis()));
    }
}
