package com.readywealth.trading.trade_engine.marketdata.application.port;

import com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey;
import com.readywealth.trading.trade_engine.session.domain.UiSessionId;

import java.util.Set;

public interface SessionRoutingPort {
    Set<UiSessionId> sessionsForInstrument(long instrumentToken);

    Set<UiSessionId> sessionsForSeries(SeriesKey key);
}
