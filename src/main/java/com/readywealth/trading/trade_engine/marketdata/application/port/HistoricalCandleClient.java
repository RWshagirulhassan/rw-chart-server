package com.readywealth.trading.trade_engine.marketdata.application.port;

import com.readywealth.trading.trade_engine.engine.domain.series.Candle;
import com.readywealth.trading.trade_engine.engine.domain.series.IntervalKind;
import com.readywealth.trading.trade_engine.marketdata.infrastructure.http.HistoricalFetchException;

import java.time.Instant;
import java.util.List;

public interface HistoricalCandleClient {

    List<Candle> fetchCandlesInRange(long instrumentToken,
                                     IntervalKind intervalKind,
                                     Instant from,
                                     Instant to);

    List<Candle> fetchRecentCandles(long instrumentToken,
                                    IntervalKind intervalKind);

    default List<Candle> fetchRecentCandlesForBootstrap(long instrumentToken,
                                                        IntervalKind intervalKind) throws HistoricalFetchException {
        return fetchRecentCandles(instrumentToken, intervalKind);
    }
}
