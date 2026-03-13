package com.readywealth.trading.trade_engine.auth.infrastructure.redis;

import com.readywealth.trading.trade_engine.auth.domain.TokenRecord;

import java.util.Optional;

public interface TokenStore {
	void save(TokenRecord tokenRecord);

	Optional<TokenRecord> get();

	void clear();

	void expire();
}
