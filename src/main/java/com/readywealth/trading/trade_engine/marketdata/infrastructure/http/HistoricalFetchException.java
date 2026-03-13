package com.readywealth.trading.trade_engine.marketdata.infrastructure.http;

public class HistoricalFetchException extends RuntimeException {
    private final boolean transientFailure;

    public HistoricalFetchException(String message, boolean transientFailure) {
        super(message);
        this.transientFailure = transientFailure;
    }

    public HistoricalFetchException(String message, Throwable cause, boolean transientFailure) {
        super(message, cause);
        this.transientFailure = transientFailure;
    }

    public boolean isTransientFailure() {
        return transientFailure;
    }
}
