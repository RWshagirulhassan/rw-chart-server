package com.readywealth.trading.trade_engine.marketdata.application;

import com.readywealth.trading.trade_engine.engine.domain.series.IntervalKind;
import com.readywealth.trading.trade_engine.session.infrastructure.memory.InMemoryConcurrentBarSeriesRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.ConcurrentBarSeries;
import org.ta4j.core.num.Num;

import java.time.Duration;
import java.time.Instant;

@Component
public class SessionAlignedSeriesIngestor {

    public enum IngestResult {
        APPENDED,
        REPLACED,
        DROPPED_OUT_OF_SESSION,
        DROPPED_OUT_OF_ORDER_BUCKET
    }

    private final SessionCandleBoundaryService boundaryService;

    @Autowired
    public SessionAlignedSeriesIngestor(SessionCandleBoundaryService boundaryService) {
        this.boundaryService = boundaryService;
    }

    SessionAlignedSeriesIngestor(String zoneId, String marketOpen, String marketClose) {
        this(new SessionCandleBoundaryService(zoneId, marketOpen, marketClose));
    }

    public IngestResult ingest(InMemoryConcurrentBarSeriesRegistry.SeriesEntry entry, long timestampMs, double price, double volume) {
        IntervalKind intervalKind = entry.seriesKey().timeframe();
        Duration barDuration = entry.timePeriod();
        Instant timestamp = Instant.ofEpochMilli(timestampMs);
        ConcurrentBarSeries series = entry.series();
        Num volumeNum = series.numFactory().numOf(Math.max(0d, volume));
        Num priceNum = series.numFactory().numOf(price);

        boolean intraday = boundaryService.isIntradayInterval(intervalKind, barDuration);
        if (intraday && !boundaryService.isTradableInstant(timestamp, intervalKind, barDuration)) {
            return IngestResult.DROPPED_OUT_OF_SESSION;
        }
        return series.withWriteLock(() ->
                ingestBucketAlignedLocked(series, intervalKind, barDuration, timestamp, priceNum, volumeNum));
    }

    private IngestResult ingestBucketAlignedLocked(
            ConcurrentBarSeries series,
            IntervalKind intervalKind,
            Duration barDuration,
            Instant timestamp,
            Num priceNum,
            Num volumeNum) {
        Instant bucketStart = boundaryService.floorToBucketStart(timestamp, barDuration, intervalKind);
        Instant bucketEnd = boundaryService.bucketEnd(bucketStart, barDuration, intervalKind);
        if (!bucketEnd.isAfter(bucketStart)) {
            return IngestResult.DROPPED_OUT_OF_SESSION;
        }

        int endIndex = series.getEndIndex();
        if (!series.isEmpty() && endIndex >= series.getBeginIndex()) {
            Bar lastBar = series.getBar(endIndex);
            Instant lastStart = lastBar.getBeginTime();
            if (bucketStart.isBefore(lastStart)) {
                return IngestResult.DROPPED_OUT_OF_ORDER_BUCKET;
            }
            if (bucketStart.equals(lastStart)) {
                Bar replaced = buildReplacementBar(series, lastBar, bucketStart, bucketEnd, priceNum, volumeNum);
                series.ingestStreamingBar(replaced);
                return IngestResult.REPLACED;
            }
        }

        Bar appended = buildAppendedBar(series, bucketStart, bucketEnd, priceNum, volumeNum);
        series.ingestStreamingBar(appended);
        return IngestResult.APPENDED;
    }

    private Bar buildReplacementBar(
            ConcurrentBarSeries series,
            Bar lastBar,
            Instant bucketStart,
            Instant bucketEnd,
            Num priceNum,
            Num volumeNum) {
        Num open = lastBar.getOpenPrice();
        Num high = lastBar.getHighPrice().max(priceNum);
        Num low = lastBar.getLowPrice().min(priceNum);
        Num close = priceNum;
        Num nextVolume = lastBar.getVolume().plus(volumeNum);
        Num nextAmount = lastBar.getAmount().plus(priceNum.multipliedBy(volumeNum));
        long nextTrades = Math.max(1L, lastBar.getTrades() + 1L);
        return series.barBuilder()
                .timePeriod(duration(bucketStart, bucketEnd))
                .beginTime(bucketStart)
                .endTime(bucketEnd)
                .openPrice(open)
                .highPrice(high)
                .lowPrice(low)
                .closePrice(close)
                .volume(nextVolume)
                .amount(nextAmount)
                .trades(nextTrades)
                .build();
    }

    private Bar buildAppendedBar(
            ConcurrentBarSeries series,
            Instant bucketStart,
            Instant bucketEnd,
            Num priceNum,
            Num volumeNum) {
        Num amount = priceNum.multipliedBy(volumeNum);
        return series.barBuilder()
                .timePeriod(duration(bucketStart, bucketEnd))
                .beginTime(bucketStart)
                .endTime(bucketEnd)
                .openPrice(priceNum)
                .highPrice(priceNum)
                .lowPrice(priceNum)
                .closePrice(priceNum)
                .volume(volumeNum)
                .amount(amount)
                .trades(1L)
                .build();
    }

    private Duration duration(Instant start, Instant end) {
        long millis = Math.max(1L, Duration.between(start, end).toMillis());
        return Duration.ofMillis(millis);
    }
}
