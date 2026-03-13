package com.readywealth.trading.trade_engine.marketdata.application;

import com.readywealth.trading.trade_engine.engine.domain.series.IntervalKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
public class SessionCandleBoundaryService {

    private static final Logger log = LoggerFactory.getLogger(SessionCandleBoundaryService.class);
    private static final LocalTime DEFAULT_MARKET_OPEN = LocalTime.of(9, 15);
    private static final LocalTime DEFAULT_MARKET_CLOSE = LocalTime.of(15, 30);

    private final ZoneId exchangeZoneId;
    private final LocalTime marketOpenTime;
    private final LocalTime marketCloseTime;

    public SessionCandleBoundaryService(
            @Value("${session.candles.zone-id:Asia/Kolkata}") String zoneIdStr,
            @Value("${session.candles.market-open:09:15}") String marketOpenStr,
            @Value("${session.candles.market-close:15:30}") String marketCloseStr) {
        this.exchangeZoneId = ZoneId.of(zoneIdStr);
        LocalTime parsedOpen = parseLocalTimeOrDefault(marketOpenStr, DEFAULT_MARKET_OPEN, "market-open");
        LocalTime parsedClose = parseLocalTimeOrDefault(marketCloseStr, DEFAULT_MARKET_CLOSE, "market-close");
        if (!parsedClose.isAfter(parsedOpen)) {
            log.warn("invalid market session config open={} close={} - falling back to defaults {}-{}",
                    parsedOpen, parsedClose, DEFAULT_MARKET_OPEN, DEFAULT_MARKET_CLOSE);
            parsedOpen = DEFAULT_MARKET_OPEN;
            parsedClose = DEFAULT_MARKET_CLOSE;
        }
        this.marketOpenTime = parsedOpen;
        this.marketCloseTime = parsedClose;
    }

    public ZoneId exchangeZoneId() {
        return exchangeZoneId;
    }

    public LocalTime marketOpenTime() {
        return marketOpenTime;
    }

    public LocalTime marketCloseTime() {
        return marketCloseTime;
    }

    public ZonedDateTime clampToTradableNow(ZonedDateTime now, IntervalKind intervalKind, Duration barDuration) {
        ZonedDateTime candidate = now.withZoneSameInstant(exchangeZoneId).withNano(0);
        if (isWeekend(candidate.toLocalDate())) {
            return ZonedDateTime.of(previousBusinessDay(candidate.toLocalDate()), marketCloseTime, exchangeZoneId);
        }
        if (!isIntradayInterval(intervalKind, barDuration)) {
            if (candidate.toLocalTime().isAfter(marketCloseTime)) {
                return ZonedDateTime.of(candidate.toLocalDate(), marketCloseTime, exchangeZoneId);
            }
            return candidate;
        }
        if (candidate.toLocalTime().isBefore(marketOpenTime)) {
            return ZonedDateTime.of(previousBusinessDay(candidate.toLocalDate()), marketCloseTime, exchangeZoneId);
        }
        if (candidate.toLocalTime().isAfter(marketCloseTime)) {
            return ZonedDateTime.of(candidate.toLocalDate(), marketCloseTime, exchangeZoneId);
        }
        return candidate;
    }

    public ZonedDateTime floorToBucketStart(ZonedDateTime timestamp, Duration barDuration, IntervalKind intervalKind) {
        ZonedDateTime candidate = timestamp.withZoneSameInstant(exchangeZoneId).withSecond(0).withNano(0);
        if (!isIntradayInterval(intervalKind, barDuration)) {
            return floorToWallClockBoundary(candidate, barDuration);
        }
        return floorToSessionBucket(candidate, barDuration);
    }

    public Instant floorToBucketStart(Instant timestamp, Duration barDuration, IntervalKind intervalKind) {
        return floorToBucketStart(timestamp.atZone(exchangeZoneId), barDuration, intervalKind).toInstant();
    }

