package com.readywealth.trading.trade_engine.session.infrastructure.memory;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SeriesRefManager {

    private final Map<Long, AtomicInteger> instrumentRefs = new ConcurrentHashMap<>();

    public RefTransition incrementInstrumentRef(long instrumentToken) {
        AtomicInteger count = instrumentRefs.computeIfAbsent(instrumentToken, ignored -> new AtomicInteger(0));
        int previous = count.getAndIncrement();
        int current = previous + 1;
        return new RefTransition(instrumentToken, previous, current, previous == 0, false);
    }

    public RefTransition decrementInstrumentRef(long instrumentToken) {
        AtomicInteger count = instrumentRefs.get(instrumentToken);
        if (count == null) {
            return new RefTransition(instrumentToken, 0, 0, false, false);
        }
        int previous = count.get();
        int current = count.updateAndGet(v -> Math.max(0, v - 1));
        boolean toZero = previous > 0 && current == 0;
        if (toZero) {
            instrumentRefs.remove(instrumentToken, count);
        }
        return new RefTransition(instrumentToken, previous, current, false, toZero);
    }

    public int activeInstruments() {
        return instrumentRefs.size();
    }

    public int totalInstrumentRefs() {
        return instrumentRefs.values().stream().mapToInt(AtomicInteger::get).sum();
    }

    public record RefTransition(
            long instrumentToken,
            int previous,
            int current,
            boolean becameActive,
            boolean becameInactive) {
    }
}
