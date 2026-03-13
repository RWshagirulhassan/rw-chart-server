package com.readywealth.trading.trade_engine.marketdata.domain;

public record InstrumentSearchResult(
        String instrument_token,
        String name,
        String tradingsymbol,
        String exchange
) {
}
