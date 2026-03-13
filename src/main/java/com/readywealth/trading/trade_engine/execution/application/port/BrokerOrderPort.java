package com.readywealth.trading.trade_engine.execution.application.port;

import com.readywealth.trading.trade_engine.execution.domain.OrderDraft;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrder;

import java.util.List;

public interface BrokerOrderPort {
    PlaceOrderResult placeOrder(OrderDraft orderDraft, TradingOrder canonicalOrder);

    CancelOrderResult cancelOrder(TradingOrder currentOrder, String reason);

    ModifyOrderResult modifyOrder(TradingOrder currentOrder, Integer qty, Double price, Double triggerPrice);

    List<TradingOrder> getOrders(String accountId);
}
