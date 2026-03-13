package com.readywealth.trading.trade_engine.marketdata.application;

import com.readywealth.trading.trade_engine.marketdata.infrastructure.config.PublisherProperties;
import com.readywealth.trading.trade_engine.marketdata.domain.BatchSubscriptionResponse;
import com.readywealth.trading.trade_engine.marketdata.domain.SubscribeRequest;
import com.readywealth.trading.trade_engine.marketdata.domain.TickMode;
import com.readywealth.trading.trade_engine.marketdata.domain.UnsubscribeRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubscriptionServiceTest {
	private SubscriptionService service;
	private StubUpstreamSession upstreamSession;

	@BeforeEach
	void setUp() {
		SubscriptionRegistry registry = new SubscriptionRegistry();
		upstreamSession = new StubUpstreamSession();
		PublisherProperties properties = new PublisherProperties();
		properties.setDefaultMode("quote");
		service = new SubscriptionService(registry, upstreamSession, properties, new SimpleMeterRegistry());
	}

	@Test
	void shouldHandleBatchSubscribeAndUnsubscribe() {
		SubscribeRequest subscribeRequest = new SubscribeRequest();
		subscribeRequest.setSubscriberId("svc-1");
		subscribeRequest.setInstrumentTokens(List.of(11L, 12L, 11L));
		subscribeRequest.setMode(TickMode.FULL);

		BatchSubscriptionResponse subscribed = service.subscribe(subscribeRequest);
		assertEquals(2, subscribed.results().size());
		assertEquals("SUBSCRIBE", subscribed.action());

		UnsubscribeRequest unsubscribeRequest = new UnsubscribeRequest();
		unsubscribeRequest.setSubscriberId("svc-1");
		unsubscribeRequest.setInstrumentTokens(List.of(11L, 12L));
		BatchSubscriptionResponse unsubscribed = service.unsubscribe(unsubscribeRequest);
		assertEquals("UNSUBSCRIBE", unsubscribed.action());

		assertEquals(2, upstreamSession.applyCalls.get());
	}

	@Test
	void shouldRejectMissingSubscriber() {
		SubscribeRequest request = new SubscribeRequest();
		request.setInstrumentTokens(List.of(1L));
		assertThrows(IllegalArgumentException.class, () -> service.subscribe(request));
	}

	private static class StubUpstreamSession implements UpstreamSession {
		private final AtomicInteger applyCalls = new AtomicInteger();

		@Override
		public void applyMutation(SubscriptionRegistry.MutationOutcome outcome) {
			applyCalls.incrementAndGet();
		}
	}
}
