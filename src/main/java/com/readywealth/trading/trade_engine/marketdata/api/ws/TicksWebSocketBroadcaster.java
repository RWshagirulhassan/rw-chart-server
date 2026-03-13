package com.readywealth.trading.trade_engine.marketdata.api.ws;

import com.readywealth.trading.trade_engine.session.domain.UiSessionId;
import com.readywealth.trading.trade_engine.marketdata.domain.TypedTick;
import com.readywealth.trading.trade_engine.marketdata.api.ws.dto.BootstrapLifecycleEvent;
import com.readywealth.trading.trade_engine.marketdata.api.ws.dto.ScriptAttachCatchupWsEvent;
import com.readywealth.trading.trade_engine.marketdata.api.ws.dto.ScriptDeltaWsEvent;
import com.readywealth.trading.trade_engine.marketdata.api.ws.dto.ScriptSnapshotReadyWsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class TicksWebSocketBroadcaster {
	private static final Logger log = LoggerFactory.getLogger(TicksWebSocketBroadcaster.class);

	private final TicksWebSocketHandler handler;
	private final ObjectMapper objectMapper;

	public TicksWebSocketBroadcaster(TicksWebSocketHandler handler, ObjectMapper objectMapper) {
		this.handler = handler;
		this.objectMapper = objectMapper;
	}

	public void broadcastTickToSessions(Set<UiSessionId> sessionIds, TypedTick typedTick) {
		if (sessionIds == null || sessionIds.isEmpty()) {
			return;
		}
		for (UiSessionId sessionId : sessionIds) {
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("sessionId", sessionId.value());
			payload.put("tickQuote", typedTick != null ? typedTick.tickQuote() : null);
			payload.put("indexQuote", typedTick != null ? typedTick.indexQuote() : null);
			broadcastToSession("tick", sessionId, payload);
		}
	}

	public void broadcastHealth(Object health) {
		broadcastAll("health", health);
	}

	public void broadcastErrorToSession(UiSessionId sessionId, String message) {
		broadcastToSession("error", sessionId, Map.of("message", message));
	}

	public void broadcastErrorAll(String message) {
		broadcastAll("error", Map.of("message", message));
	}

	public void broadcastCandleAppendedToSession(UiSessionId sessionId, Object payload) {
		broadcastToSession("candle_appended", sessionId, payload);
	}

	public void broadcastCandleLiveUpsertToSession(UiSessionId sessionId, Object payload) {
		broadcastToSession("candle_live_upsert", sessionId, payload);
	}

	public void broadcastBootstrapEventToSessions(String type, Set<UiSessionId> sessionIds, BootstrapLifecycleEvent payload) {
		if (sessionIds == null || sessionIds.isEmpty()) {
			return;
		}
		for (UiSessionId sessionId : sessionIds) {
			BootstrapLifecycleEvent event = new BootstrapLifecycleEvent(
					sessionId.value(),
					payload.seriesKey(),
					payload.status(),
					payload.cutoffAt(),
					payload.seededBars(),
					payload.replayedTicks(),
					payload.droppedTicks(),
					payload.error(),
					payload.snapshotCursor(),
					payload.asOf());
			broadcastToSession(type, sessionId, event);
		}
	}

	public void broadcastScriptDeltaToSession(UiSessionId sessionId, ScriptDeltaWsEvent payload) {
		broadcastToSession("script_registry_delta", sessionId, payload);
	}

	public void broadcastIntentToSession(UiSessionId sessionId, ScriptDeltaWsEvent payload) {
		broadcastToSession("intent_emitted", sessionId, payload);
	}

	public void broadcastScriptAttachCatchupToSession(UiSessionId sessionId, ScriptAttachCatchupWsEvent payload) {
		broadcastToSession("script_attach_catchup_completed", sessionId, payload);
	}

	public void broadcastScriptSnapshotReadyToSession(UiSessionId sessionId, ScriptSnapshotReadyWsEvent payload) {
		broadcastToSession("script_snapshot_ready", sessionId, payload);
	}

	private void broadcastAll(String type, Object data) {
		try {
			String json = objectMapper.writeValueAsString(Map.of(
				"type", type,
				"ts", System.currentTimeMillis(),
				"data", data
			));
			handler.broadcastToAllBound(json);
		} catch (Exception ex) {
			log.warn("ws_ui_serialize_failed type={} reason='{}'", type, ex.getMessage());
		}
	}

	private void broadcastToSession(String type, UiSessionId sessionId, Object data) {
		try {
			String json = objectMapper.writeValueAsString(Map.of(
					"type", type,
					"ts", System.currentTimeMillis(),
					"data", data
			));
			handler.broadcastToUiSession(sessionId, json);
		} catch (Exception ex) {
			log.warn("ws_ui_serialize_failed type={} reason='{}'", type, ex.getMessage());
		}
	}

}
