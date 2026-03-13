package com.readywealth.trading.trade_engine.execution.application.port;

import com.readywealth.trading.trade_engine.execution.domain.LinkedExitSummary;
import com.readywealth.trading.trade_engine.execution.domain.OrderAttachments;
import com.readywealth.trading.trade_engine.execution.domain.OrderDraft;
import com.readywealth.trading.trade_engine.execution.domain.TradingAccount;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrder;

public interface ExitPlanOrchestratorPort {

    record PlaceOrderWithLinkedExitResult(
            TradingOrder order,
            LinkedExitSummary linkedExit) {
    }

    PlaceOrderWithLinkedExitResult placeOrderWithLinkedExit(
            TradingAccount account,
            OrderDraft entryDraft,
            OrderAttachments attachments);
}
