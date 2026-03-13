package com.readywealth.trading.trade_engine.engine.domain.script;

import java.util.Collections;
import java.util.Map;

public record ScriptParams(Map<String, Object> values) {

    public ScriptParams {
        values = (values == null) ? Collections.emptyMap() : Map.copyOf(values);
    }

    public int getInt(String key, int defaultValue) {
        Object v = values.get(key);
        if (v == null)
            return defaultValue;
        if (v instanceof Number n)
            return n.intValue();
        return Integer.parseInt(v.toString());
    }

    public double getDouble(String key, double defaultValue) {
        Object v = values.get(key);
        if (v == null)
            return defaultValue;
        if (v instanceof Number n)
            return n.doubleValue();
        return Double.parseDouble(v.toString());
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        Object v = values.get(key);
        if (v == null)
            return defaultValue;
        if (v instanceof Boolean b)
            return b;
        return Boolean.parseBoolean(v.toString());
    }
}
