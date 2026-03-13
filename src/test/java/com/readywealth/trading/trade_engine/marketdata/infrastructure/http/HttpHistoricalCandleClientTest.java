package com.readywealth.trading.trade_engine.marketdata.infrastructure.http;

import com.readywealth.trading.trade_engine.engine.domain.series.Candle;
import com.readywealth.trading.trade_engine.engine.domain.series.CandleCause;
import com.readywealth.trading.trade_engine.engine.domain.series.IntervalKind;
import com.readywealth.trading.trade_engine.marketdata.application.AuthTokenProvider;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpHistoricalCandleClientTest {

    @Test
    void alignToBusinessDayAdjustsWeekendAndKeepsWeekday() throws Exception {
        HttpHistoricalCandleClient client = newClient();

        Method method = HttpHistoricalCandleClient.class.getDeclaredMethod("alignToBusinessDay", ZonedDateTime.class);
        method.setAccessible(true);

        ZoneId zone = ZoneId.of("Asia/Kolkata");
        ZonedDateTime saturday = ZonedDateTime.of(2026, 2, 21, 10, 7, 11, 0, zone);
        ZonedDateTime sunday = ZonedDateTime.of(2026, 2, 22, 10, 7, 11, 0, zone);
        ZonedDateTime monday = ZonedDateTime.of(2026, 2, 23, 10, 7, 11, 0, zone);

        ZonedDateTime satAligned = (ZonedDateTime) method.invoke(client, saturday);
        ZonedDateTime sunAligned = (ZonedDateTime) method.invoke(client, sunday);
        ZonedDateTime monAligned = (ZonedDateTime) method.invoke(client, monday);

        assertEquals(LocalDate.of(2026, 2, 20), satAligned.toLocalDate());
        assertEquals(LocalDate.of(2026, 2, 20), sunAligned.toLocalDate());
        assertEquals(LocalDate.of(2026, 2, 23), monAligned.toLocalDate());
        assertEquals(10, satAligned.getHour());
        assertEquals(7, satAligned.getMinute());
        assertEquals(11, satAligned.getSecond());
    }

    @Test
    void subtractBusinessDaysInclusiveSkipsWeekend() throws Exception {
        HttpHistoricalCandleClient client = newClient();

        Method method = HttpHistoricalCandleClient.class.getDeclaredMethod("subtractBusinessDaysInclusive", LocalDate.class, int.class);
        method.setAccessible(true);

        LocalDate end = LocalDate.of(2026, 2, 23); // Monday
        LocalDate startFor3 = (LocalDate) method.invoke(client, end, 3);
        LocalDate startFor10 = (LocalDate) method.invoke(client, end, 10);

        assertEquals(LocalDate.of(2026, 2, 19), startFor3); // Thu + Fri + Mon
        assertEquals(LocalDate.of(2026, 2, 10), startFor10);
    }

    @Test
    void lookbackBusinessDaysMappingWorks() throws Exception {
        HttpHistoricalCandleClient client = newClient();

        Method method = HttpHistoricalCandleClient.class.getDeclaredMethod("lookbackBusinessDays", IntervalKind.class);
        method.setAccessible(true);

        assertEquals(3, method.invoke(client, IntervalKind.TIME_1M));
        assertEquals(10, method.invoke(client, IntervalKind.TIME_15M));
        assertEquals(60, method.invoke(client, IntervalKind.TIME_1H));
        assertEquals(600, method.invoke(client, IntervalKind.TIME_1D));
        assertEquals(0, method.invoke(client, IntervalKind.TIME_2H));
    }

    @Test
    void floorToSessionBucketAnchorsTenAndThirtyMinuteBuckets() throws Exception {
        HttpHistoricalCandleClient client = newClient();
        Method method = HttpHistoricalCandleClient.class.getDeclaredMethod(
                "floorToSessionBucket", ZonedDateTime.class, Duration.class, LocalTime.class);
        method.setAccessible(true);

        ZoneId zone = ZoneId.of("Asia/Kolkata");
        ZonedDateTime tenMinuteInput = ZonedDateTime.of(2026, 3, 2, 9, 19, 0, 0, zone);
        ZonedDateTime tenMinuteFloor = (ZonedDateTime) method.invoke(
                client, tenMinuteInput, Duration.ofMinutes(10), LocalTime.of(9, 15));
        assertEquals(ZonedDateTime.of(2026, 3, 2, 9, 15, 0, 0, zone), tenMinuteFloor);

        ZonedDateTime thirtyMinuteInput = ZonedDateTime.of(2026, 3, 2, 9, 29, 0, 0, zone);
        ZonedDateTime thirtyMinuteFloor = (ZonedDateTime) method.invoke(
                client, thirtyMinuteInput, Duration.ofMinutes(30), LocalTime.of(9, 15));
        assertEquals(ZonedDateTime.of(2026, 3, 2, 9, 15, 0, 0, zone), thirtyMinuteFloor);
    }

    @Test
    void clampToTradableNowHandlesPreOpenPostCloseWeekendAndDaily() throws Exception {
        HttpHistoricalCandleClient client = newClient();
        Method method = HttpHistoricalCandleClient.class.getDeclaredMethod(
                "clampToTradableNow", ZonedDateTime.class, IntervalKind.class, Duration.class);
        method.setAccessible(true);

        ZoneId zone = ZoneId.of("Asia/Kolkata");
        ZonedDateTime preOpen = ZonedDateTime.of(2026, 2, 23, 8, 0, 0, 0, zone); // Monday
        ZonedDateTime preOpenClamped = (ZonedDateTime) method.invoke(client, preOpen, IntervalKind.TIME_10M, Duration.ofMinutes(10));
        assertEquals(ZonedDateTime.of(2026, 2, 20, 15, 30, 0, 0, zone), preOpenClamped); // previous Friday close

        ZonedDateTime postClose = ZonedDateTime.of(2026, 2, 23, 15, 38, 4, 0, zone);
        ZonedDateTime postCloseClamped = (ZonedDateTime) method.invoke(client, postClose, IntervalKind.TIME_30M, Duration.ofMinutes(30));
        assertEquals(ZonedDateTime.of(2026, 2, 23, 15, 30, 0, 0, zone), postCloseClamped);

        ZonedDateTime weekend = ZonedDateTime.of(2026, 2, 22, 11, 0, 0, 0, zone); // Sunday
        ZonedDateTime weekendClamped = (ZonedDateTime) method.invoke(client, weekend, IntervalKind.TIME_10M, Duration.ofMinutes(10));
        assertEquals(ZonedDateTime.of(2026, 2, 20, 15, 30, 0, 0, zone), weekendClamped);

        ZonedDateTime dailyPostClose = ZonedDateTime.of(2026, 2, 23, 15, 38, 4, 0, zone);
        ZonedDateTime dailyClamped = (ZonedDateTime) method.invoke(client, dailyPostClose, IntervalKind.TIME_1D, Duration.ofDays(1));
        assertEquals(ZonedDateTime.of(2026, 2, 23, 15, 30, 0, 0, zone), dailyClamped);
    }

    @Test
    void fetchRecentCandlesMergesFullAndLiveAndDeduplicatesBarStart() {
        StubHistoricalClient client = newStubClient();
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        ZonedDateTime now = ZonedDateTime.of(2026, 2, 23, 10, 7, 11, 0, zone);

        long fullStart = ZonedDateTime.of(2026, 2, 23, 10, 0, 0, 0, zone).toInstant().toEpochMilli();
        long fullEnd = ZonedDateTime.of(2026, 2, 23, 10, 5, 0, 0, zone).toInstant().toEpochMilli();
        long duplicateStart = ZonedDateTime.of(2026, 2, 23, 10, 5, 0, 0, zone).toInstant().toEpochMilli();
        long duplicateEnd = ZonedDateTime.of(2026, 2, 23, 10, 10, 0, 0, zone).toInstant().toEpochMilli();

        client.responses.add(List.of(
                candle(115626503L, IntervalKind.TIME_5M, fullStart, fullEnd, 100.0),
                candle(115626503L, IntervalKind.TIME_5M, duplicateStart, duplicateEnd, 101.0)
        ));
        client.responses.add(List.of(
                candle(115626503L, IntervalKind.TIME_5M, duplicateStart, duplicateEnd, 222.0)
        ));

        List<Candle> out = client.fetchRecentCandlesAt(115626503L, IntervalKind.TIME_5M, now);

        assertEquals(2, out.size());
        assertEquals(fullStart, out.get(0).startTimeEpochMs());
        assertEquals(duplicateStart, out.get(1).startTimeEpochMs());
        assertEquals(now.toInstant().toEpochMilli(), out.get(1).endTimeEpochMs());
        assertEquals(222.0, out.get(1).close());
    }

    @Test
    void fetchRecentCandlesSkipsLiveCallWhenNowOnBoundary() {
        StubHistoricalClient client = newStubClient();
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        ZonedDateTime now = ZonedDateTime.of(2026, 2, 23, 10, 10, 0, 0, zone); // exact 5m boundary

        long start = ZonedDateTime.of(2026, 2, 23, 10, 5, 0, 0, zone).toInstant().toEpochMilli();
        long end = ZonedDateTime.of(2026, 2, 23, 10, 10, 0, 0, zone).toInstant().toEpochMilli();
        client.responses.add(List.of(candle(115626503L, IntervalKind.TIME_5M, start, end, 101.0)));

        List<Candle> out = client.fetchRecentCandlesAt(115626503L, IntervalKind.TIME_5M, now);

        assertEquals(1, out.size());
        assertEquals(1, client.calls.size());
    }

    @Test
    void fetchRecentCandlesUsesSessionAnchoredBarStartForTenMinute() {
        StubHistoricalClient client = newStubClient();
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        ZonedDateTime now = ZonedDateTime.of(2026, 2, 23, 9, 19, 0, 0, zone);

        long liveStart = ZonedDateTime.of(2026, 2, 23, 9, 15, 0, 0, zone).toInstant().toEpochMilli();
        long liveEnd = ZonedDateTime.of(2026, 2, 23, 9, 25, 0, 0, zone).toInstant().toEpochMilli();
        client.responses.add(List.of()); // full window
        client.responses.add(List.of(candle(115626503L, IntervalKind.TIME_10M, liveStart, liveEnd, 105.0))); // live window

        List<Candle> out = client.fetchRecentCandlesAt(115626503L, IntervalKind.TIME_10M, now);

        assertEquals(1, out.size());
        assertEquals(liveStart, out.get(0).startTimeEpochMs());
        assertEquals(now.toInstant().toEpochMilli(), out.get(0).endTimeEpochMs());
        assertEquals(2, client.calls.size());
        assertTrue(client.calls.get(0).contains("T09:15"));
        assertTrue(client.calls.get(1).contains("T09:15"));
    }

    @Test
    void fetchRecentCandlesFiltersWeekendCandles() {
        StubHistoricalClient client = newStubClient();
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        ZonedDateTime now = ZonedDateTime.of(2026, 2, 23, 10, 7, 11, 0, zone);

        long weekendStart = ZonedDateTime.of(2026, 2, 22, 10, 0, 0, 0, zone).toInstant().toEpochMilli(); // Sunday
        long weekendEnd = ZonedDateTime.of(2026, 2, 22, 10, 5, 0, 0, zone).toInstant().toEpochMilli();
        client.responses.add(List.of(candle(115626503L, IntervalKind.TIME_5M, weekendStart, weekendEnd, 99.0)));
        client.responses.add(List.of());

        List<Candle> out = client.fetchRecentCandlesAt(115626503L, IntervalKind.TIME_5M, now);

        assertTrue(out.isEmpty());
    }

    @Test
    void fetchCandlesInRangePreservesVendorTimestampWithoutFlooring() {
        ReplayHistoricalClient client = newReplayClient();
        HttpHistoricalCandleClient.KiteHistoricalResponse body = new HttpHistoricalCandleClient.KiteHistoricalResponse();
        HttpHistoricalCandleClient.KiteHistoricalResponse.Data data = new HttpHistoricalCandleClient.KiteHistoricalResponse.Data();
        data.candles = List.of(List.of(
                "2026-03-02T09:15:00+0530",
                1291.0,
                1298.8,
                1282.0,
                1294.4,
                1034498L));
        body.data = data;
        client.nextBody = body;

        ZoneId zone = ZoneId.of("Asia/Kolkata");
        Instant from = ZonedDateTime.of(2026, 3, 2, 9, 0, 0, 0, zone).toInstant();
        Instant to = ZonedDateTime.of(2026, 3, 2, 10, 0, 0, 0, zone).toInstant();

        List<Candle> out = client.fetchCandlesInRange(408065L, IntervalKind.TIME_30M, from, to);

        assertEquals(1, out.size());
        long expectedStart = ZonedDateTime.of(2026, 3, 2, 9, 15, 0, 0, zone).toInstant().toEpochMilli();
        assertEquals(expectedStart, out.get(0).startTimeEpochMs());
    }

    @Test
    void fetchRecentBootstrapThrowsTransientErrorFor429() throws Exception {
        ThrottledHistoricalClient client = new ThrottledHistoricalClient(newTokenProvider());
        Method method = HttpHistoricalCandleClient.class.getDeclaredMethod(
                "fetchRecentCandlesAt",
                long.class,
                IntervalKind.class,
                ZonedDateTime.class,
                boolean.class);
        method.setAccessible(true);

        ZoneId zone = ZoneId.of("Asia/Kolkata");
        ZonedDateTime now = ZonedDateTime.of(2026, 3, 2, 10, 7, 11, 0, zone);

        try {
            method.invoke(client, 115626503L, IntervalKind.TIME_5M, now, true);
        } catch (InvocationTargetException invocationTargetException) {
            Throwable cause = invocationTargetException.getCause();
            HistoricalFetchException exception = assertInstanceOf(HistoricalFetchException.class, cause);
            assertTrue(exception.isTransientFailure());
            return;
        }
        throw new AssertionError("Expected HistoricalFetchException for 429 response");
    }

    @Test
    void fetchRecentBootstrapThrowsNonTransientErrorForInvalidPayload() throws Exception {
        InvalidPayloadHistoricalClient client = new InvalidPayloadHistoricalClient(newTokenProvider());
        Method method = HttpHistoricalCandleClient.class.getDeclaredMethod(
                "fetchRecentCandlesAt",
                long.class,
                IntervalKind.class,
                ZonedDateTime.class,
                boolean.class);
        method.setAccessible(true);

        ZoneId zone = ZoneId.of("Asia/Kolkata");
        ZonedDateTime now = ZonedDateTime.of(2026, 3, 2, 10, 7, 11, 0, zone);

        try {
            method.invoke(client, 115626503L, IntervalKind.TIME_5M, now, true);
        } catch (InvocationTargetException invocationTargetException) {
            Throwable cause = invocationTargetException.getCause();
            HistoricalFetchException exception = assertInstanceOf(HistoricalFetchException.class, cause);
            assertTrue(!exception.isTransientFailure());
            return;
        }
        throw new AssertionError("Expected HistoricalFetchException for invalid payload");
    }

    private static HttpHistoricalCandleClient newClient() {
        AuthTokenProvider authTokenProvider = newTokenProvider();
        return new HttpHistoricalCandleClient(
                authTokenProvider,
                "https://api.kite.trade",
                "api-key",
                "Asia/Kolkata",
                "09:15",
                "15:30");
    }

    private static StubHistoricalClient newStubClient() {
        AuthTokenProvider authTokenProvider = newTokenProvider();
        return new StubHistoricalClient(authTokenProvider);
    }

    private static ReplayHistoricalClient newReplayClient() {
        AuthTokenProvider authTokenProvider = newTokenProvider();
        return new ReplayHistoricalClient(authTokenProvider);
    }

    private static AuthTokenProvider newTokenProvider() {
        AuthTokenProvider authTokenProvider = Mockito.mock(AuthTokenProvider.class);
        Mockito.when(authTokenProvider.getValidAccessToken()).thenReturn(Optional.of("token"));
        return authTokenProvider;
    }

    private static Candle candle(long instrumentToken, IntervalKind intervalKind, long start, long end, double close) {
        return new Candle(instrumentToken, intervalKind, start, end, close - 1, close + 1, close - 2, close, 100, 0L, 1, CandleCause.BACKFILL);
    }

    private static class StubHistoricalClient extends HttpHistoricalCandleClient {
        private final List<List<Candle>> responses = new ArrayList<>();
        private final List<String> calls = new ArrayList<>();

        private StubHistoricalClient(AuthTokenProvider authTokenProvider) {
            super(authTokenProvider, "https://api.kite.trade", "api-key", "Asia/Kolkata", "09:15", "15:30");
        }

        @Override
        protected List<Candle> fetchHistorical(long instrumentToken, IntervalKind intervalKind, String accessToken, ZonedDateTime from, ZonedDateTime to) {
            calls.add(from + "->" + to);
            if (responses.isEmpty()) {
                return List.of();
            }
            return responses.remove(0);
        }
    }

    private static class ReplayHistoricalClient extends HttpHistoricalCandleClient {
        private HttpHistoricalCandleClient.KiteHistoricalResponse nextBody;

        private ReplayHistoricalClient(AuthTokenProvider authTokenProvider) {
            super(authTokenProvider, "https://api.kite.trade", "api-key", "Asia/Kolkata", "09:15", "15:30");
        }

        @Override
        protected ResponseEntity<KiteHistoricalResponse> executeHistoricalRequest(RequestEntity<Void> request) {
            return ResponseEntity.ok(nextBody);
        }
    }

    private static class ThrottledHistoricalClient extends HttpHistoricalCandleClient {
        private ThrottledHistoricalClient(AuthTokenProvider authTokenProvider) {
            super(authTokenProvider, "https://api.kite.trade", "api-key", "Asia/Kolkata", "09:15", "15:30");
        }

        @Override
        protected ResponseEntity<KiteHistoricalResponse> executeHistoricalRequest(RequestEntity<Void> request) {
            throw new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    private static class InvalidPayloadHistoricalClient extends HttpHistoricalCandleClient {
        private InvalidPayloadHistoricalClient(AuthTokenProvider authTokenProvider) {
            super(authTokenProvider, "https://api.kite.trade", "api-key", "Asia/Kolkata", "09:15", "15:30");
        }

        @Override
        protected ResponseEntity<KiteHistoricalResponse> executeHistoricalRequest(RequestEntity<Void> request) {
            return ResponseEntity.ok(new KiteHistoricalResponse());
        }
    }
}
