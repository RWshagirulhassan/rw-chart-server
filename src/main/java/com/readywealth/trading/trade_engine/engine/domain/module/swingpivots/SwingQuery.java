package com.readywealth.trading.trade_engine.engine.domain.module.swingpivots;

public record SwingQuery(
        SwingSide side,
        SwingLabel label,
        Integer fromBar,
        Integer toBar,
        Integer limit) {
    public static SwingQuery all() {
        return new SwingQuery(null, null, null, null, null);
    }

    public SwingQuery only(SwingLabel l) {
        return new SwingQuery(side, l, fromBar, toBar, limit);
    }

    public SwingQuery side(SwingSide s) {
        return new SwingQuery(s, label, fromBar, toBar, limit);
    }

    public SwingQuery between(int from, int to) {
        return new SwingQuery(side, label, from, to, limit);
    }

    public SwingQuery limit(int n) {
        return new SwingQuery(side, label, fromBar, toBar, n);
    }
}
