package com.readywealth.trading.trade_engine.marketdata.application;

import com.readywealth.trading.trade_engine.marketdata.domain.TickMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionRegistryTest {
	private final SubscriptionRegistry registry = new SubscriptionRegistry();

	@Test
	void shouldSubscribeOnlyOnceForDuplicateTokensAndRequests() {
		SubscriptionRegistry.MutationOutcome first = registry.subscribeBatch("sub-a", List.of(101L, 101L, 102L), TickMode.QUOTE);
		assertEquals(2, first.tokensToSubscribe().size());

		SubscriptionRegistry.MutationOutcome second = registry.subscribeBatch("sub-a", List.of(101L, 102L), TickMode.QUOTE);
		assertTrue(second.tokensToSubscribe().isEmpty());
		assertEquals(2, registry.activeInstruments());
	}

	@Test
	void shouldEscalateModeToHighestDetail() {
		registry.subscribeBatch("sub-a", List.of(200L), TickMode.LTP);
		SubscriptionRegistry.MutationOutcome upgraded = registry.subscribeBatch("sub-b", List.of(200L), TickMode.FULL);
		assertEquals(TickMode.FULL, registry.get(200L).orElseThrow().mode());
		assertEquals(1, upgraded.modeUpdates().get(TickMode.FULL).size());
	}

	@Test
	void shouldUnsubscribeUpstreamOnlyWhenRefCountReachesZero() {
		registry.subscribeBatch("sub-a", List.of(300L), TickMode.QUOTE);
		registry.subscribeBatch("sub-b", List.of(300L), TickMode.QUOTE);

		SubscriptionRegistry.MutationOutcome firstOff = registry.unsubscribeBatch("sub-a", List.of(300L), TickMode.QUOTE);
		assertTrue(firstOff.tokensToUnsubscribe().isEmpty());
		assertEquals(1, registry.get(300L).orElseThrow().refCount());

		SubscriptionRegistry.MutationOutcome secondOff = registry.unsubscribeBatch("sub-b", List.of(300L), TickMode.QUOTE);
		assertEquals(1, secondOff.tokensToUnsubscribe().size());
		assertTrue(registry.get(300L).isEmpty());
	}
}
