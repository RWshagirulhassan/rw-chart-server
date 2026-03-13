package com.readywealth.trading.trade_engine.execution.api.http.dto;

import java.util.List;

public record BasketRequest(
        List<OrderDraftRequest> orders) {
}
