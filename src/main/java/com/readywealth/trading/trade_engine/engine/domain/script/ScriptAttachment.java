package com.readywealth.trading.trade_engine.engine.domain.script;

import com.readywealth.trading.trade_engine.engine.application.runtime.ScriptAttachmentState;
import com.readywealth.trading.trade_engine.engine.application.runtime.ScriptExecutionMode;

public record ScriptAttachment(
        ScriptInstanceId instanceId,
        ScriptId scriptId,
        ScriptKind kind,
        ScriptParams rawParams,
        Object typedParams,
        ScriptRuntime runtime,
        long attachSequence,
        ScriptExecutionMode executionMode,
        ScriptAttachmentState lifecycleState,
        String ownerSessionId,
        String ownerUserId) {

    public ScriptAttachment(
            ScriptInstanceId instanceId,
            ScriptId scriptId,
            ScriptKind kind,
            ScriptParams rawParams,
            Object typedParams,
            ScriptRuntime runtime,
            long attachSequence) {
        this(
                instanceId,
                scriptId,
                kind,
                rawParams,
                typedParams,
                runtime,
                attachSequence,
                ScriptExecutionMode.ON_CANDLE_CLOSE,
                ScriptAttachmentState.ACTIVE,
                null,
                null);
    }

    public ScriptAttachment withLifecycleState(ScriptAttachmentState nextState) {
        return new ScriptAttachment(
                instanceId,
                scriptId,
                kind,
                rawParams,
                typedParams,
                runtime,
                attachSequence,
                executionMode,
                nextState,
                ownerSessionId,
                ownerUserId);
    }
}
