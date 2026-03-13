package com.readywealth.trading.trade_engine.marketdata.application;

import com.readywealth.trading.trade_engine.marketdata.infrastructure.config.KiteSocketProperties;
import com.readywealth.trading.trade_engine.marketdata.domain.InstrumentStateView;
import com.readywealth.trading.trade_engine.marketdata.domain.TickMode;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.ticker.KiteTicker;
import com.zerodhatech.ticker.OnConnect;
import com.zerodhatech.ticker.OnDisconnect;
import com.zerodhatech.ticker.OnError;
import com.zerodhatech.ticker.OnTicks;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class KiteSessionManager implements UpstreamSession {
	private static final Logger log = LoggerFactory.getLogger(KiteSessionManager.class);

	private final AuthTokenProvider authTokenProvider;
	private final KiteSocketProperties kiteSocketProperties;
	private final SubscriptionRegistry subscriptionRegistry;
	private final TickDispatcher tickDispatcher;
	private final Counter reconnectCounter;

	private volatile KiteTicker ticker;
	private volatile boolean connected;
	private volatile String activeAccessToken;
	private volatile Instant lastWsConnectAt;
	private volatile String lastError;
	private volatile boolean hasConnectedBefore;

	public KiteSessionManager(
		AuthTokenProvider authTokenProvider,
		KiteSocketProperties kiteSocketProperties,
		SubscriptionRegistry subscriptionRegistry,
		TickDispatcher tickDispatcher,
		MeterRegistry meterRegistry
	) {
		this.authTokenProvider = authTokenProvider;
		this.kiteSocketProperties = kiteSocketProperties;
		this.subscriptionRegistry = subscriptionRegistry;
		this.tickDispatcher = tickDispatcher;
		this.reconnectCounter = meterRegistry.counter("ws_reconnects");
	}

	@Scheduled(fixedDelay = 2000)
	public void ensureConnectedScheduled() {
		ensureConnected();
	}

	public synchronized void ensureConnected() {
		if (connected) {
			return;
		}
		if (kiteSocketProperties.getApiKey() == null || kiteSocketProperties.getApiKey().isBlank()) {
			lastError = "kite.api-key is missing";
			return;
		}
		Optional<String> accessToken = authTokenProvider.getValidAccessToken();
		if (accessToken.isEmpty()) {
			connected = false;
			lastError = "access token unavailable";
			return;
		}

		if (ticker == null || !accessToken.get().equals(activeAccessToken)) {
			buildTicker(accessToken.get());
		}

		if (ticker != null && !ticker.isConnectionOpen()) {
			log.info("ws_connect_attempt");
			ticker.connect();
		}
	}

	@Override
	public synchronized void applyMutation(SubscriptionRegistry.MutationOutcome outcome) {
		if (!outcome.hasUpstreamWork()) {
			return;
		}
		ensureConnected();
		if (!connected || ticker == null || !ticker.isConnectionOpen()) {
			return;
		}

		if (!outcome.tokensToUnsubscribe().isEmpty()) {
			ArrayList<Long> tokens = new ArrayList<>(outcome.tokensToUnsubscribe());
			ticker.unsubscribe(tokens);
			log.info("upstream_unsubscribe tokens={}", tokens.size());
		}

		if (!outcome.tokensToSubscribe().isEmpty()) {
			ArrayList<Long> tokens = new ArrayList<>(outcome.tokensToSubscribe());
			ticker.subscribe(tokens);
			log.info("upstream_subscribe tokens={}", tokens.size());
		}

		for (Map.Entry<TickMode, List<Long>> entry : outcome.modeUpdates().entrySet()) {
			if (entry.getValue().isEmpty()) {
				continue;
			}
			ArrayList<Long> tokens = new ArrayList<>(entry.getValue());
			ticker.setMode(tokens, entry.getKey().getKiteMode());
			log.info("upstream_mode_update mode={} tokens={}", entry.getKey(), tokens.size());
		}
	}

	public synchronized SessionHealth health() {
		return new SessionHealth(connected, lastWsConnectAt, lastError);
	}

	private void buildTicker(String accessToken) {
		activeAccessToken = accessToken;
		KiteTicker ws = new KiteTicker(accessToken, kiteSocketProperties.getApiKey());
		ws.setTryReconnection(true);
		try {
			ws.setMaximumRetries(kiteSocketProperties.getWsMaxRetries());
			ws.setMaximumRetryInterval(kiteSocketProperties.getWsMaxRetryIntervalSeconds());
		} catch (KiteException ex) {
			log.warn("ws_retry_config_failed reason='{}'", ex.message);
		}

		ws.setOnConnectedListener(onConnected());
		ws.setOnDisconnectedListener(onDisconnected());
		ws.setOnErrorListener(onError());
		ws.setOnTickerArrivalListener(onTicks());
		ticker = ws;
	}

	private OnConnect onConnected() {
		return () -> {
			synchronized (this) {
				if (hasConnectedBefore) {
					reconnectCounter.increment();
				}
				hasConnectedBefore = true;
				connected = true;
				lastError = null;
				lastWsConnectAt = Instant.now();
				log.info("ws_connected");
				replayActiveSubscriptions();
			}
		};
	}

	private OnDisconnect onDisconnected() {
		return () -> {
			synchronized (this) {
				connected = false;
				lastError = "ws_disconnected";
				log.warn("ws_disconnected");
			}
		};
	}

	private OnError onError() {
		return new OnError() {
			@Override
			public void onError(Exception exception) {
				recordError(exception.getMessage());
			}

			@Override
			public void onError(KiteException kiteException) {
				recordError(kiteException.message);
			}

			@Override
			public void onError(String error) {
				recordError(error);
			}

			private void recordError(String error) {
				synchronized (KiteSessionManager.this) {
					connected = false;
					lastError = error;
					log.warn("ws_error reason='{}'", error);
				}
			}
		};
	}

	private OnTicks onTicks() {
		return tickDispatcher::enqueue;
	}

	private void replayActiveSubscriptions() {
		if (!connected || ticker == null || !ticker.isConnectionOpen()) {
			return;
		}
		List<InstrumentStateView> states = subscriptionRegistry.snapshot();
		if (states.isEmpty()) {
			return;
		}

		ArrayList<Long> tokens = new ArrayList<>(states.stream().map(InstrumentStateView::instrumentToken).toList());
		ticker.subscribe(tokens);

		Map<TickMode, List<Long>> groupedModes = new LinkedHashMap<>();
		for (InstrumentStateView state : states) {
			groupedModes.computeIfAbsent(state.mode(), ignored -> new ArrayList<>()).add(state.instrumentToken());
		}
		for (Map.Entry<TickMode, List<Long>> entry : groupedModes.entrySet()) {
			ticker.setMode(new ArrayList<>(entry.getValue()), entry.getKey().getKiteMode());
		}
		log.info("ws_replay_subscriptions tokens={}", tokens.size());
	}

	public record SessionHealth(boolean connected, Instant lastWsConnectAt, String lastError) {
	}
}
