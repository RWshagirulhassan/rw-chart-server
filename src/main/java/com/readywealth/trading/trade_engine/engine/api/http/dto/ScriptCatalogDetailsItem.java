package com.readywealth.trading.trade_engine.engine.api.http.dto;

import java.util.List;

public record ScriptCatalogDetailsItem(
        String scriptId,
        String name,
        String kind,
        String description,
        List<ScriptParamView> params) {
}
