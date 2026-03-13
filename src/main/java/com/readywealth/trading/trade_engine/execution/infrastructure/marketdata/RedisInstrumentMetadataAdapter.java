package com.readywealth.trading.trade_engine.execution.infrastructure.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.readywealth.trading.trade_engine.execution.application.port.InstrumentMetadataPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class RedisInstrumentMetadataAdapter implements InstrumentMetadataPort {

    private static final String[] EXCHANGES = new String[] {"NSE", "BSE", "NFO", "MCX"};

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    private volatile long cacheBuiltAtEpochMs;
    private volatile Map<Long, InstrumentMeta> cache = Map.of();

    public RedisInstrumentMetadataAdapter(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<InstrumentMeta> resolve(long instrumentToken) {
        if (instrumentToken <= 0) {
            return Optional.empty();
        }
        refreshIfNeeded();
        return Optional.ofNullable(cache.get(instrumentToken));
    }

    private synchronized void refreshIfNeeded() {
        long now = System.currentTimeMillis();
        if (!cache.isEmpty() && now - cacheBuiltAtEpochMs < 300_000L) {
            return;
        }
        Map<Long, InstrumentMeta> next = new LinkedHashMap<>();
        for (String exchange : EXCHANGES) {
            Map<Object, Object> rows = redis.opsForHash().entries("inst:" + exchange + ":sym2row");
            if (rows == null || rows.isEmpty()) {
                continue;
            }
            for (Object value : rows.values()) {
                if (value == null) {
                    continue;
                }
                try {
                    JsonNode node = objectMapper.readTree(String.valueOf(value));
                    long token = node.path("instrument_token").asLong(0);
                    if (token <= 0) {
                        continue;
                    }
                    String symbol = node.path("tradingsymbol").asText("");
                    String ex = node.path("exchange").asText(exchange);
                    next.put(token, new InstrumentMeta(token, symbol, ex));
                } catch (Exception ignored) {
                    // ignore malformed rows
                }
            }
        }
        cache = Map.copyOf(next);
        cacheBuiltAtEpochMs = Instant.now().toEpochMilli();
    }
}
