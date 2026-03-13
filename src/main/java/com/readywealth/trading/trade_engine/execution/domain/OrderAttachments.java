package com.readywealth.trading.trade_engine.execution.domain;

public record OrderAttachments(
        ArmPolicy armPolicy,
        ExitLegSpec takeProfit,
        ExitLegSpec stopLoss) {

    public boolean hasTakeProfit() {
        return takeProfit != null;
    }

    public boolean hasStopLoss() {
        return stopLoss != null;
    }

    public boolean empty() {
        return takeProfit == null && stopLoss == null;
    }

    public ExitPlanMode mode() {
        if (hasTakeProfit() && hasStopLoss()) {
            return ExitPlanMode.OCO;
        }
        if (hasTakeProfit()) {
            return ExitPlanMode.TP_ONLY;
        }
        if (hasStopLoss()) {
            return ExitPlanMode.SL_ONLY;
        }
        return null;
    }
}
