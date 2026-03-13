package com.readywealth.trading.trade_engine.auth.api.http;

import com.readywealth.trading.trade_engine.auth.domain.SessionStatusResponse;
import com.readywealth.trading.trade_engine.auth.domain.TokenRecord;
import com.readywealth.trading.trade_engine.auth.application.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping
public class KiteAuthController {
	private final AuthService authService;

	public KiteAuthController(AuthService authService) {
		this.authService = authService;
	}

	@GetMapping("/")
	public Map<String, String> home() {
		return Map.of("service", "auth-service", "login", "/kite/login", "session", "/api/session");
	}

	@GetMapping("/kite/login")
	public void login(HttpServletResponse response) throws IOException {
		response.sendRedirect(authService.loginUrl());
	}

	@GetMapping("/kite/callback")
	public ResponseEntity<?> callback(
		@RequestParam(name = "request_token", required = false) String requestToken,
		@RequestParam(name = "status", required = false) String status
	) {
		if (!"success".equalsIgnoreCase(status) || requestToken == null || requestToken.isBlank()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid callback parameters"));
		}
		TokenRecord token = authService.onCallback(requestToken);
		return ResponseEntity.ok(Map.of(
			"message", "Kite linked",
			"userId", token.userId(),
			"issuedAt", token.issuedAt().toString()
		));
	}

	@PostMapping("/kite/logout")
	public ResponseEntity<Void> logout() {
		authService.logout();
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/api/session")
	public ResponseEntity<SessionStatusResponse> session() {
		return ResponseEntity.ok(authService.sessionStatus());
	}
}
