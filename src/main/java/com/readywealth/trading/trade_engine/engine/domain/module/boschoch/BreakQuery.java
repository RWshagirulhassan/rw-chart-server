package com.readywealth.trading.trade_engine.engine.domain.module.boschoch;

public record BreakQuery(
        BreakClass cls,
        BreakDir dir,
        Integer fromBar,
        Integer toBar,
        Integer limit) {
    public static BreakQuery all() {
        return new BreakQuery(null, null, null, null, null);
    }

    public BreakQuery only(BreakClass c) {
        return new BreakQuery(c, dir, fromBar, toBar, limit);
    }

    public BreakQuery dir(BreakDir d) {
        return new BreakQuery(cls, d, fromBar, toBar, limit);
    }

    public BreakQuery between(int from, int to) {
        return new BreakQuery(cls, dir, from, to, limit);
    }

    public BreakQuery limit(int n) {
        return new BreakQuery(cls, dir, fromBar, toBar, n);
    }
}
