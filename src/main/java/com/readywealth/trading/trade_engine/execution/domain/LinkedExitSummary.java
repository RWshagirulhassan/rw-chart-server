package com.readywealth.trading.trade_engine.execution.domain;

public record LinkedExitSummary(
        String exitPlanId,
        ExitPlanStatus status,
        ExitPlanMode mode,
        ArmPolicy armPolicy) {

    public static LinkedExitSummary from(ExitPlan exitPlan) {
        if (exitPlan == null) {
            return null;
        }
        return new LinkedExitSummary(
                exitPlan.exitPlanId(),
                exitPlan.status(),
                exitPlan.mode(),
                exitPlan.armPolicy());
    }
}
