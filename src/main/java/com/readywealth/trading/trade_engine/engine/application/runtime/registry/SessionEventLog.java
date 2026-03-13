package com.readywealth.trading.trade_engine.engine.application.runtime.registry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.readywealth.trading.trade_engine.engine.application.runtime.model.DeltaResponse;
import com.readywealth.trading.trade_engine.engine.application.runtime.model.SessionEvent;

public final class SessionEventLog {

    private final AtomicLong seqCounter = new AtomicLong(0);
    private final List<SessionEvent> events = new ArrayList<>();

    public synchronized long append(String type, Object payload) {
        long seq = seqCounter.incrementAndGet();
        events.add(new SessionEvent(seq, Instant.now().toEpochMilli(), type, payload));
        return seq;
    }

    public synchronized DeltaResponse deltasSince(long sinceSeq, int limit) {
        int safeLimit = limit <= 0 ? 100 : limit;
        List<SessionEvent> out = new ArrayList<>();
        for (SessionEvent event : events) {
            if (event.seq() > sinceSeq) {
                out.add(event);
                if (out.size() >= safeLimit) {
                    break;
                }
            }
        }
        long nextSeq = out.isEmpty() ? sinceSeq : out.get(out.size() - 1).seq();
        return new DeltaResponse(nextSeq, Collections.unmodifiableList(out));
    }

    public synchronized long lastSeq() {
        return seqCounter.get();
    }

    public synchronized void clear() {
        events.clear();
        seqCounter.set(0);
    }
}
