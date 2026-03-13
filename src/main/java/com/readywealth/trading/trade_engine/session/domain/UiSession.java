package com.readywealth.trading.trade_engine.session.domain;

import com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class UiSession {
    private final UiSessionId sessionId;
    private final String userId;
    private final boolean destroyOnClose;
    private final Set<SeriesKey> attachedSeries = new LinkedHashSet<>();
    private final Instant createdAt;
    private final AtomicInteger connectedClients = new AtomicInteger(0);
    private Instant updatedAt;
    private Instant lastSeenAt;
    private SessionState state;

    public UiSession(UiSessionId sessionId, String userId, boolean destroyOnClose) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.destroyOnClose = destroyOnClose;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.lastSeenAt = this.createdAt;
        this.state = SessionState.RETAINED;
    }

    public UiSessionId sessionId() {
        return sessionId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String userId() {
        return userId;
    }

    public boolean destroyOnClose() {
        return destroyOnClose;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant lastSeenAt() {
        return lastSeenAt;
    }

    public SessionState state() {
        return state;
    }

    public int connectedClients() {
        return connectedClients.get();
    }

    public Set<SeriesKey> attachedSeries() {
        return Collections.unmodifiableSet(attachedSeries);
    }

    public boolean attach(SeriesKey key) {
        boolean changed = attachedSeries.add(key);
        if (changed) {
            touch();
        }
        return changed;
    }

    public boolean detach(SeriesKey key) {
        boolean changed = attachedSeries.remove(key);
        if (changed) {
            touch();
        }
        return changed;
    }

    public void touch() {
        Instant now = Instant.now();
        updatedAt = now;
        lastSeenAt = now;
    }

    public int incrementConnectedClients() {
        touch();
        int current = connectedClients.incrementAndGet();
        state = SessionState.RUNNING;
        return current;
    }

    public int decrementConnectedClients() {
        touch();
        return connectedClients.updateAndGet(value -> Math.max(0, value - 1));
    }

    public void setState(SessionState state) {
        this.state = state;
        touch();
    }
}
