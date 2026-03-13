package com.readywealth.trading.trade_engine.engine.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.readywealth.trading.trade_engine.engine.application.runtime.registry.SessionEventLog;

class SessionEventLogTest {

    @Test
    void keepsMonotonicSequenceAndCursorDeltas() {
        SessionEventLog log = new SessionEventLog();
        log.append("A", "x");
        log.append("B", "y");
        log.append("C", "z");

        var first = log.deltasSince(0, 2);
        assertEquals(2, first.events().size());
        assertEquals(2L, first.nextSeq());

        var second = log.deltasSince(first.nextSeq(), 10);
        assertEquals(1, second.events().size());
        assertEquals(3L, second.nextSeq());
    }
}
