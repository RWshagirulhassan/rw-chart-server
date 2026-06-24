package com.readywealth.trading.trade_engine.marketdata.application;

import com.readywealth.trading.trade_engine.engine.domain.series.IntervalKind;
import com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey;
import com.readywealth.trading.trade_engine.session.infrastructure.memory.InMemoryConcurrentBarSeriesRegistry;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionAlignedSeriesIngestorTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Test
    void replacesWithinBucketAndAppendsOnBoundary() {
        InMemoryConcurrentBarSeriesRegistry registry = new InMemoryConcurrentBarSeriesRegistry();
        SeriesKey key = new SeriesKey(408065L, IntervalKind.TIME_10M);
        InMemoryConcurrentBarSeriesRegistry.SeriesEntry entry = registry.acquire(key, 2000).entry();
        SessionAlignedSeriesIngestor ingestor = new SessionAlignedSeriesIngestor("Asia/Kolkata", "09:15", "15:30");

        SessionAlignedSeriesIngestor.IngestResult first = ingestor.ingest(entry, ts(2026, 3, 2, 9, 16), 100.0, 5.0);
        SessionAlignedSeriesIngestor.IngestResult second = ingestor.ingest(entry, ts(2026, 3, 2, 9, 24), 110.0, 2.0);
        assertEquals(SessionAlignedSeriesIngestor.IngestResult.APPENDED, first);
        assertEquals(SessionAlignedSeriesIngestor.IngestResult.REPLACED, second);

        var series = entry.series();
        assertEquals(0, series.getBeginIndex());
        assertEquals(0, series.getEndIndex());
        var firstBar = series.getBar(series.getEndIndex());
        assertEquals(ts(2026, 3, 2, 9, 15), firstBar.getBeginTime().toEpochMilli());
        assertEquals(ts(2026, 3, 2, 9, 25), firstBar.getEndTime().toEpochMilli());
        assertEquals("100.0", firstBar.getOpenPrice().toString());
        assertEquals("110.0", firstBar.getHighPrice().toString());
        assertEquals("100.0", firstBar.getLowPrice().toString());
        assertEquals("110.0", firstBar.getClosePrice().toString());
        assertEquals("7.0", firstBar.getVolume().toString());
        assertEquals(2L, firstBar.getTrades());

        SessionAlignedSeriesIngestor.IngestResult third = ingestor.ingest(entry, ts(2026, 3, 2, 9, 25), 105.0, 1.0);
        assertEquals(SessionAlignedSeriesIngestor.IngestResult.APPENDED, third);
        assertEquals(1, series.getEndIndex());
        var secondBar = series.getBar(series.getEndIndex());
        assertEquals(ts(2026, 3, 2, 9, 25), secondBar.getBeginTime().toEpochMilli());
    }

    @Test
    void dropsOutOfOrderAndOutOfSessionTicks() {
        InMemoryConcurrentBarSeriesRegistry registry = new InMemoryConcurrentBarSeriesRegistry();
        SeriesKey key = new SeriesKey(408065L, IntervalKind.TIME_10M);
        InMemoryConcurrentBarSeriesRegistry.SeriesEntry entry = registry.acquire(key, 2000).entry();
        SessionAlignedSeriesIngestor ingestor = new SessionAlignedSeriesIngestor("Asia/Kolkata", "09:15", "15:30");

        SessionAlignedSeriesIngestor.IngestResult outOfSession = ingestor.ingest(entry, ts(2026, 3, 2, 8, 55), 99.0, 1.0);
        assertEquals(SessionAlignedSeriesIngestor.IngestResult.DROPPED_OUT_OF_SESSION, outOfSession);
        assertTrue(entry.series().isEmpty());

        ingestor.ingest(entry, ts(2026, 3, 2, 9, 26), 101.0, 1.0);
        SessionAlignedSeriesIngestor.IngestResult outOfOrder = ingestor.ingest(entry, ts(2026, 3, 2, 9, 24), 102.0, 1.0);
        assertEquals(SessionAlignedSeriesIngestor.IngestResult.DROPPED_OUT_OF_ORDER_BUCKET, outOfOrder);
        assertEquals(0, entry.series().getEndIndex());
    }

    @Test
    void alignsDailyBarsToExchangeDayBoundary() {
        InMemoryConcurrentBarSeriesRegistry registry = new InMemoryConcurrentBarSeriesRegistry();
        SeriesKey key = new SeriesKey(256265L, IntervalKind.TIME_1D);
        InMemoryConcurrentBarSeriesRegistry.SeriesEntry entry = registry.acquire(key, 2000).entry();
        SessionAlignedSeriesIngestor ingestor = new SessionAlignedSeriesIngestor("Asia/Kolkata", "09:15", "15:30");

        SessionAlignedSeriesIngestor.IngestResult first = ingestor.ingest(entry, ts(2026, 3, 2, 10, 0), 100.0, 1.0);
        SessionAlignedSeriesIngestor.IngestResult second = ingestor.ingest(entry, ts(2026, 3, 2, 14, 0), 120.0, 2.0);

        assertEquals(SessionAlignedSeriesIngestor.IngestResult.APPENDED, first);
        assertEquals(SessionAlignedSeriesIngestor.IngestResult.REPLACED, second);

        var series = entry.series();
        assertEquals(0, series.getEndIndex());
        var dayOne = series.getBar(series.getEndIndex());
        assertEquals(ts(2026, 3, 2, 0, 0), dayOne.getBeginTime().toEpochMilli());
        assertEquals(ts(2026, 3, 3, 0, 0), dayOne.getEndTime().toEpochMilli());
        assertEquals("100.0", dayOne.getOpenPrice().toString());
        assertEquals("120.0", dayOne.getHighPrice().toString());
        assertEquals("100.0", dayOne.getLowPrice().toString());
        assertEquals("120.0", dayOne.getClosePrice().toString());

        SessionAlignedSeriesIngestor.IngestResult third = ingestor.ingest(entry, ts(2026, 3, 3, 9, 20), 111.0, 1.0);
        assertEquals(SessionAlignedSeriesIngestor.IngestResult.APPENDED, third);
        assertEquals(1, series.getEndIndex());
        assertEquals(ts(2026, 3, 3, 0, 0), series.getBar(1).getBeginTime().toEpochMilli());
    }

    @Test
    void alignsWeeklyAndMonthlyBarsToCalendarBoundaries() {
        InMemoryConcurrentBarSeriesRegistry registry = new InMemoryConcurrentBarSeriesRegistry();
        SessionAlignedSeriesIngestor ingestor = new SessionAlignedSeriesIngestor("Asia/Kolkata", "09:15", "15:30");

        InMemoryConcurrentBarSeriesRegistry.SeriesEntry weeklyEntry = registry
                .acquire(new SeriesKey(256265L, IntervalKind.TIME_1W), 2000)
                .entry();
        assertEquals(SessionAlignedSeriesIngestor.IngestResult.APPENDED, ingestor.ingest(weeklyEntry, ts(2026, 3, 4, 10, 0), 100.0, 1.0));
        assertEquals(SessionAlignedSeriesIngestor.IngestResult.REPLACED, ingestor.ingest(weeklyEntry, ts(2026, 3, 6, 14, 0), 120.0, 2.0));
        var weeklySeries = weeklyEntry.series();
        assertEquals(ts(2026, 3, 2, 0, 0), weeklySeries.getBar(0).getBeginTime().toEpochMilli());
        assertEquals(ts(2026, 3, 9, 0, 0), weeklySeries.getBar(0).getEndTime().toEpochMilli());
        assertEquals("100.0", weeklySeries.getBar(0).getOpenPrice().toString());
        assertEquals("120.0", weeklySeries.getBar(0).getClosePrice().toString());

        InMemoryConcurrentBarSeriesRegistry.SeriesEntry monthlyEntry = registry
                .acquire(new SeriesKey(256265L, IntervalKind.TIME_1MO), 2000)
                .entry();
        assertEquals(SessionAlignedSeriesIngestor.IngestResult.APPENDED, ingestor.ingest(monthlyEntry, ts(2026, 3, 18, 10, 0), 200.0, 1.0));
        assertEquals(SessionAlignedSeriesIngestor.IngestResult.REPLACED, ingestor.ingest(monthlyEntry, ts(2026, 3, 31, 15, 0), 210.0, 3.0));
        var monthlySeries = monthlyEntry.series();
        assertEquals(ts(2026, 3, 1, 0, 0), monthlySeries.getBar(0).getBeginTime().toEpochMilli());
        assertEquals(ts(2026, 4, 1, 0, 0), monthlySeries.getBar(0).getEndTime().toEpochMilli());
        assertEquals("200.0", monthlySeries.getBar(0).getOpenPrice().toString());
        assertEquals("210.0", monthlySeries.getBar(0).getClosePrice().toString());
    }

    private long ts(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, IST).toInstant().toEpochMilli();
    }
}
