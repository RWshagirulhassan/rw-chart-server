package com.readywealth.trading.trade_engine.auth.application;

import com.readywealth.trading.trade_engine.auth.domain.SessionStatusResponse;
import com.readywealth.trading.trade_engine.auth.domain.TokenRecord;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class AuthService {
	private final KiteService kiteService;

	public AuthService(KiteService kiteService) {
		this.kiteService = kiteService;
	}

	public String loginUrl() {
		return kiteService.getLoginUrl();
	}

	public TokenRecord onCallback(String requestToken) {
		return kiteService.generateAndStoreSession(requestToken);
	}

	public void logout() {
		kiteService.clearSession();
	}

	public SessionStatusResponse sessionStatus() {
		Optional<TokenRecord> token = kiteService.getSession();
		boolean linked = token.isPresent() && !token.get().accessToken().isBlank();
		boolean expired = token.map(TokenRecord::expired).orElse(true);
		String userId = token.map(TokenRecord::userId).orElse("");
		return new SessionStatusResponse(linked, expired, userId, token.map(TokenRecord::issuedAt).orElse(null),
				loginUrl());
	}

	public Map<String, Object> internalTokenState() {
		Optional<TokenRecord> token = kiteService.getSession();
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("present", token.isPresent());
		response.put("expired", token.map(TokenRecord::expired).orElse(true));
		response.put("userId", token.map(TokenRecord::userId).orElse(""));
		response.put("accessToken", token.map(TokenRecord::accessToken).orElse(""));
		response.put("publicToken", token.map(TokenRecord::publicToken).orElse(""));
		response.put("issuedAt", token.map(TokenRecord::issuedAt).orElse(null));
		response.put("expiresAt", token.map(TokenRecord::expiresAt).orElse(null));
		return response;
	}

	public void expireInternal() {
		kiteService.expireSession();
	}
}
