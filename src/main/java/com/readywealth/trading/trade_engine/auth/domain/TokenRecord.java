package com.readywealth.trading.trade_engine.auth.domain;

import java.time.Instant;

public record TokenRecord(
	String userId,
	String accessToken,
	String publicToken,
	Instant issuedAt,
	Instant expiresAt,
	boolean expired
) {
}
