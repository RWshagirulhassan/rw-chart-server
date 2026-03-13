package com.readywealth.trading.trade_engine.engine.domain.module;

import com.readywealth.trading.trade_engine.engine.domain.series.BarSeriesContext;

public interface Module {

    /**
     * Called incrementally for each new bar index.
     * IMPORTANT: For "right-strength" patterns, you can confirm pivots with delay
     * (e.g., confirm i-rightStrength when bar i arrives).
     */
    void onBar(BarSeriesContext ctx, int index);

    /**
     * Called when series evicts old bars (live maxBarCount).
     * newBeginIndex is the current series.getBeginIndex().
     */
    default void onEvict(BarSeriesContext ctx, int newBeginIndex) {
        // no-op by default
    }

    /**
     * Called when a bar is replaced (e.g., last candle revision) and cached state
     * from index
     * onward must be recomputed.
     */
    default void onInvalidateFrom(BarSeriesContext ctx, int index) {
        // no-op by default
    }
}
