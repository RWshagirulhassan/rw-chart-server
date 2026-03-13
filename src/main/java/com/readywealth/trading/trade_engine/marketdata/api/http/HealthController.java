package com.readywealth.trading.trade_engine.marketdata.api.http;

import com.readywealth.trading.trade_engine.marketdata.domain.HealthzResponse;
import com.readywealth.trading.trade_engine.marketdata.application.TickHealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
	private final TickHealthService tickHealthService;

	public HealthController(TickHealthService tickHealthService) {
		this.tickHealthService = tickHealthService;
	}

	@GetMapping("/ticks/healthz")
	public ResponseEntity<HealthzResponse> healthz() {
		return ResponseEntity.ok(tickHealthService.snapshot());
	}
}
