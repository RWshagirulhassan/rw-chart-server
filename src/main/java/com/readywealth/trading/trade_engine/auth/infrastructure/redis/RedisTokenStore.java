package com.readywealth.trading.trade_engine.auth.infrastructure.redis;

import com.readywealth.trading.trade_engine.auth.infrastructure.config.TokenExpiryProperties;
import com.readywealth.trading.trade_engine.auth.domain.TokenRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

@Component
public class RedisTokenStore implements TokenStore {
	private static final String TOKEN_KEY = "auth:token";

	private final StringRedisTemplate redis;
	private final TokenExpiryProperties tokenExpiryProperties;

	public RedisTokenStore(StringRedisTemplate redis, TokenExpiryProperties tokenExpiryProperties) {
		this.redis = redis;
		this.tokenExpiryProperties = tokenExpiryProperties;
	}

	@Override
	public void save(TokenRecord tokenRecord) {
		Instant expiryInstant = nextExpiryInstant();
		Map<String, String> values = Map.of(
			"userId", nullSafe(tokenRecord.userId()),
			"accessToken", nullSafe(tokenRecord.accessToken()),
			"publicToken", nullSafe(tokenRecord.publicToken()),
			"issuedAt", String.valueOf(tokenRecord.issuedAt().getEpochSecond()),
			"expired", String.valueOf(tokenRecord.expired()),
			"expiresAt", String.valueOf(expiryInstant.getEpochSecond())
		);
		redis.opsForHash().putAll(TOKEN_KEY, values);
		redis.expireAt(TOKEN_KEY, expiryInstant);
	}

	@Override
	public Optional<TokenRecord> get() {
		Map<Object, Object> hash = redis.opsForHash().entries(TOKEN_KEY);
		if (hash == null || hash.isEmpty()) {
			return Optional.empty();
		}

		String issuedAtRaw = (String) hash.get("issuedAt");
		String expiresAtRaw = (String) hash.get("expiresAt");
		if (issuedAtRaw == null || expiresAtRaw == null) {
			return Optional.empty();
		}

		return Optional.of(new TokenRecord(
			(String) hash.get("userId"),
			(String) hash.get("accessToken"),
			(String) hash.get("publicToken"),
			Instant.ofEpochSecond(Long.parseLong(issuedAtRaw)),
			Instant.ofEpochSecond(Long.parseLong(expiresAtRaw)),
			Boolean.parseBoolean((String) hash.getOrDefault("expired", "false"))
		));
	}

	@Override
	public void clear() {
		redis.delete(TOKEN_KEY);
	}

	@Override
	public void expire() {
		get().ifPresent(token -> save(new TokenRecord(
			token.userId(),
			token.accessToken(),
			token.publicToken(),
			token.issuedAt(),
			token.expiresAt(),
			true
		)));
	}

	private Instant nextExpiryInstant() {
		ZoneId zoneId = ZoneId.of(tokenExpiryProperties.getZone());
		LocalDateTime target = LocalDate.now(zoneId)
			.atTime(tokenExpiryProperties.getHour(), tokenExpiryProperties.getMinute(), tokenExpiryProperties.getSecond());
		if (LocalDateTime.now(zoneId).isAfter(target)) {
			target = target.plusDays(1);
		}
		return target.atZone(zoneId).toInstant();
	}

	private static String nullSafe(String value) {
		return value == null ? "" : value;
	}
}
