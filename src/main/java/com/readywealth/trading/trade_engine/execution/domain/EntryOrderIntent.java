package com.readywealth.trading.trade_engine.execution.domain;

public record EntryOrderIntent(
        String accountId,
        OrderDraft entryDraft,
        OrderAttachments attachments) {
}
