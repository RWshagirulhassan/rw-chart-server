package com.readywealth.trading.trade_engine.session.infrastructure.config;

import com.readywealth.trading.trade_engine.engine.application.runtime.ScriptExecutionMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ConfigurationProperties(prefix = "session.stress")
public class StressSessionProperties {
    private static final int DEFAULT_MAX_BAR_COUNT = 5000;
    private static final int DEFAULT_MAX_INSTRUMENTS = 200;

    private boolean enabled = false;
    private long pollIntervalMs = 5000L;
    private Integer maxBarCount = DEFAULT_MAX_BAR_COUNT;
    private Integer maxInstruments = DEFAULT_MAX_INSTRUMENTS;
    private boolean destroyOnClose = false;
    private List<Long> instrumentTokens = new ArrayList<>();
    private List<String> symbols = new ArrayList<>();
    private String scriptId;
    private String scriptExecutionMode = ScriptExecutionMode.ON_CANDLE_CLOSE.name();
    private Map<String, Object> scriptParams = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public Integer getMaxBarCount() {
        return maxBarCount;
    }

    public void setMaxBarCount(Integer maxBarCount) {
        this.maxBarCount = maxBarCount;
    }

    public Integer getMaxInstruments() {
        return maxInstruments;
    }

    public void setMaxInstruments(Integer maxInstruments) {
        this.maxInstruments = maxInstruments;
    }

    public boolean isDestroyOnClose() {
        return destroyOnClose;
    }

    public void setDestroyOnClose(boolean destroyOnClose) {
        this.destroyOnClose = destroyOnClose;
    }

    public List<Long> getInstrumentTokens() {
        return instrumentTokens;
    }

    public void setInstrumentTokens(List<Long> instrumentTokens) {
        this.instrumentTokens = instrumentTokens == null ? new ArrayList<>() : instrumentTokens;
    }

    public List<String> getSymbols() {
        return symbols;
    }

    public void setSymbols(List<String> symbols) {
        this.symbols = symbols == null ? new ArrayList<>() : symbols;
    }

    public String getScriptId() {
        return scriptId;
    }

    public void setScriptId(String scriptId) {
        this.scriptId = scriptId;
    }

    public String getScriptExecutionMode() {
        return scriptExecutionMode;
    }

    public void setScriptExecutionMode(String scriptExecutionMode) {
        this.scriptExecutionMode = scriptExecutionMode;
    }

    public Map<String, Object> getScriptParams() {
        return scriptParams;
    }

    public void setScriptParams(Map<String, Object> scriptParams) {
        this.scriptParams = scriptParams == null ? new LinkedHashMap<>() : scriptParams;
    }

    public int resolvedMaxBarCount() {
        if (maxBarCount == null) {
            return DEFAULT_MAX_BAR_COUNT;
        }
        return Math.max(1, maxBarCount);
    }

    public int resolvedMaxInstruments() {
        if (maxInstruments == null) {
            return DEFAULT_MAX_INSTRUMENTS;
        }
        return Math.max(1, maxInstruments);
    }

    public List<Long> normalizedInstrumentTokens() {
        LinkedHashSet<Long> deduped = new LinkedHashSet<>();
        if (instrumentTokens != null) {
            for (Long token : instrumentTokens) {
                if (token != null && token > 0) {
                    deduped.add(token);
                }
            }
        }
        return new ArrayList<>(deduped);
    }

    public List<String> normalizedSymbols() {
        LinkedHashSet<String> deduped = new LinkedHashSet<>();
        if (symbols != null) {
            for (String symbol : symbols) {
                if (symbol == null) {
                    continue;
                }
                String normalized = symbol.trim().toUpperCase(Locale.ROOT);
                if (!normalized.isEmpty()) {
                    deduped.add(normalized);
                }
            }
        }
        return new ArrayList<>(deduped);
    }

    public boolean hasScript() {
        return scriptId != null && !scriptId.isBlank();
    }

    public String resolvedScriptId() {
        return scriptId == null ? "" : scriptId.trim();
    }

    public ScriptExecutionMode resolvedScriptExecutionMode() {
        return ScriptExecutionMode.fromNullable(scriptExecutionMode, ScriptExecutionMode.ON_CANDLE_CLOSE);
    }

    public Map<String, Object> resolvedScriptParams() {
        return scriptParams == null ? Map.of() : Map.copyOf(scriptParams);
    }
}
