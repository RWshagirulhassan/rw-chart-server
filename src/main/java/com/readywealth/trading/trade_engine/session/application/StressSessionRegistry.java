package com.readywealth.trading.trade_engine.session.application;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StressSessionRegistry {
    private final Set<String> stressSessionIds = ConcurrentHashMap.newKeySet();

    public void markStressSession(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            stressSessionIds.add(sessionId);
        }
    }

    public void unmarkStressSession(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            stressSessionIds.remove(sessionId);
        }
    }

    public boolean isStressSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        return stressSessionIds.contains(sessionId);
    }
}
