package com.readywealth.trading.trade_engine.execution.api.http.dto;

import com.readywealth.trading.trade_engine.execution.domain.ExitPlan;

public record ExitPlanResponse(
        String exitPlanId,
        String accountId,
        String entryOrderId,
        long instrumentToken,
        String tradingsymbol,
        String exchange,
        String product,
        String entrySide,
        int plannedQty,
        String status,
        String armPolicy,
        String mode,
        String ocoGroupId,
        ExitLegResponse takeProfit,
        ExitLegResponse stopLoss,
        Integer rearmRequiredQty,
        String rearmReason,
        long createdAtEpochMs,
        long updatedAtEpochMs) {

    public static ExitPlanResponse from(ExitPlan exitPlan) {
        return new ExitPlanResponse(
                exitPlan.exitPlanId(),
                exitPlan.accountId(),
                exitPlan.entryOrderId(),
                exitPlan.instrumentToken(),
                exitPlan.tradingsymbol(),
                exitPlan.exchange(),
                exitPlan.product().name(),
                exitPlan.entrySide().name(),
                exitPlan.plannedQty(),
                exitPlan.status().name(),
                exitPlan.armPolicy().name(),
                exitPlan.mode() == null ? null : exitPlan.mode().name(),
                exitPlan.ocoGroupId(),
                ExitLegResponse.from(exitPlan.takeProfit()),
                ExitLegResponse.from(exitPlan.stopLoss()),
                exitPlan.rearmRequiredQty(),
                exitPlan.rearmReason(),
                exitPlan.createdAtEpochMs(),
                exitPlan.updatedAtEpochMs());
    }
}
