package com.readywealth.trading.trade_engine.session.domain;

public class SessionForbiddenException extends RuntimeException {
    public SessionForbiddenException(String message) {
        super(message);
    }
}
