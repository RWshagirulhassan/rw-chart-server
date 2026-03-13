package com.readywealth.trading.trade_engine.execution.application.port;

import com.readywealth.trading.trade_engine.execution.domain.ChargesResult;
import com.readywealth.trading.trade_engine.execution.domain.MarginCheckResult;
import com.readywealth.trading.trade_engine.execution.domain.OrderDraft;

import java.util.List;

public interface MarginService {
    MarginCheckResult checkMargins(String accountId, List<OrderDraft> basket);

    ChargesResult estimateCharges(String accountId, List<OrderDraft> basket);
}
