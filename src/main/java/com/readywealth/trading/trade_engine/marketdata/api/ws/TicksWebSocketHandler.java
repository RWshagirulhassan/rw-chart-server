package com.readywealth.trading.trade_engine.marketdata.api.ws;

import com.readywealth.trading.trade_engine.session.application.SessionIdentityService;
import com.readywealth.trading.trade_engine.session.application.SessionSeriesCoordinator;
import com.readywealth.trading.trade_engine.session.domain.UiSession;
import com.readywealth.trading.trade_engine.session.domain.UiSessionId;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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
public class TicksWebSocketHandler extends TextWebSocketHandler {
	private static final Logger log = LoggerFactory.getLogger(TicksWebSocketHandler.class);

	private final Map<String, UiSessionId> wsToUiSession = new ConcurrentHashMap<>();
	private final Map<UiSessionId, Set<WebSocketSession>> uiSessionSockets = new ConcurrentHashMap<>();
	private final ObjectProvider<SessionSeriesCoordinator> sessionSeriesCoordinatorProvider;
	private final SessionIdentityService sessionIdentityService;
	private final ObjectMapper objectMapper;
	private final Counter sentCounter;
	private final Counter broadcastErrorCounter;

	public TicksWebSocketHandler(
			ObjectProvider<SessionSeriesCoordinator> sessionSeriesCoordinatorProvider,
			SessionIdentityService sessionIdentityService,
			ObjectMapper objectMapper,
			MeterRegistry meterRegistry) {
		this.sessionSeriesCoordinatorProvider = sessionSeriesCoordinatorProvider;
		this.sessionIdentityService = sessionIdentityService;
		this.objectMapper = objectMapper;
		this.sentCounter = meterRegistry.counter("ws_ui_messages_sent");
		this.broadcastErrorCounter = meterRegistry.counter("ws_ui_broadcast_errors");
		Gauge.builder("ws_ui_sessions", wsToUiSession, Map::size).register(meterRegistry);
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		log.info("ws_ui_connect_attempt wsSessionId={} uri={}", session.getId(), session.getUri());
		String rawSessionId = resolveQueryParam(session, "sessionId");
		if (rawSessionId == null || rawSessionId.isBlank()) {
			reject(session, CloseStatus.POLICY_VIOLATION, "sessionId query param is required");
			return;
		}
		try {
			String userId = sessionIdentityService.requireCurrentUserId();
			SessionSeriesCoordinator sessionSeriesCoordinator = sessionSeriesCoordinatorProvider.getIfAvailable();
			if (sessionSeriesCoordinator == null) {
				reject(session, CloseStatus.SERVER_ERROR, "Session coordinator unavailable");
				return;
			}
			UiSession uiSession = sessionSeriesCoordinator.onWsConnected(userId, new UiSessionId(rawSessionId));
			UiSessionId uiSessionId = uiSession.sessionId();
			wsToUiSession.put(session.getId(), uiSessionId);
			uiSessionSockets.computeIfAbsent(uiSessionId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
			log.info("ws_ui_connected wsSessionId={} uiSessionId={} totalConnections={}",
					session.getId(), rawSessionId, wsToUiSession.size());
			sendRaw(session, toEvent("meta", Map.of(
					"sessionId", uiSessionId.value(),
					"userId", uiSession.userId(),
					"destroyOnClose", uiSession.destroyOnClose(),
					"connectedClients", uiSession.connectedClients(),
					"state", uiSession.state().name(),
					"message", "connected")));
		} catch (Exception ex) {
			reject(session, CloseStatus.POLICY_VIOLATION, ex.getMessage());
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		UiSessionId uiSessionId = wsToUiSession.remove(session.getId());
		if (uiSessionId != null) {
			Set<WebSocketSession> set = uiSessionSockets.get(uiSessionId);
			if (set != null) {
				set.remove(session);
				if (set.isEmpty()) {
					uiSessionSockets.remove(uiSessionId, set);
				}
			}
			notifyCoordinatorOnWsDisconnect(
					"after_connection_closed",
					session.getId(),
					uiSessionId,
					status != null ? status.getCode() : null,
					status != null ? status.getReason() : null);
		}
		log.info("ws_ui_disconnected wsSessionId={} uiSessionId={} code={} reason='{}' totalConnections={}",
				session.getId(), uiSessionId != null ? uiSessionId.value() : "na",
				status != null ? status.getCode() : -1,
				status != null ? status.getReason() : "",
				wsToUiSession.size());
	}

	public void broadcastToUiSession(UiSessionId uiSessionId, String payload) {
		Set<WebSocketSession> targets = uiSessionSockets.get(uiSessionId);
		if (targets == null || targets.isEmpty()) {
			return;
		}
		for (WebSocketSession socket : targets) {
			sendSafely(socket, payload, uiSessionId);
		}
	}

	public void broadcastToUiSessions(Set<UiSessionId> sessionIds, String payload) {
		for (UiSessionId sessionId : sessionIds) {
			broadcastToUiSession(sessionId, payload);
		}
	}

	public void broadcastToAllBound(String payload) {
		for (Set<WebSocketSession> sockets : uiSessionSockets.values()) {
			for (WebSocketSession socket : sockets) {
				sendSafely(socket, payload, wsToUiSession.get(socket.getId()));
			}
		}
	}

	private void sendSafely(WebSocketSession session, String payload, UiSessionId uiSessionId) {
		if (!session.isOpen()) {
			cleanupSocket(session, uiSessionId);
			return;
		}
		try {
			sendRaw(session, payload);
			sentCounter.increment();
		} catch (IOException ex) {
			broadcastErrorCounter.increment();
			log.warn("ws_ui_broadcast_failed wsSessionId={} uiSessionId={} reason='{}'",
					session.getId(), uiSessionId != null ? uiSessionId.value() : "na", ex.getMessage());
			cleanupSocket(session, uiSessionId);
		}
	}

	private void cleanupSocket(WebSocketSession session, UiSessionId uiSessionId) {
		wsToUiSession.remove(session.getId());
		if (uiSessionId != null) {
			Set<WebSocketSession> sockets = uiSessionSockets.get(uiSessionId);
			if (sockets != null) {
				sockets.remove(session);
				if (sockets.isEmpty()) {
					uiSessionSockets.remove(uiSessionId, sockets);
				}
			}
			notifyCoordinatorOnWsDisconnect("cleanup_socket", session.getId(), uiSessionId, null, null);
		}
		try {
			session.close(CloseStatus.SERVER_ERROR);
		} catch (IOException ignored) {
		}
	}

	private void notifyCoordinatorOnWsDisconnect(
			String source,
			String wsSessionId,
			UiSessionId uiSessionId,
			Integer closeCode,
			String closeReason) {
		SessionSeriesCoordinator sessionSeriesCoordinator = sessionSeriesCoordinatorProvider.getIfAvailable();
		if (sessionSeriesCoordinator == null) {
			log.warn("ws_ui_disconnect_coordinator_missing source={} wsSessionId={} uiSessionId={} closeCode={} closeReason='{}'",
					source,
					wsSessionId,
					uiSessionId != null ? uiSessionId.value() : "na",
					closeCode,
					closeReason);
			return;
		}
		try {
			sessionSeriesCoordinator.onWsDisconnected(uiSessionId);
		} catch (Exception ex) {
			log.error("ws_ui_disconnect_callback_failed source={} wsSessionId={} uiSessionId={} closeCode={} closeReason='{}' reason={}",
					source,
					wsSessionId,
					uiSessionId != null ? uiSessionId.value() : "na",
					closeCode,
					closeReason,
					ex.getMessage(),
					ex);
		}
	}

	private void reject(WebSocketSession session, CloseStatus status, String message) {
		try {
			log.warn("ws_ui_reject wsSessionId={} code={} reason='{}'",
					session.getId(), status.getCode(), message);
			sendRaw(session, toEvent("error", Map.of("message", message)));
			session.close(status);
		} catch (Exception ignored) {
		}
	}

	private String resolveQueryParam(WebSocketSession session, String key) {
		if (session.getUri() == null) {
			return null;
		}
		return UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams().getFirst(key);
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
