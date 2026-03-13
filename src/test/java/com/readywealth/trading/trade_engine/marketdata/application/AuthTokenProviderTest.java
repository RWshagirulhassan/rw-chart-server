package com.readywealth.trading.trade_engine.marketdata.application;

import com.readywealth.trading.trade_engine.auth.domain.TokenRecord;
import com.readywealth.trading.trade_engine.auth.infrastructure.redis.TokenStore;
import com.readywealth.trading.trade_engine.marketdata.infrastructure.config.PublisherProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthTokenProviderTest {

    @Test
    void returnsNewlyAvailableTokenEvenWhenBackoffWindowIsActive() {
        TokenStore tokenStore = Mockito.mock(TokenStore.class);
        PublisherProperties properties = new PublisherProperties();
        properties.setRetryInitialDelayMs(60000);
        properties.setRetryMaxDelayMs(60000);
        properties.setTokenCacheSeconds(5);

        TokenRecord valid = new TokenRecord(
                "OWD611",
                "access-token-1",
                "public-token-1",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                false);

        Mockito.when(tokenStore.get())
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(valid));

        AuthTokenProvider provider = new AuthTokenProvider(tokenStore, properties);

        assertEquals(Optional.empty(), provider.getValidAccessToken());
        assertEquals(Optional.of("access-token-1"), provider.getValidAccessToken());
    }
}
