package com.readywealth.trading.trade_engine.engine.application.runtime;

import java.util.List;

import com.readywealth.trading.trade_engine.engine.domain.script.ScriptAttachment;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptExecutionContext;
import com.readywealth.trading.trade_engine.engine.domain.series.BarSeriesContext;

public final class UnifiedBarProgressionEngine {

    public void advance(
            BarSeriesContext seriesContext,
            List<ScriptAttachment> orderedAttachments,
            ScriptExecutionContextFactory executionContextFactory) {
        int end = seriesContext.series().getEndIndex();
        if (end < seriesContext.series().getBeginIndex()) {
            return;
        }

        seriesContext.context().ensureComputedUpTo(end);
        for (ScriptAttachment attachment : orderedAttachments) {
            ScriptExecutionContext executionContext = executionContextFactory.forAttachment(attachment);
            attachment.runtime().onBar(executionContext, end);
        }
    }

    @FunctionalInterface
    public interface ScriptExecutionContextFactory {
        ScriptExecutionContext forAttachment(ScriptAttachment attachment);
    }
}
