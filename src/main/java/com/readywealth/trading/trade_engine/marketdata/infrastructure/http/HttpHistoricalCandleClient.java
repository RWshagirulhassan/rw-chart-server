package com.readywealth.trading.trade_engine.marketdata.infrastructure.http;

import com.readywealth.trading.trade_engine.engine.domain.series.Candle;
import com.readywealth.trading.trade_engine.engine.domain.series.CandleCause;
import com.readywealth.trading.trade_engine.engine.domain.series.IntervalDescriptor;
import com.readywealth.trading.trade_engine.engine.domain.series.IntervalKind;
import com.readywealth.trading.trade_engine.engine.domain.series.IntervalDescriptors;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final BootstrapSchedulerProperties schedulerProperties;

    @Autowired
    public HttpHistoricalCandleClient(
            AuthTokenProvider authTokenProvider,
            SessionCandleBoundaryService boundaryService,
            HistoricalRequestRateLimiter historicalRequestRateLimiter,
            BootstrapSchedulerProperties schedulerProperties,
            @Value("${kite.base-url:https://api.kite.trade}") String kiteBaseUrl,
            @Value("${kite.api-key:}") String kiteApiKey) {
        this(
                authTokenProvider,
                boundaryService,
                kiteBaseUrl,
                kiteApiKey,
                new RestTemplate(),
                historicalRequestRateLimiter,
                schedulerProperties);
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
                new HistoricalRequestRateLimiter(new BootstrapSchedulerProperties()),
                new BootstrapSchedulerProperties());
    }

    private HttpHistoricalCandleClient(
            AuthTokenProvider authTokenProvider,
            SessionCandleBoundaryService boundaryService,
            String kiteBaseUrl,
            String kiteApiKey,
            RestTemplate restTemplate,
            HistoricalRequestRateLimiter historicalRequestRateLimiter,
            BootstrapSchedulerProperties schedulerProperties) {
        this.http = restTemplate;
        this.authTokenProvider = authTokenProvider;
        this.boundaryService = boundaryService;
        this.kiteBaseUrl = kiteBaseUrl;
        this.kiteApiKey = kiteApiKey;
        this.exchangeZoneId = boundaryService.exchangeZoneId();
        this.historicalRequestRateLimiter = historicalRequestRateLimiter;
        this.schedulerProperties = schedulerProperties;
    }

    HttpHistoricalCandleClient(
            AuthTokenProvider authTokenProvider,
            SessionCandleBoundaryService boundaryService,
            String kiteBaseUrl,
            String kiteApiKey,
            RestTemplate restTemplate,
            HistoricalRequestRateLimiter historicalRequestRateLimiter,
            BootstrapSchedulerProperties schedulerProperties,
            boolean unusedTestHook) {
        this(
                authTokenProvider,
                boundaryService,
                kiteBaseUrl,
                kiteApiKey,
                restTemplate,
                historicalRequestRateLimiter,
                schedulerProperties);
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

        IntervalDescriptor descriptor = IntervalDescriptors.of(intervalKind);
        if (descriptor.isTick()) {
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

        return fetchCandlesInRange(instrumentToken, descriptor, accessToken.get(), fromZdt, toZdt, false);
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
        IntervalDescriptor requestedDescriptor = IntervalDescriptors.of(intervalKind);
        if (requestedDescriptor.isTick()) {
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

        IntervalDescriptor sourceDescriptor = IntervalDescriptors.of(requestedDescriptor.sourceIntervalKindOrSelf());
        ZonedDateTime toNow = clampToTradableNow(now.withNano(0), sourceDescriptor);
        LocalDate endDate = toNow.toLocalDate();
        LocalDate startDate = subtractBusinessDaysInclusive(endDate, lookback);
        ZonedDateTime requestedFromDayStart = ZonedDateTime.of(startDate, LocalTime.MIDNIGHT, exchangeZoneId);
        ZonedDateTime sourceFetchStart = requestedDescriptor.isDerivedFromSource()
                ? boundaryService.floorToBucketStart(requestedFromDayStart, requestedDescriptor)
                : requestedFromDayStart;
        ZonedDateTime barStart = floorToRecentBarStart(toNow, sourceDescriptor);

        log.info(
                "historical_recent_start instrument={} interval={} sourceInterval={} lookbackDays={} sourceFetchStart={} barStart={} toNow={} bootstrapMode={}",
                instrumentToken,
                intervalKind,
                sourceDescriptor.kind(),
                lookback,
                sourceFetchStart,
                barStart,
                toNow,
                bootstrapMode);

        List<Candle> sourceCandles = new ArrayList<>();

        if (barStart.isAfter(sourceFetchStart)) {
            List<Candle> full = requestedDescriptor.isDerivedFromSource()
                    ? fetchHistoricalInChunks(
                            instrumentToken,
                            sourceDescriptor.kind(),
                            accessToken.get(),
                            sourceFetchStart,
                            barStart,
                            bootstrapMode)
                    : fetchHistorical(
                            instrumentToken,
                            sourceDescriptor.kind(),
                            accessToken.get(),
                            sourceFetchStart,
                            barStart,
                            bootstrapMode);
            sourceCandles.addAll(full);
            log.info("historical_recent_full_count instrument={} interval={} sourceInterval={} count={}",
                    instrumentToken, intervalKind, sourceDescriptor.kind(), full.size());
        }

        if (toNow.isAfter(barStart)) {
            List<Candle> partial = fetchHistorical(
                    instrumentToken,
                    sourceDescriptor.kind(),
                    accessToken.get(),
                    barStart,
                    toNow,
                    bootstrapMode);
            log.info("historical_recent_live_count instrument={} interval={} sourceInterval={} count={}",
                    instrumentToken, intervalKind, sourceDescriptor.kind(), partial.size());

            if (!partial.isEmpty()) {
                long liveStart = barStart.toInstant().toEpochMilli();
                Candle seed = partial.stream()
                        .filter(c -> c.startTimeEpochMs() == liveStart)
                        .findFirst()
                        .orElseGet(() -> partial.get(partial.size() - 1));
                Candle live = new Candle(
                        instrumentToken,
                        sourceDescriptor.kind(),
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
                sourceCandles.removeIf(c -> c.startTimeEpochMs() == liveStart);
                sourceCandles.add(live);
                log.info("historical_recent_live_applied instrument={} interval={} sourceInterval={} liveStart={} liveEnd={}",
                        instrumentToken, intervalKind, sourceDescriptor.kind(), live.startTimeEpochMs(), live.endTimeEpochMs());
            }
        }

        List<Candle> normalizedSource = sortAndDedupeCandles(sourceCandles);

        long fromMs = sourceFetchStart.toInstant().toEpochMilli();
        long toMs = toNow.toInstant().toEpochMilli();
        normalizedSource.removeIf(c -> {
            LocalDate startDateLocal = Instant.ofEpochMilli(c.startTimeEpochMs()).atZone(exchangeZoneId).toLocalDate();
            return isWeekend(startDateLocal) || c.startTimeEpochMs() < fromMs || c.startTimeEpochMs() > toMs;
        });

        List<Candle> requestedCandles = requestedDescriptor.isDerivedFromSource()
                ? aggregateCandles(instrumentToken, requestedDescriptor, normalizedSource)
                : normalizedSource;
        requestedCandles = applyRecentCandleLimit(intervalKind, requestedCandles);

        log.info("historical_recent_result instrument={} interval={} count={} from={} to={}",
                instrumentToken, intervalKind, requestedCandles.size(), sourceFetchStart, toNow);
        return requestedCandles;
    }

    protected List<Candle> fetchHistorical(
            long instrumentToken,
            IntervalKind intervalKind,
            String accessToken,
            ZonedDateTime from,
            ZonedDateTime to) {
        IntervalDescriptor descriptor = IntervalDescriptors.of(intervalKind);
        if (!descriptor.supportsVendorHistorical()) {
            return Collections.emptyList();
        }
        return doHistoricalRequest(instrumentToken, descriptor, accessToken, from, to, false);
    }

    private List<Candle> fetchHistorical(
            long instrumentToken,
            IntervalKind intervalKind,
            String accessToken,
            ZonedDateTime from,
            ZonedDateTime to,
            boolean bootstrapMode) {
        IntervalDescriptor descriptor = IntervalDescriptors.of(intervalKind);
        if (!descriptor.supportsVendorHistorical()) {
            return Collections.emptyList();
        }
        if (!bootstrapMode) {
            return fetchHistorical(instrumentToken, intervalKind, accessToken, from, to);
        }
        return doHistoricalRequest(instrumentToken, descriptor, accessToken, from, to, bootstrapMode);
    }

    private List<Candle> doHistoricalRequest(long instrumentToken,
                                             IntervalDescriptor descriptor,
                                             String accessToken,
                                             ZonedDateTime from,
                                             ZonedDateTime to,
                                             boolean bootstrapMode) {
        String fromStr = from.format(KITE_DATE_FORMAT);
        String toStr = to.format(KITE_DATE_FORMAT);

        URI uri = UriComponentsBuilder
                .fromUriString(kiteBaseUrl)
                .pathSegment("instruments", "historical", String.valueOf(instrumentToken), descriptor.kiteInterval())
                .queryParam("from", fromStr)
                .queryParam("to", toStr)
                .queryParam("continuous", "0")
                .queryParam("oi", "0")
                .build()
                .encode()
                .toUri();
        log.info("kite_historical_request uri={} instrument={} interval={} from={} to={}",
                uri, instrumentToken, descriptor.kind(), fromStr, toStr);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Kite-Version", "3");
        headers.set("Authorization", "token " + kiteApiKey + ":" + accessToken);
        RequestEntity<Void> request = RequestEntity.get(uri).headers(headers).build();

        KiteHistoricalResponse body;
        try {
            acquireHistoricalPermit();
            ResponseEntity<KiteHistoricalResponse> response = executeHistoricalRequest(request);
            body = response.getBody();
        } catch (RestClientException exception) {
            HistoricalFetchException typed = toHistoricalFetchException(
                    instrumentToken, descriptor.kind(), uri, exception);
            if (bootstrapMode) {
                throw typed;
            }
            log.warn("kite historical request failed for {}@{} url={} transient={} error={}",
                    instrumentToken, descriptor.kind(), uri, typed.isTransientFailure(), exception.toString());
            return Collections.emptyList();
        }

        if (body == null || body.data == null || body.data.candles == null) {
            if (bootstrapMode) {
                throw new HistoricalFetchException(
                        "kite_historical_invalid_payload instrument=" + instrumentToken + " interval=" + descriptor.kind(),
                        false);
            }
            log.info("kite_historical_empty instrument={} interval={} from={} to={}",
                    instrumentToken, descriptor.kind(), fromStr, toStr);
            return Collections.emptyList();
        }
        if (body.data.candles.isEmpty()) {
            log.info("kite_historical_empty instrument={} interval={} from={} to={}",
                    instrumentToken, descriptor.kind(), fromStr, toStr);
            return Collections.emptyList();
        }
        log.info("kite_historical_rows instrument={} interval={} rows={}",
                instrumentToken, descriptor.kind(), body.data.candles.size());

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
                Instant end = start.plus(descriptor.fixedDurationOrThrow());

                result.add(new Candle(
                        instrumentToken,
                        descriptor.kind(),
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
        log.info("kite_historical_parsed instrument={} interval={} accepted={}", instrumentToken, descriptor.kind(),
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

    private List<Candle> fetchCandlesInRange(
            long instrumentToken,
            IntervalDescriptor descriptor,
            String accessToken,
            ZonedDateTime from,
            ZonedDateTime to,
            boolean bootstrapMode) {
        if (!descriptor.isDerivedFromSource()) {
            return fetchHistorical(instrumentToken, descriptor.kind(), accessToken, from, to, bootstrapMode);
        }
        ZonedDateTime sourceFrom = boundaryService.floorToBucketStart(from, descriptor);
        List<Candle> sourceCandles = fetchHistoricalInChunks(
                instrumentToken,
                descriptor.sourceIntervalKindOrSelf(),
                accessToken,
                sourceFrom,
                to,
                bootstrapMode);
        List<Candle> aggregated = aggregateCandles(instrumentToken, descriptor, sourceCandles);
        long fromMs = from.toInstant().toEpochMilli();
        long toMs = to.toInstant().toEpochMilli();
        aggregated.removeIf(candle -> candle.endTimeEpochMs() <= fromMs || candle.startTimeEpochMs() > toMs);
        return aggregated;
    }

    private int lookbackBusinessDays(IntervalKind kind) {
        return schedulerProperties.lookbackBusinessDays(kind);
    }

    private List<Candle> applyRecentCandleLimit(IntervalKind kind, List<Candle> candles) {
        int limit = schedulerProperties.recentCandleLimit(kind);
        if (limit <= 0 || candles == null || candles.size() <= limit) {
            return candles;
        }
        return new ArrayList<>(candles.subList(candles.size() - limit, candles.size()));
    }

    private List<Candle> fetchHistoricalInChunks(
            long instrumentToken,
            IntervalKind intervalKind,
            String accessToken,
            ZonedDateTime from,
            ZonedDateTime to,
            boolean bootstrapMode) {
        if (!to.isAfter(from)) {
            return Collections.emptyList();
        }
        int chunkDays = schedulerProperties.historicalChunkDays();
        List<Candle> combined = new ArrayList<>();
        ZonedDateTime cursor = from;
        while (!cursor.isAfter(to)) {
            ZonedDateTime chunkEnd = cursor.plusDays(chunkDays);
            if (chunkEnd.isAfter(to)) {
                chunkEnd = to;
            }
            combined.addAll(fetchHistorical(instrumentToken, intervalKind, accessToken, cursor, chunkEnd, bootstrapMode));
            if (!chunkEnd.isBefore(to)) {
                break;
            }
            cursor = chunkEnd;
        }
        return sortAndDedupeCandles(combined);
    }

    private List<Candle> sortAndDedupeCandles(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, Candle> byStart = new LinkedHashMap<>();
        List<Candle> sorted = new ArrayList<>(candles);
        sorted.sort(Comparator.comparingLong(Candle::startTimeEpochMs));
        for (Candle candle : sorted) {
            byStart.put(candle.startTimeEpochMs(), candle);
        }
        return new ArrayList<>(byStart.values());
    }

    private List<Candle> aggregateCandles(
            long instrumentToken,
            IntervalDescriptor targetDescriptor,
            List<Candle> sourceCandles) {
        if (sourceCandles == null || sourceCandles.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, CandleAccumulator> buckets = new LinkedHashMap<>();
        for (Candle source : sortAndDedupeCandles(sourceCandles)) {
            Instant startInstant = Instant.ofEpochMilli(source.startTimeEpochMs());
            Instant bucketStartInstant = boundaryService.floorToBucketStart(startInstant, targetDescriptor);
            Instant bucketEndInstant = boundaryService.bucketEnd(bucketStartInstant, targetDescriptor);
            long bucketStart = bucketStartInstant.toEpochMilli();
            CandleAccumulator accumulator = buckets.computeIfAbsent(
                    bucketStart,
                    ignored -> new CandleAccumulator(
                            bucketStart,
                            bucketEndInstant.toEpochMilli(),
                            source.open(),
                            source.high(),
                            source.low(),
                            source.close(),
                            source.volume(),
                            source.tickCount(),
                            source.barVersion(),
                            source.cause()));
            accumulator.accumulate(source);
        }

        List<Candle> aggregated = new ArrayList<>(buckets.size());
        for (CandleAccumulator accumulator : buckets.values()) {
            aggregated.add(accumulator.toCandle(instrumentToken, targetDescriptor.kind()));
        }
        aggregated.sort(Comparator.comparingLong(Candle::startTimeEpochMs));
        return aggregated;
    }

    protected void acquireHistoricalPermit() {
        historicalRequestRateLimiter.acquirePermit();
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

    private ZonedDateTime floorToRecentBarStart(ZonedDateTime zdt, IntervalDescriptor descriptor) {
        return boundaryService.floorToBucketStart(zdt, descriptor);
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

    private ZonedDateTime clampToTradableNow(ZonedDateTime now, IntervalDescriptor descriptor) {
        return boundaryService.clampToTradableNow(now, descriptor);
    }

    private ZonedDateTime clampToTradableNow(ZonedDateTime now, IntervalKind intervalKind, Duration barDuration) {
        return clampToTradableNow(now, IntervalDescriptors.of(intervalKind));
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

    private static final class CandleAccumulator {
        private final long startTimeEpochMs;
        private final long endTimeEpochMs;
        private final double open;
        private double high;
        private double low;
        private double close;
        private long volume;
        private long tickCount;
        private int barVersion;
        private CandleCause cause;

        private CandleAccumulator(
                long startTimeEpochMs,
                long endTimeEpochMs,
                double open,
                double high,
                double low,
                double close,
                long volume,
                long tickCount,
                int barVersion,
                CandleCause cause) {
            this.startTimeEpochMs = startTimeEpochMs;
            this.endTimeEpochMs = endTimeEpochMs;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
            this.tickCount = tickCount;
            this.barVersion = barVersion;
            this.cause = cause;
        }

        private void accumulate(Candle source) {
            this.high = Math.max(high, source.high());
            this.low = Math.min(low, source.low());
            this.close = source.close();
            this.volume += source.volume();
            this.tickCount += source.tickCount();
            this.barVersion = Math.max(barVersion, source.barVersion());
            if (source.cause() != null) {
                this.cause = source.cause();
            }
        }

        private Candle toCandle(long instrumentToken, IntervalKind intervalKind) {
            return new Candle(
                    instrumentToken,
                    intervalKind,
                    startTimeEpochMs,
                    endTimeEpochMs,
                    open,
                    high,
                    low,
                    close,
                    volume,
                    tickCount,
                    barVersion,
                    cause);
        }
    }
}
