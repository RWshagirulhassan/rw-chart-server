package com.readywealth.trading.trade_engine.marketdata.application;

import com.readywealth.trading.trade_engine.marketdata.domain.InstrumentStateView;
import com.readywealth.trading.trade_engine.marketdata.domain.TickMode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SubscriptionRegistry {
	private final ConcurrentHashMap<Long, InstrumentState> instruments = new ConcurrentHashMap<>();

	public MutationOutcome subscribeBatch(String subscriberId, List<Long> tokens, TickMode requestedMode) {
		List<TokenDecision> decisions = new ArrayList<>();
		Set<Long> toSubscribe = new LinkedHashSet<>();
		Map<TickMode, List<Long>> modeUpdates = new LinkedHashMap<>();

		for (Long token : dedupe(tokens)) {
			final DecisionHolder holder = new DecisionHolder();
			instruments.compute(token, (key, existing) -> {
				InstrumentState state = existing == null ? new InstrumentState(token, Instant.now(), requestedMode) : existing;
				int previousCount = state.refCount.get();
				TickMode previousMode = state.mode;
				TickMode previousSubscriberMode = state.subscriberModes.put(subscriberId, requestedMode);
				state.subscriberIds.add(subscriberId);
				if (previousSubscriberMode == null) {
					state.refCount.incrementAndGet();
				}
				state.mode = resolveEffectiveMode(state.subscriberModes.values(), requestedMode);

				boolean needSubscribe = previousCount == 0 && state.refCount.get() == 1;
				boolean modeChanged = previousMode != state.mode;
				boolean modeApply = needSubscribe || modeChanged;
				if (needSubscribe) {
					toSubscribe.add(token);
				}
				if (modeApply) {
					modeUpdates.computeIfAbsent(state.mode, ignored -> new ArrayList<>()).add(token);
				}

				String status;
				if (previousSubscriberMode == null) {
					status = "SUBSCRIBED";
				} else if (previousSubscriberMode == requestedMode) {
					status = "NOOP_ALREADY_SUBSCRIBED";
				} else {
					status = "UPDATED_MODE";
				}

				holder.decision = new TokenDecision(token, status, needSubscribe, false, modeApply, state.view());
				return state;
			});
			decisions.add(holder.decision);
		}

		return new MutationOutcome(decisions, new ArrayList<>(toSubscribe), List.of(), modeUpdates);
	}

	public MutationOutcome unsubscribeBatch(String subscriberId, List<Long> tokens, TickMode fallbackMode) {
		List<TokenDecision> decisions = new ArrayList<>();
		Set<Long> toUnsubscribe = new LinkedHashSet<>();
		Map<TickMode, List<Long>> modeUpdates = new LinkedHashMap<>();

		for (Long token : dedupe(tokens)) {
			final DecisionHolder holder = new DecisionHolder();
			instruments.compute(token, (key, existing) -> {
				if (existing == null) {
					holder.decision = new TokenDecision(token, "NOOP_NOT_FOUND", false, false, false, null);
					return null;
				}

				int previousCount = existing.refCount.get();
				TickMode previousMode = existing.mode;
				TickMode removed = existing.subscriberModes.remove(subscriberId);
				existing.subscriberIds.remove(subscriberId);

				if (removed == null) {
					holder.decision = new TokenDecision(token, "NOOP_NOT_SUBSCRIBED", false, false, false, existing.view());
					return existing;
				}

				int newCount = existing.refCount.updateAndGet(value -> Math.max(0, value - 1));
				if (newCount == 0) {
					toUnsubscribe.add(token);
					holder.decision = new TokenDecision(token, "UNSUBSCRIBED", false, true, false, null);
					return null;
				}

				existing.mode = resolveEffectiveMode(existing.subscriberModes.values(), fallbackMode);
				boolean modeChanged = previousMode != existing.mode;
				if (modeChanged) {
					modeUpdates.computeIfAbsent(existing.mode, ignored -> new ArrayList<>()).add(token);
				}
				holder.decision = new TokenDecision(token, "UNSUBSCRIBED", false, false, modeChanged, existing.view());
				return existing;
			});
			decisions.add(holder.decision);
		}

		return new MutationOutcome(decisions, List.of(), new ArrayList<>(toUnsubscribe), modeUpdates);
	}

	public List<InstrumentStateView> snapshot() {
		return instruments.values().stream().map(InstrumentState::view).sorted((a, b) -> Long.compare(a.instrumentToken(), b.instrumentToken())).toList();
	}

	public Optional<InstrumentStateView> get(long token) {
		InstrumentState state = instruments.get(token);
		return state == null ? Optional.empty() : Optional.of(state.view());
	}

	public int activeInstruments() {
		return instruments.size();
	}

	public int totalSubscribers() {
		return instruments.values().stream().mapToInt(value -> value.refCount.get()).sum();
	}

	private List<Long> dedupe(List<Long> tokens) {
		if (tokens == null || tokens.isEmpty()) {
			return List.of();
		}
		Set<Long> set = new LinkedHashSet<>(tokens);
		return new ArrayList<>(set);
	}

	private TickMode resolveEffectiveMode(Iterable<TickMode> modes, TickMode defaultMode) {
		TickMode result = defaultMode;
		for (TickMode mode : modes) {
			result = TickMode.max(result, mode);
		}
		return result;
	}

	public record TokenDecision(
		long instrumentToken,
		String status,
		boolean upstreamSubscribed,
		boolean upstreamUnsubscribed,
		boolean modeUpdated,
		InstrumentStateView state
	) {
	}

	public record MutationOutcome(
		List<TokenDecision> decisions,
		List<Long> tokensToSubscribe,
		List<Long> tokensToUnsubscribe,
		Map<TickMode, List<Long>> modeUpdates
	) {
		public boolean hasUpstreamWork() {
			return !tokensToSubscribe.isEmpty() || !tokensToUnsubscribe.isEmpty() || !modeUpdates.isEmpty();
		}
	}

	private static final class DecisionHolder {
		private TokenDecision decision;
	}

	private static final class InstrumentState {
		private final long instrumentToken;
		private final AtomicInteger refCount = new AtomicInteger(0);
		private final Map<String, TickMode> subscriberModes = new ConcurrentHashMap<>();
		private final Set<String> subscriberIds = ConcurrentHashMap.newKeySet();
		private final Instant subscribedAt;
		private volatile TickMode mode;

		private InstrumentState(long instrumentToken, Instant subscribedAt, TickMode mode) {
			this.instrumentToken = instrumentToken;
			this.subscribedAt = subscribedAt;
			this.mode = mode;
		}

		private InstrumentStateView view() {
			return new InstrumentStateView(instrumentToken, refCount.get(), Collections.unmodifiableSet(new LinkedHashSet<>(subscriberIds)), mode, subscribedAt);
		}
	}
}
