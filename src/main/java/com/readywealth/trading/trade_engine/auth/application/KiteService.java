package com.readywealth.trading.trade_engine.auth.application;

import com.readywealth.trading.trade_engine.auth.infrastructure.config.KiteProperties;
import com.readywealth.trading.trade_engine.auth.domain.TokenRecord;
import com.readywealth.trading.trade_engine.auth.infrastructure.redis.TokenStore;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.User;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

@Service
public class KiteService {
	private final KiteProperties kiteProperties;
	private final TokenStore tokenStore;

	public KiteService(KiteProperties kiteProperties, TokenStore tokenStore) {
		this.kiteProperties = kiteProperties;
		this.tokenStore = tokenStore;
	}

	public String getLoginUrl() {
		requireApiKey();
		KiteConnect kite = new KiteConnect(kiteProperties.getApiKey());
		return kite.getLoginURL();
	}

	public TokenRecord generateAndStoreSession(String requestToken) {
		try {
			requireApiKey();
			requireApiSecret();
			KiteConnect kite = new KiteConnect(kiteProperties.getApiKey());
			User user = kite.generateSession(requestToken, kiteProperties.getApiSecret());

			String userId = firstNonBlank(
					readString(user, "getUserId", "userId"),
					safeRead(kite::getUserId));
			String accessToken = firstNonBlank(
					readString(user, "getAccessToken", "accessToken"),
					safeRead(kite::getAccessToken));
			String publicToken = firstNonBlank(
					readString(user, "getPublicToken", "publicToken"),
					safeRead(kite::getPublicToken));
			if (userId.isBlank()) {
				throw new IllegalStateException("Kite session generated without userId");
			}
			if (accessToken.isBlank()) {
				throw new IllegalStateException("Kite session generated without access token");
			}
			Instant now = Instant.now();
			TokenRecord record = new TokenRecord(userId, accessToken, publicToken, now, now, false);
			tokenStore.save(record);
			return record;
		} catch (IOException | KiteException e) {
			throw new IllegalStateException("Failed to generate Kite session", e);
		}
	}

	public Optional<TokenRecord> getSession() {
		return tokenStore.get();
	}

	public void clearSession() {
		tokenStore.clear();
	}

	public void expireSession() {
		tokenStore.expire();
	}

	public Optional<KiteConnect> buildAuthenticatedClient() {
		return tokenStore.get()
				.filter(t -> !t.expired() && !t.accessToken().isBlank())
				.map(this::buildClient);
	}

	private KiteConnect buildClient(TokenRecord tokenRecord) {
		KiteConnect kite = new KiteConnect(kiteProperties.getApiKey());
		kite.setAccessToken(tokenRecord.accessToken());
		kite.setPublicToken(tokenRecord.publicToken());
		configureSessionExpiryHook(kite);
		return kite;
	}

	private void configureSessionExpiryHook(KiteConnect kite) {
		try {
			Class<?> hookType = Class.forName("com.zerodhatech.kiteconnect.kitehttp.SessionExpiryHook");
			InvocationHandler handler = (proxy, method, args) -> {
				if ("sessionExpired".equals(method.getName())) {
					tokenStore.expire();
				}
				return null;
			};
			Object hook = Proxy.newProxyInstance(hookType.getClassLoader(), new Class<?>[] { hookType }, handler);
			Method setter = KiteConnect.class.getMethod("setSessionExpiryHook", hookType);
			setter.invoke(kite, hook);
		} catch (Exception ignored) {
			// Hook is best-effort; token persistence still works without it.
		}
	}

	private String readString(Object source, String getterName, String fieldName) {
		try {
			Method getter = source.getClass().getMethod(getterName);
			Object value = getter.invoke(source);
			return value == null ? "" : String.valueOf(value);
		} catch (Exception ignored) {
			try {
				Field field = source.getClass().getField(fieldName);
				Object value = field.get(source);
				return value == null ? "" : String.valueOf(value);
			} catch (Exception ignoredAgain) {
				return "";
			}
		}
	}

	private static String safeRead(Supplier<String> supplier) {
		try {
			String value = supplier.get();
			return value == null ? "" : value;
		} catch (Exception ignored) {
			return "";
		}
	}

	private static String firstNonBlank(String primary, String fallback) {
		if (primary != null && !primary.isBlank()) {
			return primary;
		}
		if (fallback != null && !fallback.isBlank()) {
			return fallback;
		}
		return "";
	}

	private void requireApiKey() {
		if (kiteProperties.getApiKey() == null || kiteProperties.getApiKey().isBlank()) {
			throw new IllegalStateException("kite.api-key is missing; set KITE_API_KEY or add it to trade-engine/.env");
		}
	}

	private void requireApiSecret() {
		if (kiteProperties.getApiSecret() == null || kiteProperties.getApiSecret().isBlank()) {
			throw new IllegalStateException("kite.api-secret is missing; set KITE_API_SECRET or add it to trade-engine/.env");
		}
	}
}
