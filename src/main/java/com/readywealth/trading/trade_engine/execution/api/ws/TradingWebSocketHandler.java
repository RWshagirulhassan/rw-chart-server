package com.readywealth.trading.trade_engine.execution.api.ws;

import com.readywealth.trading.trade_engine.execution.application.service.TradingAccountService;
import com.readywealth.trading.trade_engine.session.application.SessionIdentityService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TradingWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TradingWebSocketHandler.class);

    private final SessionIdentityService sessionIdentityService;
    private final TradingAccountService tradingAccountService;
    private final ObjectMapper objectMapper;
    private final Counter sentCounter;
    private final Counter errorCounter;

    private final Map<String, String> wsToAccount = new ConcurrentHashMap<>();
    private final Map<String, Set<WebSocketSession>> accountSockets = new ConcurrentHashMap<>();

    public TradingWebSocketHandler(
            SessionIdentityService sessionIdentityService,
            TradingAccountService tradingAccountService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.sessionIdentityService = sessionIdentityService;
        this.tradingAccountService = tradingAccountService;
        this.objectMapper = objectMapper;
        this.sentCounter = meterRegistry.counter("trading_ws_messages_sent");
        this.errorCounter = meterRegistry.counter("trading_ws_broadcast_errors");
        Gauge.builder("trading_ws_connections", wsToAccount, Map::size).register(meterRegistry);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String accountId = resolveQueryParam(session, "accountId");
        if (accountId == null || accountId.isBlank()) {
            reject(session, "accountId query param is required");
            return;
        }
        try {
            String userId = sessionIdentityService.requireCurrentUserId();
            tradingAccountService.requireOwnedAccount(accountId, userId);
            wsToAccount.put(session.getId(), accountId);
            accountSockets.computeIfAbsent(accountId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
            sendRaw(session, toEvent("meta", Map.of(
                    "accountId", accountId,
                    "message", "connected")));
        } catch (Exception ex) {
            reject(session, ex.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        cleanup(session);
    }

    public void broadcastToAccount(String accountId, String payload) {
        Set<WebSocketSession> sockets = accountSockets.get(accountId);
        if (sockets == null || sockets.isEmpty()) {
            return;
        }
        for (WebSocketSession socket : sockets) {
            if (!socket.isOpen()) {
                cleanup(socket);
                continue;
            }
            try {
                sendRaw(socket, payload);
                sentCounter.increment();
            } catch (Exception ex) {
                errorCounter.increment();
                log.warn("trading_ws_broadcast_failed accountId={} reason='{}'", accountId, ex.getMessage());
                cleanup(socket);
            }
        }
    }

    private void cleanup(WebSocketSession session) {
        String accountId = wsToAccount.remove(session.getId());
        if (accountId == null) {
            return;
        }
        Set<WebSocketSession> sockets = accountSockets.get(accountId);
        if (sockets != null) {
            sockets.remove(session);
            if (sockets.isEmpty()) {
                accountSockets.remove(accountId, sockets);
            }
        }
    }

    private String resolveQueryParam(WebSocketSession session, String key) {
        if (session.getUri() == null) {
            return null;
        }
        return UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams().getFirst(key);
    }

    private void reject(WebSocketSession session, String message) {
        try {
            sendRaw(session, toEvent("error", Map.of("message", message)));
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (Exception ignored) {
            // ignored
        }
    }

    private String toEvent(String type, Object data) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "type", type,
                "ts", System.currentTimeMillis(),
                "data", data));
    }

    private void sendRaw(WebSocketSession session, String payload) throws IOException {
        session.sendMessage(new TextMessage(payload));
    }
}
