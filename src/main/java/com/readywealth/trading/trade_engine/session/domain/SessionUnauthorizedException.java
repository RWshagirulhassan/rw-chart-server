package com.readywealth.trading.trade_engine.session.domain;

public class SessionUnauthorizedException extends RuntimeException {
    public SessionUnauthorizedException(String message) {
        super(message);
    }
}
