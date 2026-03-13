package com.readywealth.trading.trade_engine.marketdata.application;

import com.readywealth.trading.trade_engine.session.application.port.UpstreamSubscriptionPort;
import com.readywealth.trading.trade_engine.marketdata.infrastructure.config.PublisherProperties;
import com.readywealth.trading.trade_engine.marketdata.domain.BatchSubscriptionResponse;
import com.readywealth.trading.trade_engine.marketdata.domain.InstrumentStateView;
import com.readywealth.trading.trade_engine.marketdata.domain.SubscribeRequest;
import com.readywealth.trading.trade_engine.marketdata.domain.TickMode;
import com.readywealth.trading.trade_engine.marketdata.domain.TokenOperationResult;
import com.readywealth.trading.trade_engine.marketdata.domain.UnsubscribeRequest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class SubscriptionService implements UpstreamSubscriptionPort {
	private final SubscriptionRegistry subscriptionRegistry;
	private final UpstreamSession upstreamSession;
	private final PublisherProperties publisherProperties;
	private final Counter subscribeCalls;
	private final Counter unsubscribeCalls;

	public SubscriptionService(
		SubscriptionRegistry subscriptionRegistry,
		UpstreamSession upstreamSession,
		PublisherProperties publisherProperties,
		MeterRegistry meterRegistry
	) {
		this.subscriptionRegistry = subscriptionRegistry;
		this.upstreamSession = upstreamSession;
		this.publisherProperties = publisherProperties;
		this.subscribeCalls = meterRegistry.counter("subscribe_calls");
		this.unsubscribeCalls = meterRegistry.counter("unsubscribe_calls");
		meterRegistry.gauge("active_instruments", subscriptionRegistry, SubscriptionRegistry::activeInstruments);
		meterRegistry.gauge("total_subscribers", subscriptionRegistry, SubscriptionRegistry::totalSubscribers);
	}

	public BatchSubscriptionResponse subscribe(SubscribeRequest request) {
		String subscriberId = validateSubscriberId(request.getSubscriberId());
		List<Long> tokens = validateTokens(request.getInstrumentTokens());
		TickMode requestedMode = request.getMode() == null ? defaultMode() : request.getMode();

		subscribeCalls.increment(tokens.size());
		SubscriptionRegistry.MutationOutcome outcome = subscriptionRegistry.subscribeBatch(subscriberId, tokens, requestedMode);
		upstreamSession.applyMutation(outcome);
		return new BatchSubscriptionResponse(subscriberId, "SUBSCRIBE", toResults(outcome.decisions()));
	}

	public BatchSubscriptionResponse unsubscribe(UnsubscribeRequest request) {
		String subscriberId = validateSubscriberId(request.getSubscriberId());
		List<Long> tokens = validateTokens(request.getInstrumentTokens());

		unsubscribeCalls.increment(tokens.size());
		SubscriptionRegistry.MutationOutcome outcome = subscriptionRegistry.unsubscribeBatch(subscriberId, tokens, defaultMode());
		upstreamSession.applyMutation(outcome);
		return new BatchSubscriptionResponse(subscriberId, "UNSUBSCRIBE", toResults(outcome.decisions()));
	}

	public List<InstrumentStateView> snapshot() {
		return subscriptionRegistry.snapshot();
	}

	@Override
	public void subscribeInternal(String subscriberId, List<Long> instrumentTokens) {
		String id = validateSubscriberId(subscriberId);
		List<Long> tokens = validateTokens(instrumentTokens);
		SubscriptionRegistry.MutationOutcome outcome = subscriptionRegistry.subscribeBatch(id, tokens, defaultMode());
		upstreamSession.applyMutation(outcome);
	}

	@Override
	public void unsubscribeInternal(String subscriberId, List<Long> instrumentTokens) {
		String id = validateSubscriberId(subscriberId);
		List<Long> tokens = validateTokens(instrumentTokens);
		SubscriptionRegistry.MutationOutcome outcome = subscriptionRegistry.unsubscribeBatch(id, tokens, defaultMode());
		upstreamSession.applyMutation(outcome);
	}

	public Optional<InstrumentStateView> get(long instrumentToken) {
		return subscriptionRegistry.get(instrumentToken);
	}

	public int activeInstruments() {
		return subscriptionRegistry.activeInstruments();
	}

	public int totalSubscribers() {
		return subscriptionRegistry.totalSubscribers();
	}

	private TickMode defaultMode() {
		String mode = publisherProperties.getDefaultMode();
		if (mode == null) {
			return TickMode.QUOTE;
		}
		return switch (mode.toLowerCase(Locale.ROOT)) {
			case "ltp" -> TickMode.LTP;
			case "full" -> TickMode.FULL;
			default -> TickMode.QUOTE;
		};
	}

	private List<TokenOperationResult> toResults(List<SubscriptionRegistry.TokenDecision> decisions) {
		List<TokenOperationResult> results = new ArrayList<>(decisions.size());
		for (SubscriptionRegistry.TokenDecision decision : decisions) {
			results.add(new TokenOperationResult(
				decision.instrumentToken(),
				decision.status(),
				decision.upstreamSubscribed(),
				decision.upstreamUnsubscribed(),
				decision.modeUpdated(),
				decision.state()
			));
		}
		return results;
	}

	private String validateSubscriberId(String subscriberId) {
		if (subscriberId == null || subscriberId.isBlank()) {
			throw new IllegalArgumentException("subscriberId is required");
		}
		return subscriberId.trim();
	}

	private List<Long> validateTokens(List<Long> tokens) {
		if (tokens == null || tokens.isEmpty()) {
			throw new IllegalArgumentException("instrumentTokens is required");
		}
		LinkedHashSet<Long> unique = new LinkedHashSet<>();
		for (Long token : tokens) {
			if (token == null || token <= 0) {
				throw new IllegalArgumentException("instrumentTokens must contain positive values");
			}
			unique.add(token);
		}
		return new ArrayList<>(unique);
	}
}
