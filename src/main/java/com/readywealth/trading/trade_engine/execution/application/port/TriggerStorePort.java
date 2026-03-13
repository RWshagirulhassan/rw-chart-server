package com.readywealth.trading.trade_engine.execution.application.port;

import com.readywealth.trading.trade_engine.execution.domain.ExitPlan;
import com.readywealth.trading.trade_engine.execution.domain.ExitPlanStatus;
import com.readywealth.trading.trade_engine.execution.domain.OcoGroup;
import com.readywealth.trading.trade_engine.execution.domain.Trigger;
import com.readywealth.trading.trade_engine.execution.domain.TriggerFireResult;
import com.readywealth.trading.trade_engine.execution.domain.TriggerStatus;

import java.util.List;
import java.util.Optional;

public interface TriggerStorePort {

    ExitPlan saveExitPlan(ExitPlan exitPlan);

    Optional<ExitPlan> findExitPlan(String accountId, String exitPlanId);

    List<ExitPlan> listExitPlans(String accountId, ExitPlanStatus status);

    List<ExitPlan> findPendingExitPlansByEntryOrder(String accountId, String entryOrderId);

    Trigger saveTrigger(Trigger trigger);

    List<Trigger> saveTriggers(List<Trigger> triggers);

    Optional<Trigger> findTrigger(String accountId, String triggerId);

    List<Trigger> listTriggers(String accountId, TriggerStatus status);

    List<Trigger> listActiveTriggers(String accountId);

    List<Trigger> listActiveTriggersByInstrument(String accountId, long instrumentToken);

    void updateTriggerLastObservedLtp(String accountId, String triggerId, double ltp, long updatedAtEpochMs);

    TriggerFireResult fireTriggerAtomically(String accountId, String triggerId, double ltp, long firedAtEpochMs);

    Trigger markTriggerOrderAttempt(
            String accountId,
            String triggerId,
            String orderId,
            TriggerStatus status,
            String rejectionReason,
            long updatedAtEpochMs);

    Trigger markTriggerCompleted(String accountId, String triggerId, long updatedAtEpochMs);

    OcoGroup saveOcoGroup(OcoGroup ocoGroup);

    Optional<OcoGroup> findOcoGroup(String accountId, String ocoGroupId);

    void cancelExitPlan(String accountId, String exitPlanId, long updatedAtEpochMs, String reason);

    void markExitPlanRearmRequired(String accountId, String exitPlanId, int remainingQty, String reason, long updatedAtEpochMs);
}
