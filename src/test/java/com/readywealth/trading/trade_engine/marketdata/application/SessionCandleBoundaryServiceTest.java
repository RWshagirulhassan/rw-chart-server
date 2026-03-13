package com.readywealth.trading.trade_engine.marketdata.application;

import com.readywealth.trading.trade_engine.engine.domain.series.IntervalKind;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionCandleBoundaryServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Test
    void floorsIntradayBucketsFromSessionAnchor() {
        SessionCandleBoundaryService service = new SessionCandleBoundaryService("Asia/Kolkata", "09:15", "15:30");

        ZonedDateTime tenMinute = ZonedDateTime.of(2026, 3, 2, 9, 19, 0, 0, IST);
        ZonedDateTime tenFloor = service.floorToBucketStart(tenMinute, Duration.ofMinutes(10), IntervalKind.TIME_10M);
        assertEquals(ZonedDateTime.of(2026, 3, 2, 9, 15, 0, 0, IST), tenFloor);

        ZonedDateTime thirtyMinute = ZonedDateTime.of(2026, 3, 2, 9, 29, 0, 0, IST);
        ZonedDateTime thirtyFloor = service.floorToBucketStart(thirtyMinute, Duration.ofMinutes(30), IntervalKind.TIME_30M);
        assertEquals(ZonedDateTime.of(2026, 3, 2, 9, 15, 0, 0, IST), thirtyFloor);
    }

    @Test
    void clampsTradableNowForIntradayAndDaily() {
        SessionCandleBoundaryService service = new SessionCandleBoundaryService("Asia/Kolkata", "09:15", "15:30");

        ZonedDateTime preOpen = ZonedDateTime.of(2026, 2, 23, 8, 0, 0, 0, IST);
        ZonedDateTime preOpenClamped = service.clampToTradableNow(preOpen, IntervalKind.TIME_10M, Duration.ofMinutes(10));
        assertEquals(ZonedDateTime.of(2026, 2, 20, 15, 30, 0, 0, IST), preOpenClamped);

        ZonedDateTime postClose = ZonedDateTime.of(2026, 2, 23, 15, 45, 0, 0, IST);
        ZonedDateTime postCloseClamped = service.clampToTradableNow(postClose, IntervalKind.TIME_30M, Duration.ofMinutes(30));
        assertEquals(ZonedDateTime.of(2026, 2, 23, 15, 30, 0, 0, IST), postCloseClamped);

        ZonedDateTime dailyPostClose = ZonedDateTime.of(2026, 2, 23, 15, 45, 0, 0, IST);
        ZonedDateTime dailyClamped = service.clampToTradableNow(dailyPostClose, IntervalKind.TIME_1D, Duration.ofDays(1));
        assertEquals(ZonedDateTime.of(2026, 2, 23, 15, 30, 0, 0, IST), dailyClamped);
    }

    @Test
    void computesSessionAwareNextBoundaryAndSessionChecks() {
        SessionCandleBoundaryService service = new SessionCandleBoundaryService("Asia/Kolkata", "09:15", "15:30");

        Instant now = ZonedDateTime.of(2026, 3, 2, 9, 24, 0, 0, IST).toInstant();
        Instant next = service.nextBucketBoundary(now, Duration.ofMinutes(10), IntervalKind.TIME_10M);
        assertEquals(ZonedDateTime.of(2026, 3, 2, 9, 25, 0, 0, IST).toInstant(), next);

        Instant inSession = ZonedDateTime.of(2026, 3, 2, 10, 0, 0, 0, IST).toInstant();
        Instant outOfSession = ZonedDateTime.of(2026, 3, 2, 8, 55, 0, 0, IST).toInstant();
        assertTrue(service.isTradableInstant(inSession, IntervalKind.TIME_10M, Duration.ofMinutes(10)));
        assertFalse(service.isTradableInstant(outOfSession, IntervalKind.TIME_10M, Duration.ofMinutes(10)));
    }
}
