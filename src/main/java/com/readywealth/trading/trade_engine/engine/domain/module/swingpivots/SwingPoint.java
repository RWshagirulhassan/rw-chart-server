package com.readywealth.trading.trade_engine.engine.domain.module.swingpivots;

public record SwingPoint(
        String id,
        int length,
        int pivotIndex,
        SwingSide side,
        SwingLabel label,
        String price) {
}
