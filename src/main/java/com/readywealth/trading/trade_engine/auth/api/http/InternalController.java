package com.readywealth.trading.trade_engine.auth.api.http;

import com.readywealth.trading.trade_engine.auth.application.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal")
public class InternalController {
	private final AuthService authService;

	public InternalController(AuthService authService) {
		this.authService = authService;
	}

	@GetMapping("/token")
	public ResponseEntity<Map<String, Object>> token() {
		return ResponseEntity.ok(authService.internalTokenState());
	}

	@PostMapping("/expire")
	public ResponseEntity<Map<String, String>> expire() {
		authService.expireInternal();
		return ResponseEntity.ok(Map.of("message", "Token marked expired"));
	}
}
