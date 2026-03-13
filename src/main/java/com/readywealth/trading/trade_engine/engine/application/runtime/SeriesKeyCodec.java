package com.readywealth.trading.trade_engine.engine.application.runtime;

import com.readywealth.trading.trade_engine.engine.domain.series.IntervalKind;
import com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey;

public final class SeriesKeyCodec {

    private SeriesKeyCodec() {
    }

    public static String encode(SeriesKey key) {
        return key.instrumentToken() + "@" + key.timeframe().name();
    }

    public static SeriesKey decode(String raw) {
        String[] parts = raw.split("@", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid seriesKey format. Expected <instrument>@<timeframe>");
        }
        long instrument = Long.parseLong(parts[0]);
        IntervalKind timeframe = IntervalKind.valueOf(parts[1]);
        return new SeriesKey(instrument, timeframe);
    }
}
