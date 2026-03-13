package com.readywealth.trading.trade_engine.marketdata.infrastructure.http;

import com.readywealth.trading.trade_engine.engine.domain.series.Candle;
import com.readywealth.trading.trade_engine.engine.domain.series.CandleCause;
import com.readywealth.trading.trade_engine.engine.domain.series.IntervalKind;
import com.readywealth.trading.trade_engine.marketdata.application.AuthTokenProvider;
import com.readywealth.trading.trade_engine.marketdata.application.HistoricalRequestRateLimiter;
import com.readywealth.trading.trade_engine.marketdata.application.SessionCandleBoundaryService;
import com.readywealth.trading.trade_engine.marketdata.application.port.HistoricalCandleClient;
import com.readywealth.trading.trade_engine.marketdata.infrastructure.config.BootstrapSchedulerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class HttpHistoricalCandleClient implements HistoricalCandleClient {
    private static final Logger log = LoggerFactory.getLogger(HttpHistoricalCandleClient.class);

    private static final DateTimeFormatter KITE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter KITE_TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

    private final RestTemplate http;
    private final AuthTokenProvider authTokenProvider;
    private final String kiteBaseUrl;
    private final String kiteApiKey;
    private final SessionCandleBoundaryService boundaryService;
    private final ZoneId exchangeZoneId;
    private final HistoricalRequestRateLimiter historicalRequestRateLimiter;

    @Autowired
    public HttpHistoricalCandleClient(
            AuthTokenProvider authTokenProvider,
            SessionCandleBoundaryService boundaryService,
            HistoricalRequestRateLimiter historicalRequestRateLimiter,
            @Value("${kite.base-url:https://api.kite.trade}") String kiteBaseUrl,
            @Value("${kite.api-key:}") String kiteApiKey) {
        this(authTokenProvider, boundaryService, kiteBaseUrl, kiteApiKey, new RestTemplate(), historicalRequestRateLimiter);
    }

    HttpHistoricalCandleClient(
            AuthTokenProvider authTokenProvider,
            String kiteBaseUrl,
            String kiteApiKey,
            String zoneIdStr,
            String marketOpenStr,
            String marketCloseStr) {
        this(
                authTokenProvider,
                new SessionCandleBoundaryService(zoneIdStr, marketOpenStr, marketCloseStr),
                kiteBaseUrl,
                kiteApiKey,
                new RestTemplate(),
                new HistoricalRequestRateLimiter(new BootstrapSchedulerProperties()));
    }

    private HttpHistoricalCandleClient(
            AuthTokenProvider authTokenProvider,
            SessionCandleBoundaryService boundaryService,
            String kiteBaseUrl,
            String kiteApiKey,
            RestTemplate restTemplate,
            HistoricalRequestRateLimiter historicalRequestRateLimiter) {
        this.http = restTemplate;
        this.authTokenProvider = authTokenProvider;
        this.boundaryService = boundaryService;
        this.kiteBaseUrl = kiteBaseUrl;
        this.kiteApiKey = kiteApiKey;
        this.exchangeZoneId = boundaryService.exchangeZoneId();
        this.historicalRequestRateLimiter = historicalRequestRateLimiter;
    }

    @Override
    public List<Candle> fetchCandlesInRange(long instrumentToken,
                                            IntervalKind intervalKind,
                                            Instant from,
                                            Instant to) {
        if (from == null || to == null) {
            log.warn("fetchCandlesInRange called with null instants: from={}, to={}, instrument={}",
                    from, to, instrumentToken);
            return Collections.emptyList();
        }
        if (to.isBefore(from)) {
            log.warn("fetchCandlesInRange called with invalid range: from={} > to={}, instrument={}",
                    from, to, instrumentToken);
            return Collections.emptyList();
        }

        IntervalMapping mapping = mapInterval(intervalKind);
        if (mapping == null) {
            log.warn("historical fetch unsupported for intervalKind={} instrument={}", intervalKind, instrumentToken);
            return Collections.emptyList();
        }
        if (kiteApiKey == null || kiteApiKey.isBlank()) {
            log.warn("historical fetch skipped: kite.api-key missing");
            return Collections.emptyList();
        }

        Optional<String> accessToken = authTokenProvider.getValidAccessToken();
        if (accessToken.isEmpty()) {
            log.warn("historical fetch skipped: access token unavailable");
            return Collections.emptyList();
        }

        ZonedDateTime fromZdt = from.atZone(exchangeZoneId);
        ZonedDateTime toZdt = to.atZone(exchangeZoneId);
        log.info(
                "historical_fetch_start instrument={} interval={} fromUtc={} toUtc={} fromExchange={} toExchange={} zone={}",
                instrumentToken, intervalKind, from, to, fromZdt, toZdt, exchangeZoneId);

        return fetchHistorical(instrumentToken, intervalKind, accessToken.get(), fromZdt, toZdt);
    }

    @Override
    public List<Candle> fetchRecentCandles(long instrumentToken, IntervalKind intervalKind) {
        return fetchRecentCandlesAt(instrumentToken, intervalKind, ZonedDateTime.now(exchangeZoneId).withNano(0), false);
    }

    @Override
    public List<Candle> fetchRecentCandlesForBootstrap(long instrumentToken, IntervalKind intervalKind) throws HistoricalFetchException {
        return fetchRecentCandlesAt(instrumentToken, intervalKind, ZonedDateTime.now(exchangeZoneId).withNano(0), true);
    }

    List<Candle> fetchRecentCandlesAt(long instrumentToken, IntervalKind intervalKind, ZonedDateTime now) {
        return fetchRecentCandlesAt(instrumentToken, intervalKind, now, false);
    }

    private List<Candle> fetchRecentCandlesAt(
            long instrumentToken,
            IntervalKind intervalKind,
            ZonedDateTime now,
            boolean bootstrapMode) {
        IntervalMapping mapping = mapInterval(intervalKind);
        if (mapping == null) {
            log.warn("historical recent unsupported intervalKind={} instrument={}", intervalKind, instrumentToken);
            return Collections.emptyList();
        }

        int lookback = lookbackBusinessDays(intervalKind);
        if (lookback <= 0) {
            log.warn("historical recent lookback unsupported intervalKind={} instrument={}", intervalKind,
                    instrumentToken);
            return Collections.emptyList();
        }
        if (kiteApiKey == null || kiteApiKey.isBlank()) {
            log.warn("historical recent skipped: kite.api-key missing");
            return Collections.emptyList();
        }

        Optional<String> accessToken = authTokenProvider.getValidAccessToken();
        if (accessToken.isEmpty()) {
            log.warn("historical recent skipped: access token unavailable");
            return Collections.emptyList();
        }

        ZonedDateTime toNow = clampToTradableNow(now.withNano(0), intervalKind, mapping.barDuration());
        LocalDate endDate = toNow.toLocalDate();
        LocalDate startDate = subtractBusinessDaysInclusive(endDate, lookback);
        ZonedDateTime fromDayStart = ZonedDateTime.of(startDate, LocalTime.MIDNIGHT, exchangeZoneId);
        ZonedDateTime barStart = floorToRecentBarStart(toNow, mapping.barDuration(), intervalKind);

        log.info(
                "historical_recent_start instrument={} interval={} lookbackDays={} fromDayStart={} barStart={} toNow={} bootstrapMode={}",
                instrumentToken, intervalKind, lookback, fromDayStart, barStart, toNow, bootstrapMode);

        List<Candle> out = new ArrayList<>();

        if (barStart.isAfter(fromDayStart)) {
            List<Candle> full = fetchHistorical(
                    instrumentToken,
                    intervalKind,
                    accessToken.get(),
                    fromDayStart,
                    barStart,
                    bootstrapMode);
            out.addAll(full);
            log.info("historical_recent_full_count instrument={} interval={} count={}",
                    instrumentToken, intervalKind, full.size());
        }

        if (toNow.isAfter(barStart)) {
            List<Candle> partial = fetchHistorical(
                    instrumentToken,
                    intervalKind,
                    accessToken.get(),
                    barStart,
                    toNow,
                    bootstrapMode);
            log.info("historical_recent_live_count instrument={} interval={} count={}",
                    instrumentToken, intervalKind, partial.size());

            if (!partial.isEmpty()) {
                long liveStart = barStart.toInstant().toEpochMilli();
                Candle seed = partial.stream()
                        .filter(c -> c.startTimeEpochMs() == liveStart)
                        .findFirst()
                        .orElseGet(() -> partial.get(partial.size() - 1));
                Candle live = new Candle(
                        instrumentToken,
                        intervalKind,
                        liveStart,
                        toNow.toInstant().toEpochMilli(),
                        seed.open(),
                        seed.high(),
                        seed.low(),
                        seed.close(),
                        seed.volume(),
                        0L,
                        1,
                        CandleCause.BACKFILL);
                out.removeIf(c -> c.startTimeEpochMs() == liveStart);
                out.add(live);
                log.info("historical_recent_live_applied instrument={} interval={} liveStart={} liveEnd={}",
                        instrumentToken, intervalKind, live.startTimeEpochMs(), live.endTimeEpochMs());
            }
        }

        out.sort(Comparator.comparingLong(Candle::startTimeEpochMs));

        long fromMs = fromDayStart.toInstant().toEpochMilli();
        long toMs = toNow.toInstant().toEpochMilli();
        out.removeIf(c -> {
            LocalDate startDateLocal = Instant.ofEpochMilli(c.startTimeEpochMs()).atZone(exchangeZoneId).toLocalDate();
            return isWeekend(startDateLocal) || c.startTimeEpochMs() < fromMs || c.startTimeEpochMs() > toMs;
        });

        log.info("historical_recent_result instrument={} interval={} count={} from={} to={}",
                instrumentToken, intervalKind, out.size(), fromDayStart, toNow);
        return out;
    }

    protected List<Candle> fetchHistorical(
            long instrumentToken,
            IntervalKind intervalKind,
            String accessToken,
            ZonedDateTime from,
            ZonedDateTime to) {
        return fetchHistorical(instrumentToken, intervalKind, accessToken, from, to, false);
    }

    private List<Candle> fetchHistorical(
            long instrumentToken,
            IntervalKind intervalKind,
            String accessToken,
            ZonedDateTime from,
            ZonedDateTime to,
            boolean bootstrapMode) {
        IntervalMapping mapping = mapInterval(intervalKind);
        if (mapping == null) {
            return Collections.emptyList();
        }
        return doHistoricalRequest(instrumentToken, intervalKind, mapping, accessToken, from, to, bootstrapMode);
    }

    private List<Candle> doHistoricalRequest(long instrumentToken,
                                             IntervalKind intervalKind,
                                             IntervalMapping mapping,
                                             String accessToken,
                                             ZonedDateTime from,
                                             ZonedDateTime to,
                                             boolean bootstrapMode) {
        String fromStr = from.format(KITE_DATE_FORMAT);
        String toStr = to.format(KITE_DATE_FORMAT);

        URI uri = UriComponentsBuilder
                .fromUriString(kiteBaseUrl)
                .pathSegment("instruments", "historical", String.valueOf(instrumentToken), mapping.kiteInterval())
                .queryParam("from", fromStr)
                .queryParam("to", toStr)
                .queryParam("continuous", "0")
                .queryParam("oi", "0")
                .build()
                .encode()
                .toUri();
        log.info("kite_historical_request uri={} instrument={} interval={} from={} to={}",
                uri, instrumentToken, intervalKind, fromStr, toStr);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Kite-Version", "3");
        headers.set("Authorization", "token " + kiteApiKey + ":" + accessToken);
        RequestEntity<Void> request = RequestEntity.get(uri).headers(headers).build();

        KiteHistoricalResponse body;
        try {
            historicalRequestRateLimiter.acquirePermit();
            ResponseEntity<KiteHistoricalResponse> response = executeHistoricalRequest(request);
            body = response.getBody();
        } catch (RestClientException exception) {
            HistoricalFetchException typed = toHistoricalFetchException(
                    instrumentToken, intervalKind, uri, exception);
            if (bootstrapMode) {
                throw typed;
            }
            log.warn("kite historical request failed for {}@{} url={} transient={} error={}",
                    instrumentToken, intervalKind, uri, typed.isTransientFailure(), exception.toString());
            return Collections.emptyList();
        }

        if (body == null || body.data == null || body.data.candles == null) {
            if (bootstrapMode) {
                throw new HistoricalFetchException(
                        "kite_historical_invalid_payload instrument=" + instrumentToken + " interval=" + intervalKind,
                        false);
            }
            log.info("kite_historical_empty instrument={} interval={} from={} to={}",
                    instrumentToken, intervalKind, fromStr, toStr);
            return Collections.emptyList();
        }
        if (body.data.candles.isEmpty()) {
            log.info("kite_historical_empty instrument={} interval={} from={} to={}",
                    instrumentToken, intervalKind, fromStr, toStr);
            return Collections.emptyList();
        }
        log.info("kite_historical_rows instrument={} interval={} rows={}",
                instrumentToken, intervalKind, body.data.candles.size());

        List<Candle> result = new ArrayList<>(body.data.candles.size());
        for (List<Object> row : body.data.candles) {
            if (row == null || row.size() < 6) {
                continue;
            }
            try {
                String ts = String.valueOf(row.get(0));
                double open = toDouble(row.get(1));
                double high = toDouble(row.get(2));
                double low = toDouble(row.get(3));
                double close = toDouble(row.get(4));
                long volume = toLong(row.get(5));

                Instant start = parseTimestamp(ts);
                Instant end = start.plus(mapping.barDuration());

                result.add(new Candle(
                        instrumentToken,
                        intervalKind,
                        start.toEpochMilli(),
                        end.toEpochMilli(),
                        open,
                        high,
                        low,
                        close,
                        volume,
                        0L,
                        1,
                        CandleCause.BACKFILL));
            } catch (Exception ignored) {
                // best-effort: skip malformed rows
            }
        }
        log.info("kite_historical_parsed instrument={} interval={} accepted={}", instrumentToken, intervalKind,
                result.size());
        return result;
    }

    private HistoricalFetchException toHistoricalFetchException(
            long instrumentToken,
            IntervalKind intervalKind,
            URI uri,
            RestClientException exception) {
        boolean transientFailure = false;
        String reason = "kite_historical_request_failed";

        if (exception instanceof HttpStatusCodeException httpStatusCodeException) {
            int status = httpStatusCodeException.getStatusCode().value();
            transientFailure = status == 429 || status >= 500;
            reason = "kite_historical_http_status_" + status;
        } else if (exception instanceof ResourceAccessException) {
            transientFailure = true;
            reason = "kite_historical_resource_access";
        } else if (exception.getCause() instanceof SocketTimeoutException
                || exception.getCause() instanceof IOException) {
            transientFailure = true;
            reason = "kite_historical_io_failure";
        }

        String message = reason + " instrument=" + instrumentToken + " interval=" + intervalKind + " uri=" + uri;
        return new HistoricalFetchException(message, exception, transientFailure);
    }

    private static double toDouble(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(String.valueOf(o));
    }

    private static long toLong(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(o));
    }

    private static Instant parseTimestamp(String ts) {
        try {
            return OffsetDateTime.parse(ts, KITE_TS_FORMAT).toInstant();
        } catch (Exception ignored) {
            return OffsetDateTime.parse(ts, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
        }
    }

    private IntervalMapping mapInterval(IntervalKind kind) {
        return switch (kind) {
            case TIME_1M -> new IntervalMapping("minute", Duration.ofMinutes(1));
            case TIME_2M -> new IntervalMapping("2minute", Duration.ofMinutes(2));
            case TIME_3M -> new IntervalMapping("3minute", Duration.ofMinutes(3));
            case TIME_5M -> new IntervalMapping("5minute", Duration.ofMinutes(5));
            case TIME_10M -> new IntervalMapping("10minute", Duration.ofMinutes(10));
            case TIME_15M -> new IntervalMapping("15minute", Duration.ofMinutes(15));
            case TIME_30M -> new IntervalMapping("30minute", Duration.ofMinutes(30));
            case TIME_45M -> new IntervalMapping("45minute", Duration.ofMinutes(45));
            case TIME_1H -> new IntervalMapping("60minute", Duration.ofHours(1));
            case TIME_1D -> new IntervalMapping("day", Duration.ofDays(1));
            default -> null;
        };
    }

    private int lookbackBusinessDays(IntervalKind kind) {
        return switch (kind) {
            case TIME_1M, TIME_2M, TIME_3M -> 3;
            case TIME_5M, TIME_10M, TIME_15M -> 10;
            case TIME_30M, TIME_45M, TIME_1H -> 60;
            case TIME_1D -> 600;
            default -> 0;
        };
    }

    private ZonedDateTime alignToBusinessDay(ZonedDateTime dt) {
        DayOfWeek dayOfWeek = dt.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY) {
            return dt.minusDays(1);
        }
        if (dayOfWeek == DayOfWeek.SUNDAY) {
            return dt.minusDays(2);
        }
        return dt;
    }

    private LocalDate subtractBusinessDaysInclusive(LocalDate end, int businessDays) {
        LocalDate d = end;
        int remaining = businessDays - 1;
        while (remaining > 0) {
            d = d.minusDays(1);
            if (!isWeekend(d)) {
                remaining--;
            }
        }
        return d;
    }

    private boolean isWeekend(LocalDate d) {
        DayOfWeek dayOfWeek = d.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }

    private ZonedDateTime floorToRecentBarStart(ZonedDateTime zdt, Duration barDuration, IntervalKind intervalKind) {
        return boundaryService.floorToBucketStart(zdt, barDuration, intervalKind);
    }

    private boolean isIntradayInterval(IntervalKind intervalKind, Duration barDuration) {
        return boundaryService.isIntradayInterval(intervalKind, barDuration);
    }

    private ZonedDateTime floorToSessionBucket(ZonedDateTime zdt, Duration barDuration, LocalTime sessionOpen) {
        ZonedDateTime candidate = zdt.withSecond(0).withNano(0);
        ZonedDateTime sessionStart = ZonedDateTime.of(candidate.toLocalDate(), sessionOpen, exchangeZoneId);
        if (!candidate.isAfter(sessionStart)) {
            return sessionStart;
        }
        long bucketMillis = Math.max(1L, barDuration.toMillis());
        long elapsedMillis = Duration.between(sessionStart, candidate).toMillis();
        long completedBuckets = elapsedMillis / bucketMillis;
        return sessionStart.plus(Duration.ofMillis(bucketMillis * completedBuckets));
    }

    private ZonedDateTime clampToTradableNow(ZonedDateTime now, IntervalKind intervalKind, Duration barDuration) {
        return boundaryService.clampToTradableNow(now, intervalKind, barDuration);
    }

    private LocalDate previousBusinessDay(LocalDate date) {
        LocalDate out = date.minusDays(1);
        while (isWeekend(out)) {
            out = out.minusDays(1);
        }
        return out;
    }

    private ZonedDateTime floorToBarBoundary(ZonedDateTime zdt, Duration barDuration) {
        long minutes = barDuration.toMinutes();
        zdt = zdt.withNano(0);

        if (minutes >= 1440) {
            return ZonedDateTime.of(zdt.toLocalDate(), LocalTime.MIDNIGHT, exchangeZoneId).withSecond(0);
        }
        if (minutes >= 60 && minutes % 60 == 0) {
            long hours = minutes / 60;
            int flooredHour = (int) ((zdt.getHour() / hours) * hours);
            return zdt.withHour(flooredHour).withMinute(0).withSecond(0);
        }
        if (minutes > 0) {
            int flooredMinute = (int) ((zdt.getMinute() / minutes) * minutes);
            return zdt.withMinute(flooredMinute).withSecond(0);
        }
        return zdt.withSecond(0);
    }

    protected ResponseEntity<KiteHistoricalResponse> executeHistoricalRequest(RequestEntity<Void> request) {
        return http.exchange(request, KiteHistoricalResponse.class);
    }

    static class KiteHistoricalResponse {
        public String status;
        public Data data;

        static class Data {
            public List<List<Object>> candles;
        }
    }

    private record IntervalMapping(String kiteInterval, Duration barDuration) {
    }
}
