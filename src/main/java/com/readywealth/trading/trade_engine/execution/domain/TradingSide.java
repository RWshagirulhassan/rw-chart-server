package com.readywealth.trading.trade_engine.execution.domain;

public enum TradingSide {
    BUY(1),
    SELL(-1);

    private final int sign;

    TradingSide(int sign) {
        this.sign = sign;
    }

    public int sign() {
        return sign;
    }
}
