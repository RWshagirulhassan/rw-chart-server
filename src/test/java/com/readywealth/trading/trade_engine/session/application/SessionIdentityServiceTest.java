package com.readywealth.trading.trade_engine.session.application;

import com.readywealth.trading.trade_engine.auth.domain.TokenRecord;
import com.readywealth.trading.trade_engine.auth.infrastructure.redis.TokenStore;
import com.readywealth.trading.trade_engine.session.domain.SessionUnauthorizedException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionIdentityServiceTest {

    @Test
    void resolvesValidUserId() {
        TokenStore tokenStore = Mockito.mock(TokenStore.class);
        Mockito.when(tokenStore.get()).thenReturn(Optional.of(new TokenRecord(
                "user-1",
                "access",
                "public",
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(60),
                false)));
        SessionIdentityService service = new SessionIdentityService(tokenStore);

        assertEquals("user-1", service.requireCurrentUserId());
    }

    @Test
    void rejectsExpiredToken() {
        TokenStore tokenStore = Mockito.mock(TokenStore.class);
        Mockito.when(tokenStore.get()).thenReturn(Optional.of(new TokenRecord(
                "user-1",
                "access",
                "public",
                Instant.now().minusSeconds(120),
                Instant.now().minusSeconds(1),
                true)));
        SessionIdentityService service = new SessionIdentityService(tokenStore);

        assertThrows(SessionUnauthorizedException.class, service::requireCurrentUserId);
    }
}
