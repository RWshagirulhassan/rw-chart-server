package com.readywealth.trading.trade_engine.marketdata.application;

import com.readywealth.trading.trade_engine.engine.domain.series.IntervalDescriptor;
import com.readywealth.trading.trade_engine.engine.domain.series.IntervalKind;
import com.readywealth.trading.trade_engine.engine.domain.series.IntervalDescriptors;
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
        return clampToTradableNow(now, IntervalDescriptors.of(intervalKind));
    }

    public ZonedDateTime clampToTradableNow(ZonedDateTime now, IntervalDescriptor descriptor) {
        ZonedDateTime candidate = now.withZoneSameInstant(exchangeZoneId).withNano(0);
        if (isWeekend(candidate.toLocalDate())) {
            return ZonedDateTime.of(previousBusinessDay(candidate.toLocalDate()), marketCloseTime, exchangeZoneId);
        }
        if (!descriptor.isIntraday()) {
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
        return floorToBucketStart(timestamp, IntervalDescriptors.of(intervalKind));
    }

    public ZonedDateTime floorToBucketStart(ZonedDateTime timestamp, IntervalDescriptor descriptor) {
        ZonedDateTime candidate = timestamp.withZoneSameInstant(exchangeZoneId).withSecond(0).withNano(0);
        if (descriptor.isIntraday()) {
            return floorToSessionBucket(candidate, descriptor.fixedDurationOrThrow());
        }
        return floorToCalendarBoundary(candidate, descriptor);
    }

    public Instant floorToBucketStart(Instant timestamp, Duration barDuration, IntervalKind intervalKind) {
        return floorToBucketStart(timestamp.atZone(exchangeZoneId), IntervalDescriptors.of(intervalKind)).toInstant();
    }

    public Instant floorToBucketStart(Instant timestamp, IntervalKind intervalKind) {
        return floorToBucketStart(timestamp.atZone(exchangeZoneId), IntervalDescriptors.of(intervalKind)).toInstant();
    }

    public Instant floorToBucketStart(Instant timestamp, IntervalDescriptor descriptor) {
        return floorToBucketStart(timestamp.atZone(exchangeZoneId), descriptor).toInstant();
    }

    public Instant nextBucketBoundary(Instant now, Duration barDuration, IntervalKind intervalKind) {
        return nextBucketBoundary(now, IntervalDescriptors.of(intervalKind));
    }

    public Instant nextBucketBoundary(Instant now, IntervalDescriptor descriptor) {
        ZonedDateTime candidate = now.atZone(exchangeZoneId).withSecond(0).withNano(0);
        if (!descriptor.isIntraday()) {
            return advanceCalendarBucket(floorToCalendarBoundary(candidate, descriptor), descriptor).toInstant();
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
        Duration barDuration = descriptor.fixedDurationOrThrow();
        ZonedDateTime bucketStart = floorToSessionBucket(candidate, barDuration);
        Instant boundary = bucketEnd(bucketStart.toInstant(), descriptor);
        if (!boundary.isAfter(candidate.toInstant())) {
            Instant nextStart = bucketStart.toInstant().plus(barDuration);
            boundary = bucketEnd(nextStart, descriptor);
        }
        if (!boundary.isAfter(candidate.toInstant())) {
            return ZonedDateTime.of(nextBusinessDay(date), marketOpenTime, exchangeZoneId).toInstant();
        }
        return boundary;
    }

    public Instant bucketEnd(Instant bucketStart, Duration barDuration, IntervalKind intervalKind) {
        return bucketEnd(bucketStart, IntervalDescriptors.of(intervalKind));
    }

    public Instant bucketEnd(Instant bucketStart, IntervalDescriptor descriptor) {
        ZonedDateTime startLocal = bucketStart.atZone(exchangeZoneId);
        if (!descriptor.isIntraday()) {
            return advanceCalendarBucket(startLocal, descriptor).toInstant();
        }
        Instant naturalEnd = bucketStart.plus(descriptor.fixedDurationOrThrow());
        Instant sessionClose = ZonedDateTime.of(startLocal.toLocalDate(), marketCloseTime, exchangeZoneId).toInstant();
        return naturalEnd.isAfter(sessionClose) ? sessionClose : naturalEnd;
    }

    public boolean isTradableInstant(Instant timestamp, IntervalKind intervalKind, Duration barDuration) {
        return isTradableInstant(timestamp, IntervalDescriptors.of(intervalKind));
    }

    public boolean isTradableInstant(Instant timestamp, IntervalDescriptor descriptor) {
        if (!descriptor.isIntraday()) {
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
        return IntervalDescriptors.of(intervalKind).isIntraday();
    }

    public boolean isIntradayInterval(IntervalDescriptor descriptor) {
        return descriptor.isIntraday();
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

    private ZonedDateTime floorToCalendarBoundary(ZonedDateTime zdt, IntervalDescriptor descriptor) {
        return switch (descriptor.kind()) {
            case TIME_1D -> ZonedDateTime.of(zdt.toLocalDate(), LocalTime.MIDNIGHT, exchangeZoneId).withSecond(0);
            case TIME_1W -> {
                LocalDate date = zdt.toLocalDate();
                while (date.getDayOfWeek() != DayOfWeek.MONDAY) {
                    date = date.minusDays(1);
                }
                yield ZonedDateTime.of(date, LocalTime.MIDNIGHT, exchangeZoneId).withSecond(0);
            }
            case TIME_1MO -> ZonedDateTime.of(
                    zdt.getYear(),
                    zdt.getMonthValue(),
                    1,
                    0,
                    0,
                    0,
                    0,
                    exchangeZoneId).withSecond(0);
            default -> throw new IllegalArgumentException("Unsupported calendar interval: " + descriptor.kind());
        };
    }

    private ZonedDateTime advanceCalendarBucket(ZonedDateTime start, IntervalDescriptor descriptor) {
        return switch (descriptor.kind()) {
            case TIME_1D -> start.plusDays(1);
            case TIME_1W -> start.plusWeeks(1);
            case TIME_1MO -> start.plusMonths(1);
            default -> throw new IllegalArgumentException("Unsupported calendar interval: " + descriptor.kind());
        };
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
