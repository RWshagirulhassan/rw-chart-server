package com.readywealth.trading.trade_engine.marketdata.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.readywealth.trading.trade_engine.marketdata.domain.InstrumentSearchResult;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class InstrumentSearchService {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<String> EXCHANGES = List.of("NSE", "BSE");
    private static final Pattern NON_ALNUM = Pattern.compile("[^A-Z0-9]+");

    public InstrumentSearchService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String s(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String norm(String value) {
        if (value == null) {
            return "";
        }
        String up = value.trim().toUpperCase(Locale.ROOT);
        return NON_ALNUM.matcher(up).replaceAll("");
    }

    private static String lexEnd(String prefix) {
        return prefix + "\uFFFF";
    }

    public List<InstrumentSearchResult> lookupExact(String symbol) {
        String sym = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        if (sym.isEmpty()) {
            return List.of();
        }

        List<Object> piped = redis.executePipelined((RedisCallback<Object>) connection -> {
            byte[] field = b(sym);
            for (String ex : EXCHANGES) {
                connection.hashCommands().hGet(b("inst:" + ex + ":sym2row"), field);
            }
            return null;
        });

        List<InstrumentSearchResult> out = new ArrayList<>();
        for (int i = 0; i < EXCHANGES.size(); i++) {
            String ex = EXCHANGES.get(i);
            Object v = piped.get(i);
            if (v == null) {
                continue;
            }
            String json = (v instanceof byte[] bytes) ? s(bytes) : String.valueOf(v);
            parseRowJson(json, ex).ifPresent(out::add);
        }
        return out;
    }

    public Optional<InstrumentSearchResult> lookupBySymbolAndExchange(String symbol, String exchange) {
        String sym = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        String ex = exchange == null ? "" : exchange.trim().toUpperCase(Locale.ROOT);
        if (sym.isEmpty() || ex.isEmpty()) {
            return Optional.empty();
        }

        Object raw = redis.opsForHash().get("inst:" + ex + ":sym2row", sym);
        if (raw == null) {
            return Optional.empty();
        }
        String json = raw instanceof byte[] bytes ? s(bytes) : String.valueOf(raw);
        return parseRowJson(json, ex);
    }

    public List<InstrumentSearchResult> suggest(String query, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        String raw = query == null ? "" : query.trim();
        if (raw.isEmpty()) {
            return List.of();
        }

        String symbolPrefixRaw = raw.toUpperCase(Locale.ROOT);
        String namePrefix = norm(raw);
        int fetchWindow = safeLimit;

        final int wSymbol = 3000;
        final int wName = 2500;
        final int wExact = 5000;
        final int bonusNse = 30;
        final int bonusBse = 0;

        class Cand {
            final String ex;
            final String sym;
            int baseScore;
            int bestPos;

            Cand(String ex, String sym, int baseScore, int bestPos) {
                this.ex = ex;
                this.sym = sym;
                this.baseScore = baseScore;
                this.bestPos = bestPos;
            }

            void improve(int newBaseScore, int newPos) {
                if (newBaseScore > this.baseScore) {
                    this.baseScore = newBaseScore;
                    this.bestPos = newPos;
                } else if (newBaseScore == this.baseScore && newPos < this.bestPos) {
                    this.bestPos = newPos;
                }
            }
        }

        LinkedHashMap<String, Cand> candidates = new LinkedHashMap<>();

        for (String ex : EXCHANGES) {
            String symSuggestKey = "inst:" + ex + ":suggest";
            String nameSuggestKey = "inst:" + ex + ":name_suggest";

            List<String> symHits = new ArrayList<>(
                    zRangeByLex(symSuggestKey, symbolPrefixRaw, lexEnd(symbolPrefixRaw), fetchWindow)
            );

            for (int i = 0; i < symHits.size(); i++) {
                String sym = symHits.get(i);
                int score = wSymbol - i;
                if (sym.equalsIgnoreCase(symbolPrefixRaw)) {
                    score += wExact;
                }
                String key = ex + ":" + sym;
                int pos = i;
                int finalScore = score;
                candidates.compute(key, (k, existing) -> {
                    if (existing == null) {
                        return new Cand(ex, sym, finalScore, pos);
                    }
                    existing.improve(finalScore, pos);
                    return existing;
                });
            }

            if (!namePrefix.isEmpty()) {
                List<String> nameHits = new ArrayList<>(
                        zRangeByLex(nameSuggestKey, namePrefix, lexEnd(namePrefix), fetchWindow)
                );
                for (int i = 0; i < nameHits.size(); i++) {
                    String member = nameHits.get(i);
                    int p = member.indexOf('|');
                    if (p <= 0 || p >= member.length() - 1) {
                        continue;
                    }
                    String sym = member.substring(p + 1);
                    if (sym.isBlank()) {
                        continue;
                    }
                    int score = wName - i;
                    String key = ex + ":" + sym;
                    int pos = i;
                    candidates.compute(key, (k, existing) -> {
                        if (existing == null) {
                            return new Cand(ex, sym, score, pos);
                        }
                        existing.improve(score, pos);
                        return existing;
                    });
                }
            }
        }

        if (candidates.isEmpty()) {
            return List.of();
        }

        List<Cand> candList = new ArrayList<>(candidates.values());
        List<Object> pipedRows = redis.executePipelined((RedisCallback<Object>) connection -> {
            for (Cand c : candList) {
                connection.hashCommands().hGet(b("inst:" + c.ex + ":sym2row"), b(c.sym));
            }
            return null;
        });

        class Scored {
            final InstrumentSearchResult r;
            final int score;
            final int exPref;

            Scored(InstrumentSearchResult r, int score, int exPref) {
                this.r = r;
                this.score = score;
                this.exPref = exPref;
            }
        }

        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < pipedRows.size(); i++) {
            Object v = pipedRows.get(i);
            if (v == null) {
                continue;
            }
            Cand c = candList.get(i);
            String json = (v instanceof byte[] bytes) ? s(bytes) : String.valueOf(v);
            Optional<InstrumentSearchResult> parsed = parseRowJson(json, c.ex);
            if (parsed.isEmpty()) {
                continue;
            }
            int exBonus = "NSE".equalsIgnoreCase(c.ex) ? bonusNse : bonusBse;
            int finalScore = c.baseScore + exBonus - c.bestPos;
            int exPref = "NSE".equalsIgnoreCase(c.ex) ? 1 : 0;
            scored.add(new Scored(parsed.get(), finalScore, exPref));
        }

        Map<String, Scored> best = new HashMap<>();
        for (Scored sc : scored) {
            String key = (sc.r.exchange() == null ? "" : sc.r.exchange()) + ":" + sc.r.instrument_token();
            best.merge(key, sc, (a, b2) -> b2.score > a.score ? b2 : a);
        }

        List<Scored> merged = new ArrayList<>(best.values());
        merged.sort(Comparator
                .comparingInt((Scored sc) -> sc.score).reversed()
                .thenComparingInt(sc -> sc.exPref).reversed()
                .thenComparing(sc -> sc.r.tradingsymbol() == null ? "" : sc.r.tradingsymbol())
        );

        List<InstrumentSearchResult> out = new ArrayList<>(safeLimit);
        for (Scored sc : merged) {
            out.add(sc.r);
            if (out.size() >= safeLimit) {
                break;
            }
        }
        return out;
    }

    private Set<String> zRangeByLex(String key, String minInclusive, String maxInclusive, int count) {
        return redis.execute((RedisCallback<Set<String>>) connection -> {
            Range<byte[]> range = Range.rightOpen(b(minInclusive), b(maxInclusive)); // [min, max)
            Limit limit = Limit.limit().offset(0).count(count);
            Set<byte[]> raw = connection.zSetCommands().zRangeByLex(b(key), range, limit);
            if (raw == null || raw.isEmpty()) {
                return Collections.emptySet();
            }
            LinkedHashSet<String> out = new LinkedHashSet<>(raw.size());
            for (byte[] m : raw) {
                out.add(s(m));
            }
            return out;
        });
    }

    private Optional<InstrumentSearchResult> parseRowJson(String json, String fallbackExchange) {
        try {
            JsonNode node = objectMapper.readTree(json);
            String instrumentToken = node.path("instrument_token").asText("");
            if (instrumentToken.isBlank()) {
                return Optional.empty();
            }
            String tradingsymbol = node.path("tradingsymbol").asText("");
            String name = node.path("name").asText(tradingsymbol);
            String exchange = node.path("exchange").asText(fallbackExchange);
            return Optional.of(new InstrumentSearchResult(
                    instrumentToken,
                    name,
                    tradingsymbol.isBlank() ? null : tradingsymbol,
                    exchange.isBlank() ? fallbackExchange : exchange
            ));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
