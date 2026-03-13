package com.readywealth.trading.trade_engine.marketdata.application;

import com.readywealth.trading.trade_engine.marketdata.domain.InstrumentSearchResult;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstrumentSearchServiceTest {

    @Test
    void lookupBySymbolAndExchangeReturnsInstrumentFromRedisHash() {
        StringRedisTemplate redis = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = Mockito.mock(HashOperations.class);
        Mockito.when(redis.opsForHash()).thenReturn(hashOperations);
        Mockito.when(hashOperations.get("inst:NSE:sym2row", "INFY"))
                .thenReturn("""
                        {
                          "instrument_token":"408065",
                          "name":"Infosys Ltd",
                          "tradingsymbol":"INFY",
                          "exchange":"NSE"
                        }
                        """);

        InstrumentSearchService service = new InstrumentSearchService(redis);

        Optional<InstrumentSearchResult> result = service.lookupBySymbolAndExchange("infy", "nse");

        assertTrue(result.isPresent());
        assertEquals("408065", result.get().instrument_token());
        assertEquals("INFY", result.get().tradingsymbol());
        assertEquals("NSE", result.get().exchange());
    }

    @Test
    void lookupBySymbolAndExchangeReturnsEmptyWhenHashMisses() {
        StringRedisTemplate redis = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = Mockito.mock(HashOperations.class);
        Mockito.when(redis.opsForHash()).thenReturn(hashOperations);
        Mockito.when(hashOperations.get("inst:NSE:sym2row", "INFY")).thenReturn(null);

        InstrumentSearchService service = new InstrumentSearchService(redis);

        assertEquals(Optional.empty(), service.lookupBySymbolAndExchange("INFY", "NSE"));
    }
}
