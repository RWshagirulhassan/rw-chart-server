package com.readywealth.trading.trade_engine.engine.domain.series;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

public final class IntervalDescriptors {
    private static final Map<IntervalKind, IntervalDescriptor> DESCRIPTORS = buildDescriptors();

    private IntervalDescriptors() {
    }

    public static IntervalDescriptor of(IntervalKind kind) {
        IntervalDescriptor descriptor = DESCRIPTORS.get(kind);
        if (descriptor == null) {
            throw new IllegalArgumentException("Unsupported interval: " + kind);
        }
        return descriptor;
    }

    private static Map<IntervalKind, IntervalDescriptor> buildDescriptors() {
        Map<IntervalKind, IntervalDescriptor> out = new EnumMap<>(IntervalKind.class);

        register(out, IntervalKind.TIME_1M, IntervalFamily.INTRADAY, Duration.ofMinutes(1), "minute", 3);
        register(out, IntervalKind.TIME_2M, IntervalFamily.INTRADAY, Duration.ofMinutes(2), "2minute", 3);
        register(out, IntervalKind.TIME_3M, IntervalFamily.INTRADAY, Duration.ofMinutes(3), "3minute", 3);
        register(out, IntervalKind.TIME_5M, IntervalFamily.INTRADAY, Duration.ofMinutes(5), "5minute", 10);
        register(out, IntervalKind.TIME_10M, IntervalFamily.INTRADAY, Duration.ofMinutes(10), "10minute", 10);
        register(out, IntervalKind.TIME_15M, IntervalFamily.INTRADAY, Duration.ofMinutes(15), "15minute", 10);
        register(out, IntervalKind.TIME_30M, IntervalFamily.INTRADAY, Duration.ofMinutes(30), "30minute", 60);
        register(out, IntervalKind.TIME_45M, IntervalFamily.INTRADAY, Duration.ofMinutes(45), "45minute", 60);
        register(out, IntervalKind.TIME_1H, IntervalFamily.INTRADAY, Duration.ofHours(1), "60minute", 60);
        register(out, IntervalKind.TIME_2H, IntervalFamily.INTRADAY, Duration.ofHours(2), null, 0);
        register(out, IntervalKind.TIME_3H, IntervalFamily.INTRADAY, Duration.ofHours(3), null, 0);
        register(out, IntervalKind.TIME_4H, IntervalFamily.INTRADAY, Duration.ofHours(4), null, 0);
        register(out, IntervalKind.TIME_1D, IntervalFamily.DAILY, Duration.ofDays(1), "day", 600);

        out.put(
                IntervalKind.TIME_1W,
                new IntervalDescriptor(
                        IntervalKind.TIME_1W,
                        IntervalFamily.CALENDAR,
                        null,
                        Duration.ofDays(7),
                        IntervalKind.TIME_1D,
                        null,
                        1500));
        out.put(
                IntervalKind.TIME_1MO,
                new IntervalDescriptor(
                        IntervalKind.TIME_1MO,
                        IntervalFamily.CALENDAR,
                        null,
                        Duration.ofDays(31),
                        IntervalKind.TIME_1D,
                        null,
                        2300));

        out.put(
                IntervalKind.TICK_10T,
                new IntervalDescriptor(
                        IntervalKind.TICK_10T,
                        IntervalFamily.TICK,
                        null,
                        Duration.ofMinutes(1),
                        null,
                        null,
                        0));
        out.put(
                IntervalKind.TICK_100T,
                new IntervalDescriptor(
                        IntervalKind.TICK_100T,
                        IntervalFamily.TICK,
                        null,
                        Duration.ofMinutes(1),
                        null,
                        null,
                        0));
        out.put(
                IntervalKind.TICK_1000T,
                new IntervalDescriptor(
                        IntervalKind.TICK_1000T,
                        IntervalFamily.TICK,
                        null,
                        Duration.ofMinutes(1),
                        null,
                        null,
                        0));
        return out;
    }

    private static void register(
            Map<IntervalKind, IntervalDescriptor> out,
            IntervalKind kind,
            IntervalFamily family,
            Duration duration,
            String kiteInterval,
            int lookbackBusinessDays) {
        out.put(
                kind,
                new IntervalDescriptor(
                        kind,
                        family,
                        duration,
                        duration,
                        kind,
                        kiteInterval,
                        lookbackBusinessDays));
    }
}