    public Instant nextBucketBoundary(Instant now, Duration barDuration, IntervalKind intervalKind) {
        ZonedDateTime candidate = now.atZone(exchangeZoneId).withSecond(0).withNano(0);
        if (!isIntradayInterval(intervalKind, barDuration)) {
            return floorToWallClockBoundary(candidate, barDuration).plus(barDuration).toInstant();
        }
        LocalDate date = candidate.toLocalDate();
        if (isWeekend(date)) {
            return ZonedDateTime.of(nextBusinessDay(date), marketOpenTime, exchangeZoneId).toInstant();
        }
        LocalTime time = candidate.toLocalTime();
        if (time.isBefore(marketOpenTime)) {
            return ZonedDateTime.of(date, marketOpenTime, exchangeZoneId).toInstant();
        }
        if (!time.isBefore(marketCloseTime)) {
            return ZonedDateTime.of(nextBusinessDay(date), marketOpenTime, exchangeZoneId).toInstant();
        }
        ZonedDateTime bucketStart = floorToSessionBucket(candidate, barDuration);
        Instant boundary = bucketEnd(bucketStart.toInstant(), barDuration, intervalKind);
        if (!boundary.isAfter(candidate.toInstant())) {
            Instant nextStart = bucketStart.toInstant().plus(barDuration);
            boundary = bucketEnd(nextStart, barDuration, intervalKind);
        }
        if (!boundary.isAfter(candidate.toInstant())) {
            return ZonedDateTime.of(nextBusinessDay(date), marketOpenTime, exchangeZoneId).toInstant();
        }
        return boundary;
    }

    public Instant bucketEnd(Instant bucketStart, Duration barDuration, IntervalKind intervalKind) {
        Instant naturalEnd = bucketStart.plus(barDuration);
        if (!isIntradayInterval(intervalKind, barDuration)) {
            return naturalEnd;
        }
        ZonedDateTime startLocal = bucketStart.atZone(exchangeZoneId);
        Instant sessionClose = ZonedDateTime.of(startLocal.toLocalDate(), marketCloseTime, exchangeZoneId).toInstant();
        if (naturalEnd.isAfter(sessionClose)) {
            return sessionClose;
        }
        return naturalEnd;
    }

    public boolean isTradableInstant(Instant timestamp, IntervalKind intervalKind, Duration barDuration) {
        if (!isIntradayInterval(intervalKind, barDuration)) {
            return true;
        }
        ZonedDateTime local = timestamp.atZone(exchangeZoneId);
        if (isWeekend(local.toLocalDate())) {
            return false;
        }
        LocalTime time = local.toLocalTime();
        return !time.isBefore(marketOpenTime) && !time.isAfter(marketCloseTime);
    }

    public boolean isIntradayInterval(IntervalKind intervalKind, Duration barDuration) {
        return intervalKind != IntervalKind.TIME_1D
                && barDuration != null
                && barDuration.toMinutes() > 0
                && barDuration.toMinutes() < 1440;
    }

    public LocalDate previousBusinessDay(LocalDate date) {
        LocalDate out = date.minusDays(1);
        while (isWeekend(out)) {
            out = out.minusDays(1);
        }
        return out;
    }

    public LocalDate nextBusinessDay(LocalDate date) {
        LocalDate out = date.plusDays(1);
        while (isWeekend(out)) {
            out = out.plusDays(1);
        }
        return out;
    }

    private ZonedDateTime floorToSessionBucket(ZonedDateTime zdt, Duration barDuration) {
        ZonedDateTime sessionStart = ZonedDateTime.of(zdt.toLocalDate(), marketOpenTime, exchangeZoneId);
        ZonedDateTime sessionClose = ZonedDateTime.of(zdt.toLocalDate(), marketCloseTime, exchangeZoneId);
        if (!zdt.isAfter(sessionStart)) {
            return sessionStart;
        }
        ZonedDateTime effective = zdt;
        if (!effective.isBefore(sessionClose)) {
            effective = sessionClose.minusNanos(1);
        }
        long bucketMillis = Math.max(1L, barDuration.toMillis());
        long elapsedMillis = Math.max(0L, Duration.between(sessionStart, effective).toMillis());
        long completedBuckets = elapsedMillis / bucketMillis;
        return sessionStart.plus(Duration.ofMillis(bucketMillis * completedBuckets));
    }

    private ZonedDateTime floorToWallClockBoundary(ZonedDateTime zdt, Duration barDuration) {
        long minutes = barDuration.toMinutes();
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

    private boolean isWeekend(LocalDate d) {
        DayOfWeek dayOfWeek = d.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }

    private LocalTime parseLocalTimeOrDefault(String candidate, LocalTime fallback, String fieldName) {
        if (candidate == null || candidate.isBlank()) {
            return fallback;
        }
        try {
            return LocalTime.parse(candidate.trim());
        } catch (Exception ignored) {
            log.warn("invalid session.candles.{} value='{}' - using fallback {}", fieldName, candidate, fallback);
            return fallback;
        }
    }
}
