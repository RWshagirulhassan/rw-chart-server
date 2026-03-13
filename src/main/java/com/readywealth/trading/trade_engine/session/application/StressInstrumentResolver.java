package com.readywealth.trading.trade_engine.session.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.readywealth.trading.trade_engine.session.infrastructure.config.StressSessionProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class StressInstrumentResolver {
    private static final String NFO_KEY = "inst:NFO:sym2row";
    private static final String NSE_KEY = "inst:NSE:sym2row";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StressInstrumentResolver(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public List<Long> resolveInstrumentTokens(StressSessionProperties properties) {
        List<Long> configuredTokens = properties.normalizedInstrumentTokens();
        if (!configuredTokens.isEmpty()) {
            int limit = Math.min(properties.resolvedMaxInstruments(), configuredTokens.size());
            return configuredTokens.subList(0, limit);
        }

        Set<String> symbolAllowList = Set.copyOf(properties.normalizedSymbols());
        int limit = properties.resolvedMaxInstruments();

        Set<String> nfoNames = loadDistinctNfoNames();
        if (nfoNames.isEmpty()) {
            return List.of();
        }

        List<SymbolToken> matches = loadMatchingNseTokens(nfoNames, symbolAllowList);
        matches.sort(Comparator
                .comparing(SymbolToken::symbol)
                .thenComparingLong(SymbolToken::instrumentToken));

        List<Long> out = new ArrayList<>();
        LinkedHashSet<Long> deduped = new LinkedHashSet<>();
        for (SymbolToken item : matches) {
            if (deduped.add(item.instrumentToken())) {
                out.add(item.instrumentToken());
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return out;
    }

    private Set<String> loadDistinctNfoNames() {
        Map<Object, Object> rows = redis.opsForHash().entries(NFO_KEY);
        if (rows == null || rows.isEmpty()) {
            return Set.of();
        }

        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Object raw : rows.values()) {
            JsonNode node = parseJson(raw);
            if (node == null) {
                continue;
            }
            String name = normalize(node.path("name").asText(""));
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    private List<SymbolToken> loadMatchingNseTokens(Set<String> nfoNames, Set<String> symbolAllowList) {
        Map<Object, Object> rows = redis.opsForHash().entries(NSE_KEY);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<SymbolToken> matches = new ArrayList<>();
        for (Object raw : rows.values()) {
            JsonNode node = parseJson(raw);
            if (node == null) {
                continue;
            }
            String symbol = normalize(node.path("tradingsymbol").asText(""));
            if (symbol.isEmpty()) {
                continue;
            }
            if (!nfoNames.contains(symbol)) {
                continue;
            }
            if (!symbolAllowList.isEmpty() && !symbolAllowList.contains(symbol)) {
                continue;
            }
            long token = parsePositiveLong(node.path("instrument_token"));
            if (token <= 0) {
                continue;
            }
            matches.add(new SymbolToken(symbol, token));
        }
        return matches;
    }

    private JsonNode parseJson(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.readTree(String.valueOf(raw));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private long parsePositiveLong(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return 0L;
        }
        if (node.isIntegralNumber()) {
            long value = node.asLong(0L);
            return Math.max(0L, value);
        }
        String text = node.asText("").trim();
        if (text.isEmpty()) {
            return 0L;
        }
        try {
            long value = Long.parseLong(text);
            return Math.max(0L, value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private record SymbolToken(String symbol, long instrumentToken) {
    }
}
