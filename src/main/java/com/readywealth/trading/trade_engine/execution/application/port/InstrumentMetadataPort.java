package com.readywealth.trading.trade_engine.execution.application.port;

import java.util.Optional;

public interface InstrumentMetadataPort {
    Optional<InstrumentMeta> resolve(long instrumentToken);

    record InstrumentMeta(long instrumentToken, String tradingsymbol, String exchange) {
    }
}
