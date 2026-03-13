package com.readywealth.trading.trade_engine.session.api.http.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.OffsetDateTime;

public record SeriesBarView(
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
        OffsetDateTime beginTime,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
        OffsetDateTime endTime,
        int barIndex,
        String mutation,
        String open,
        String high,
        String low,
        String close,
        String volume,
        long trades) {
}
