package com.readywealth.trading.trade_engine.auth.domain;

import java.time.Instant;

public record SessionStatusResponse(
	boolean linked,
	boolean expired,
	String userId,
	Instant issuedAt,
	String loginUrl
) {
}
