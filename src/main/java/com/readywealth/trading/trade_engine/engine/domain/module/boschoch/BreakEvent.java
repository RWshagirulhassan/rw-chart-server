package com.readywealth.trading.trade_engine.engine.domain.module.boschoch;

public record BreakEvent(
        int barIndex,
        int length,
        BreakClass cls,
        BreakDir dir,
        String brokenSwingId,
        int pivotIndex,
        String levelPrice) {
}
