package com.readywealth.trading.trade_engine.engine.domain.script;

import com.readywealth.trading.trade_engine.engine.domain.series.BarSeriesContext;

public record ScriptInitContext(
        BarSeriesContext seriesContext,
        ScriptInstanceId scriptInstanceId) {
}
