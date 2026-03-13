package com.readywealth.trading.trade_engine.marketdata.api.http;

import com.readywealth.trading.trade_engine.marketdata.domain.BatchSubscriptionResponse;
import com.readywealth.trading.trade_engine.marketdata.domain.InstrumentStateView;
import com.readywealth.trading.trade_engine.marketdata.domain.SubscribeRequest;
import com.readywealth.trading.trade_engine.marketdata.domain.UnsubscribeRequest;
import com.readywealth.trading.trade_engine.marketdata.application.SubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SubscriptionController {
	private final SubscriptionService subscriptionService;

	public SubscriptionController(SubscriptionService subscriptionService) {
		this.subscriptionService = subscriptionService;
	}

	@PostMapping("/ticks/subscriptions")
	public ResponseEntity<BatchSubscriptionResponse> subscribe(@RequestBody SubscribeRequest request) {
		return ResponseEntity.ok(subscriptionService.subscribe(request));
	}

	@DeleteMapping("/ticks/subscriptions")
	public ResponseEntity<BatchSubscriptionResponse> unsubscribe(@RequestBody UnsubscribeRequest request) {
		return ResponseEntity.ok(subscriptionService.unsubscribe(request));
	}

	@GetMapping("/ticks/subscriptions")
	public ResponseEntity<List<InstrumentStateView>> snapshot() {
		return ResponseEntity.ok(subscriptionService.snapshot());
	}

	@GetMapping("/ticks/subscriptions/{instrumentToken}")
	public ResponseEntity<InstrumentStateView> get(@PathVariable long instrumentToken) {
		return subscriptionService.get(instrumentToken)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.notFound().build());
	}
}
