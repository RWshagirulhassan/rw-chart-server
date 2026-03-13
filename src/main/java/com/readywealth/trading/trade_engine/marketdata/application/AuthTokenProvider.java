package com.readywealth.trading.trade_engine.marketdata.application;

import com.readywealth.trading.trade_engine.auth.domain.TokenRecord;
import com.readywealth.trading.trade_engine.auth.infrastructure.redis.TokenStore;
import com.readywealth.trading.trade_engine.marketdata.infrastructure.config.PublisherProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
public class AuthTokenProvider {
	private final TokenStore tokenStore;
	private final PublisherProperties publisherProperties;

	private CachedToken cachedToken;
	private Instant lastTokenFetchAt;
	private String lastError;
	private boolean lastTokenPresent;
	private boolean lastTokenExpired;
	private long nextRetryAtEpochMs;
	private long currentBackoffMs;

	public AuthTokenProvider(TokenStore tokenStore, PublisherProperties publisherProperties) {
		this.tokenStore = tokenStore;
		this.publisherProperties = publisherProperties;
		this.currentBackoffMs = Math.max(250L, publisherProperties.getRetryInitialDelayMs());
	}

	public synchronized Optional<String> getValidAccessToken() {
		long now = System.currentTimeMillis();
		if (cachedToken != null && cachedToken.isValid(now, publisherProperties.getTokenCacheSeconds())) {
			return Optional.of(cachedToken.accessToken());
		}
		boolean inBackoffWindow = now < nextRetryAtEpochMs;

		lastTokenFetchAt = Instant.now();
		Optional<TokenRecord> tokenRecord = tokenStore.get();
		if (tokenRecord.isEmpty()) {
			lastTokenPresent = false;
			lastTokenExpired = true;
			if (!inBackoffWindow) {
				registerFailure("token missing in store", now);
			}
			return Optional.empty();
		}

		TokenRecord token = tokenRecord.get();
		lastTokenPresent = !token.accessToken().isBlank();
		lastTokenExpired = token.expired() || (token.expiresAt() != null && token.expiresAt().isBefore(Instant.now()));
		if (!lastTokenPresent || lastTokenExpired) {
			if (!inBackoffWindow) {
				registerFailure("token expired or empty in store", now);
			}
			return Optional.empty();
		}

		cachedToken = new CachedToken(token.accessToken(), Instant.now());
		lastError = null;
		nextRetryAtEpochMs = 0;
		currentBackoffMs = Math.max(250L, publisherProperties.getRetryInitialDelayMs());
		return Optional.of(token.accessToken());
	}

	public synchronized TokenHealth tokenHealth() {
		return new TokenHealth(lastTokenPresent, lastTokenExpired, lastTokenFetchAt, lastError);
	}

	private void registerFailure(String message, long now) {
		lastError = message;
		nextRetryAtEpochMs = now + currentBackoffMs;
		currentBackoffMs = Math.min(
			Math.max(publisherProperties.getRetryInitialDelayMs(), currentBackoffMs * 2),
			Math.max(publisherProperties.getRetryInitialDelayMs(), publisherProperties.getRetryMaxDelayMs())
		);
	}

	private record CachedToken(String accessToken, Instant fetchedAt) {
		private boolean isValid(long nowEpochMs, long cacheSeconds) {
			long maxAgeMs = Math.max(1000L, cacheSeconds * 1000L);
			return (nowEpochMs - fetchedAt.toEpochMilli()) <= maxAgeMs && accessToken != null && !accessToken.isBlank();
		}
	}

	public record TokenHealth(boolean tokenPresent, boolean tokenExpired, Instant lastTokenFetchAt, String lastError) {
	}
}
