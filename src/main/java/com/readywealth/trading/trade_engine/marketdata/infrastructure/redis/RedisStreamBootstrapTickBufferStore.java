package com.readywealth.trading.trade_engine.marketdata.infrastructure.redis;

import com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey;
import com.readywealth.trading.trade_engine.engine.application.runtime.SeriesKeyCodec;
import com.readywealth.trading.trade_engine.marketdata.application.BootstrapTickBufferStore;
import com.readywealth.trading.trade_engine.marketdata.infrastructure.config.BootstrapProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RedisStreamBootstrapTickBufferStore implements BootstrapTickBufferStore {
    private static final Logger log = LoggerFactory.getLogger(RedisStreamBootstrapTickBufferStore.class);

    private final StringRedisTemplate redis;
    private final BootstrapProperties bootstrapProperties;
    private final AtomicLong pendingTicksApprox = new AtomicLong(0L);

    public RedisStreamBootstrapTickBufferStore(
            StringRedisTemplate redis,
            BootstrapProperties bootstrapProperties) {
        this.redis = redis;
        this.bootstrapProperties = bootstrapProperties;
    }

    @Override
    public void append(SeriesKey key, long runId, long timestampMs, double price, double volume, long receivedAtMs) {
        String streamKey = streamKey(key);
        String seqKey = seqKey(key);
        Long seq = redis.opsForValue().increment(seqKey);
        long ingestSeq = seq == null ? 0L : seq.longValue();

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("ingestSeq", String.valueOf(ingestSeq));
        fields.put("timestampMs", String.valueOf(timestampMs));
        fields.put("price", String.valueOf(price));
        fields.put("volume", String.valueOf(volume));
        fields.put("receivedAtMs", String.valueOf(receivedAtMs));
        fields.put("runId", String.valueOf(runId));

        RecordId id = redis.opsForStream().add(MapRecord.create(streamKey, fields));
        if (id != null) {
            long total = pendingTicksApprox.incrementAndGet();
            long guard = bootstrapProperties.getGuard().getMaxPendingTicksTotal();
            if (guard > 0 && total > guard && total % 1000 == 0) {
                log.warn("bootstrap_tick_buffer_guard_exceeded approxPending={} guard={} streamKey={}",
                        total, guard, streamKey);
            }
        }
    }

    @Override
    public List<BufferedBootstrapTick> readAll(SeriesKey key, long runId) {
        String streamKey = streamKey(key);
        List<MapRecord<String, Object, Object>> records = redis.opsForStream().range(streamKey, Range.unbounded());
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        List<BufferedBootstrapTick> out = new ArrayList<>(records.size());
        for (MapRecord<String, Object, Object> record : records) {
            Map<Object, Object> value = record.getValue();
            long recordRunId = parseLong(value.get("runId"));
            if (recordRunId != runId) {
                continue;
            }
            out.add(new BufferedBootstrapTick(
                    parseLong(value.get("ingestSeq")),
                    parseLong(value.get("timestampMs")),
                    parseDouble(value.get("price")),
                    parseDouble(value.get("volume")),
                    parseLong(value.get("receivedAtMs")),
                    recordRunId));
        }
        out.sort(Comparator
                .comparingLong(BufferedBootstrapTick::timestampMs)
                .thenComparingLong(BufferedBootstrapTick::ingestSeq));
        return out;
    }

    @Override
    public long size(SeriesKey key) {
        Long size = redis.opsForStream().size(streamKey(key));
        return size == null ? 0L : size.longValue();
    }

    @Override
    public void purge(SeriesKey key) {
        long removed = size(key);
        redis.delete(streamKey(key));
        redis.delete(seqKey(key));
        pendingTicksApprox.addAndGet(-Math.max(0L, removed));
    }

    private String streamKey(SeriesKey key) {
        return bootstrapProperties.getRedis().getKeyPrefix() + ":" + SeriesKeyCodec.encode(key);
    }

    private String seqKey(SeriesKey key) {
        return bootstrapProperties.getRedis().getKeyPrefix() + ":seq:" + SeriesKeyCodec.encode(key);
    }

    private long parseLong(Object raw) {
        if (raw == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private double parseDouble(Object raw) {
        if (raw == null) {
            return 0d;
        }
        try {
            return Double.parseDouble(String.valueOf(raw));
        } catch (Exception ignored) {
            return 0d;
        }
    }
}
