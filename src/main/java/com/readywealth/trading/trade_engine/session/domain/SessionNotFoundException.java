package com.readywealth.trading.trade_engine.session.domain;

public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(String message) {
        super(message);
    }
}
