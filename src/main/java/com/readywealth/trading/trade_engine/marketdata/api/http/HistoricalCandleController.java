package com.readywealth.trading.trade_engine.marketdata.api.http;

import com.readywealth.trading.trade_engine.engine.domain.series.Candle;
import com.readywealth.trading.trade_engine.engine.domain.series.IntervalKind;
import com.readywealth.trading.trade_engine.marketdata.api.http.dto.ClientCandleView;
import com.readywealth.trading.trade_engine.marketdata.api.http.dto.ClientHistoricalCandlesResponse;
import com.readywealth.trading.trade_engine.marketdata.application.port.HistoricalCandleClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/marketdata/historical")
public class HistoricalCandleController {
    private static final Logger log = LoggerFactory.getLogger(HistoricalCandleController.class);
    private static final ZoneId CLIENT_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter CLIENT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final HistoricalCandleClient historicalCandleClient;

    public HistoricalCandleController(HistoricalCandleClient historicalCandleClient) {
        this.historicalCandleClient = historicalCandleClient;
    }

    @GetMapping("/candles")
    public ClientHistoricalCandlesResponse candles(
            @RequestParam long instrumentToken,
            @RequestParam IntervalKind intervalKind,
            @RequestParam String from,
            @RequestParam String to) {
        log.info("historical_api_hit instrumentToken={} intervalKind={} from={} to={}",
                instrumentToken, intervalKind, from, to);
        ZonedDateTime fromClient = parseClientDateTime(from);
        ZonedDateTime toClient = parseClientDateTime(to);
        Instant fromInstant = fromClient.toInstant();
        Instant toInstant = toClient.toInstant();
        List<Candle> candles = historicalCandleClient.fetchCandlesInRange(
                instrumentToken,
                intervalKind,
                fromInstant,
                toInstant);
        log.info("historical_api_result instrumentToken={} intervalKind={} count={} from={} to={}",
                instrumentToken, intervalKind, candles.size(), fromInstant, toInstant);
        return toClientResponse(
                instrumentToken,
                intervalKind,
                candles,
                formatForClient(fromInstant.toEpochMilli()),
                formatForClient(toInstant.toEpochMilli()));
    }

    @GetMapping("/recent")
    public ClientHistoricalCandlesResponse recent(
            @RequestParam long instrumentToken,
            @RequestParam IntervalKind intervalKind) {
        log.info("historical_recent_api_hit instrumentToken={} intervalKind={}",
                instrumentToken, intervalKind);
        List<Candle> candles = historicalCandleClient.fetchRecentCandles(instrumentToken, intervalKind);
        log.info("historical_recent_api_result instrumentToken={} intervalKind={} count={}",
                instrumentToken, intervalKind, candles.size());
        return toClientResponse(instrumentToken, intervalKind, candles, null, null);
    }

    private ClientHistoricalCandlesResponse toClientResponse(
            long instrumentToken,
            IntervalKind intervalKind,
            List<Candle> candles,
            String requestedFrom,
            String requestedTo) {
        List<Candle> sorted = candles.stream()
                .sorted(Comparator.comparingLong(Candle::startTimeEpochMs))
                .toList();
        List<ClientCandleView> views = sorted.stream()
                .map(this::toClientCandle)
                .toList();
        String from = requestedFrom != null ? requestedFrom : (views.isEmpty() ? null : views.getFirst().startTime());
        String to = requestedTo != null ? requestedTo : (views.isEmpty() ? null : views.getLast().endTime());
        return new ClientHistoricalCandlesResponse(
                instrumentToken,
                intervalKind.name(),
                from,
                to,
                views.size(),
                views);
    }

    private ClientCandleView toClientCandle(Candle candle) {
        return new ClientCandleView(
                formatForClient(candle.startTimeEpochMs()),
                formatForClient(candle.endTimeEpochMs()),
                candle.open(),
                candle.high(),
                candle.low(),
                candle.close(),
                candle.volume(),
                candle.tickCount(),
                candle.barVersion(),
                candle.cause() == null ? null : candle.cause().name());
    }

    private String formatForClient(long epochMs) {
        return Instant.ofEpochMilli(epochMs).atZone(CLIENT_ZONE).withNano(0).format(CLIENT_TIME_FORMAT);
    }

    private ZonedDateTime parseClientDateTime(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("from/to is required and must be Delhi time");
        }
        String normalized = value.trim();
        // Query params often decode '+' as space; recover common case "... 05:30".
        if (normalized.endsWith(" 05:30")) {
            normalized = normalized.substring(0, normalized.length() - " 05:30".length()) + "+05:30";
        }
        try {
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(normalized);
            if (!offsetDateTime.getOffset().equals(ZoneOffset.ofHoursMinutes(5, 30))) {
                throw new IllegalArgumentException("Invalid timezone offset. Use +05:30 (Delhi time)");
            }
            return offsetDateTime.atZoneSameInstant(CLIENT_ZONE);
        } catch (DateTimeParseException ignored) {
        }
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
            return localDateTime.atZone(CLIENT_ZONE);
        } catch (DateTimeParseException ignored) {
        }
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return localDateTime.atZone(CLIENT_ZONE);
        } catch (DateTimeParseException ignored) {
        }
        throw new IllegalArgumentException(
                "Invalid datetime format. Use Delhi time like yyyy-MM-dd'T'HH:mm:ss+05:30 or yyyy-MM-dd'T'HH:mm:ss");
    }
}
