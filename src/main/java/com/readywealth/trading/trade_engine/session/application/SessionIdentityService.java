package com.readywealth.trading.trade_engine.session.application;

import com.readywealth.trading.trade_engine.auth.domain.TokenRecord;
import com.readywealth.trading.trade_engine.auth.infrastructure.redis.TokenStore;
import com.readywealth.trading.trade_engine.session.application.port.CurrentUserPort;
import com.readywealth.trading.trade_engine.session.domain.SessionUnauthorizedException;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class SessionIdentityService implements CurrentUserPort {
    private final TokenStore tokenStore;

    public SessionIdentityService(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @Override
    public String requireCurrentUserId() {
        TokenRecord token = tokenStore.get()
                .orElseThrow(() -> new SessionUnauthorizedException("No auth token in Redis. Login first."));
        if (token.expired() || token.expiresAt() == null || token.expiresAt().isBefore(Instant.now())) {
            throw new SessionUnauthorizedException("Auth token is expired. Login again.");
        }
        if (token.userId() == null || token.userId().isBlank()) {
            throw new SessionUnauthorizedException("Auth token has no userId.");
        }
        return token.userId();
    }
}
