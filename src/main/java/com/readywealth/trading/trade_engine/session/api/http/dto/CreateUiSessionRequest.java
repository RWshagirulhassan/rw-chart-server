package com.readywealth.trading.trade_engine.session.api.http.dto;

import java.util.List;

public record CreateUiSessionRequest(
        List<String> seriesKeys,
        Integer maxBarCount,
        Boolean destroyOnClose) {
}
