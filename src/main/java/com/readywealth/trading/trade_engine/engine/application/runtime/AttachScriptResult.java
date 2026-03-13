package com.readywealth.trading.trade_engine.engine.application.runtime;

import com.readywealth.trading.trade_engine.engine.domain.script.ScriptAttachment;

public record AttachScriptResult(
        ScriptAttachment attachment,
        String bootstrapJobId,
        long attachAcceptedAtEpochMs) {
}
