package com.readywealth.trading.trade_engine.session.application;

import com.readywealth.trading.trade_engine.auth.domain.TokenRecord;
import com.readywealth.trading.trade_engine.auth.infrastructure.redis.TokenStore;
import com.readywealth.trading.trade_engine.engine.domain.series.IntervalKind;
import com.readywealth.trading.trade_engine.engine.application.runtime.SeriesKeyCodec;
import com.readywealth.trading.trade_engine.engine.application.runtime.SeriesRuntimeManager;
import com.readywealth.trading.trade_engine.session.domain.UiSession;
import com.readywealth.trading.trade_engine.session.domain.UiSessionId;
import com.readywealth.trading.trade_engine.session.infrastructure.config.StressSessionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class StressSessionStartupService {
    private static final Logger log = LoggerFactory.getLogger(StressSessionStartupService.class);
    private static final String FIXED_TIMEFRAME = IntervalKind.TIME_1M.name();

    private final StressSessionProperties properties;
    private final TokenStore tokenStore;
    private final SessionSeriesCoordinator sessionSeriesCoordinator;
    private final SeriesRuntimeManager runtimeManager;
    private final StressInstrumentResolver instrumentResolver;
    private final StressSessionRegistry stressSessionRegistry;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile String activeSessionId;
    private volatile String activeUserId;
    private volatile boolean noInstrumentLogEmitted;

    public StressSessionStartupService(
            StressSessionProperties properties,
            TokenStore tokenStore,
            SessionSeriesCoordinator sessionSeriesCoordinator,
            SeriesRuntimeManager runtimeManager,
            StressInstrumentResolver instrumentResolver,
            StressSessionRegistry stressSessionRegistry) {
        this.properties = properties;
        this.tokenStore = tokenStore;
        this.sessionSeriesCoordinator = sessionSeriesCoordinator;
        this.runtimeManager = runtimeManager;
        this.instrumentResolver = instrumentResolver;
        this.stressSessionRegistry = stressSessionRegistry;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        attemptStartup("app_ready");
    }

    @Scheduled(fixedDelayString = "${session.stress.poll-interval-ms:5000}")
    public void pollAndAttemptStartup() {
        attemptStartup("scheduled_poll");
    }

    private void attemptStartup(String trigger) {
        if (!properties.isEnabled()) {
            return;
        }
        if (!lock.tryLock()) {
            return;
        }
        try {
            TokenRecord token = tokenStore.get().orElse(null);
            if (token == null || token.expired() || token.userId() == null || token.userId().isBlank()) {
                return;
            }
            String userId = token.userId().trim();
            if (hasActiveSession(userId)) {
                return;
            }

            List<Long> instrumentTokens = instrumentResolver.resolveInstrumentTokens(properties);
            if (instrumentTokens.isEmpty()) {
                if (!noInstrumentLogEmitted) {
                    log.warn("stress_session_start_skipped trigger={} reason=no_instruments_found", trigger);
                    noInstrumentLogEmitted = true;
                }
                return;
            }
            noInstrumentLogEmitted = false;

            List<String> seriesKeys = new ArrayList<>(instrumentTokens.size());
            for (Long tokenValue : instrumentTokens) {
                seriesKeys.add(tokenValue + "@" + FIXED_TIMEFRAME);
            }

            UiSession session = sessionSeriesCoordinator.createSession(
                    userId,
                    seriesKeys,
                    properties.resolvedMaxBarCount(),
                    properties.isDestroyOnClose(),
                    true);

            activeSessionId = session.sessionId().value();
            activeUserId = userId;

            log.info(
                    "stress_session_started trigger={} sessionId={} userId={} seriesCount={} destroyOnClose={} source={}",
                    trigger,
                    session.sessionId().value(),
                    userId,
                    session.attachedSeries().size(),
                    session.destroyOnClose(),
                    properties.normalizedInstrumentTokens().isEmpty()
                            ? "redis_nfo_name_to_nse_symbol_match"
                            : "configured_tokens");

            if (properties.hasScript()) {
                attachScreenerScript(session);
            }
        } catch (Exception e) {
            log.error("stress_session_start_failed trigger={} error={}", trigger, e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    private boolean hasActiveSession(String userId) {
        if (activeSessionId == null || activeUserId == null || !activeUserId.equals(userId)) {
            return false;
        }
        try {
            sessionSeriesCoordinator.getSession(userId, new UiSessionId(activeSessionId));
            return true;
        } catch (RuntimeException ignored) {
            activeSessionId = null;
            activeUserId = null;
            return false;
        }
    }

    private void attachScreenerScript(UiSession session) {
        String scriptId = properties.resolvedScriptId();
        Map<String, Object> scriptParams = properties.resolvedScriptParams();
        var executionMode = properties.resolvedScriptExecutionMode();

        for (var key : session.attachedSeries()) {
            String encodedSeriesKey = SeriesKeyCodec.encode(key);
            try {
                var result = runtimeManager.attachScript(
                        key,
                        session.sessionId().value(),
                        session.userId(),
                        scriptId,
                        scriptParams,
                        executionMode,
                        ready -> CompletableFuture.runAsync(() -> {
                            try {
                                var ack = runtimeManager.markScriptSnapshotDelivered(
                                        key,
                                        session.sessionId().value(),
                                        ready.scriptInstanceId(),
                                        ready.snapshotCursorSeq());
                                log.info(
                                        "stress_script_snapshot_acked sessionId={} seriesKey={} scriptInstanceId={} status={} activated={} replayedEvents={} fromSeq={} toSeq={}",
                                        session.sessionId().value(),
                                        encodedSeriesKey,
                                        ready.scriptInstanceId(),
                                        ready.status().name(),
                                        ack.activated(),
                                        ack.replayedEvents(),
                                        ack.fromSeq(),
                                        ack.toSeq());
                            } catch (Exception ackError) {
                                log.warn(
                                        "stress_script_snapshot_ack_failed sessionId={} seriesKey={} scriptInstanceId={} status={} error={}",
                                        session.sessionId().value(),
                                        encodedSeriesKey,
                                        ready.scriptInstanceId(),
                                        ready.status().name(),
                                        ackError.getMessage(),
                                        ackError);
                            }
                        }));
                log.info(
                        "stress_script_attach_accepted sessionId={} seriesKey={} scriptId={} scriptInstanceId={} executionMode={} bootstrapJobId={}",
                        session.sessionId().value(),
                        encodedSeriesKey,
                        result.attachment().scriptId().value(),
                        result.attachment().instanceId().value(),
                        result.attachment().executionMode().name(),
                        result.bootstrapJobId());
            } catch (Exception e) {
                log.warn(
                        "stress_script_attach_failed sessionId={} seriesKey={} scriptId={} executionMode={} error={}",
                        session.sessionId().value(),
                        encodedSeriesKey,
                        scriptId,
                        executionMode.name(),
                        e.getMessage(),
                        e);
            }
        }
    }
}
