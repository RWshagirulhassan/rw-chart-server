package com.readywealth.trading.trade_engine.execution.domain;

public final class OrderTimelineIds {

    private OrderTimelineIds() {
    }

    public static String entryRowId(String orderId) {
        return "order:" + orderId;
    }

    public static String linkedRowId(String exitPlanId, ExitLegType legType) {
        String suffix = legType == ExitLegType.TAKE_PROFIT ? "TP" : "SL";
        return "lexit:" + exitPlanId + ":" + suffix;
    }
}
