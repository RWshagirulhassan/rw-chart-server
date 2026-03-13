package com.readywealth.trading.trade_engine.execution.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trading")
public class TradingProperties {

    private final Retention retention = new Retention();
    private final Margin margin = new Margin();
    private final Paper paper = new Paper();
    private final Kite kite = new Kite();
    private final Reconciliation reconciliation = new Reconciliation();
    private final Guardrails guardrails = new Guardrails();
    private final Intent intent = new Intent();

    public Retention getRetention() {
        return retention;
    }

    public Margin getMargin() {
        return margin;
    }

    public Paper getPaper() {
        return paper;
    }

    public Kite getKite() {
        return kite;
    }

    public Reconciliation getReconciliation() {
        return reconciliation;
    }

    public Guardrails getGuardrails() {
        return guardrails;
    }

    public Intent getIntent() {
        return intent;
    }

    public static class Retention {
        private int orderTtlSeconds = 604800;
        private int fillTtlSeconds = 604800;
        private int dedupeTtlSeconds = 172800;

        public int getOrderTtlSeconds() {
            return orderTtlSeconds;
        }

        public void setOrderTtlSeconds(int orderTtlSeconds) {
            this.orderTtlSeconds = orderTtlSeconds;
        }

        public int getFillTtlSeconds() {
            return fillTtlSeconds;
        }

        public void setFillTtlSeconds(int fillTtlSeconds) {
            this.fillTtlSeconds = fillTtlSeconds;
        }

        public int getDedupeTtlSeconds() {
            return dedupeTtlSeconds;
        }

        public void setDedupeTtlSeconds(int dedupeTtlSeconds) {
            this.dedupeTtlSeconds = dedupeTtlSeconds;
        }
    }

    public static class Margin {
        private double misPercent = 0.2d;
        private double nrmlPercent = 0.4d;
        private double chargeBps = 2.0d;

        public double getMisPercent() {
            return misPercent;
        }

        public void setMisPercent(double misPercent) {
            this.misPercent = misPercent;
        }

        public double getNrmlPercent() {
            return nrmlPercent;
        }

        public void setNrmlPercent(double nrmlPercent) {
            this.nrmlPercent = nrmlPercent;
        }

        public double getChargeBps() {
            return chargeBps;
        }

        public void setChargeBps(double chargeBps) {
            this.chargeBps = chargeBps;
        }
    }

    public static class Paper {
        private double slippageBps = 2.0d;
        private double feeBps = 1.5d;
        private boolean executionCycleEnabled = true;
        private long executionCycleFixedDelayMs = 500L;
        private double pnlEpsilon = 0.01d;
        private long triggerEvaluationFixedDelayMs = 100L;
        private boolean eodSweepEnabled = true;
        private long eodSweepFixedDelayMs = 15000L;
        private String eodCutoffTime = "15:29:30";

        public double getSlippageBps() {
            return slippageBps;
        }

        public void setSlippageBps(double slippageBps) {
            this.slippageBps = slippageBps;
        }

        public double getFeeBps() {
            return feeBps;
        }

        public void setFeeBps(double feeBps) {
            this.feeBps = feeBps;
        }

        public boolean isExecutionCycleEnabled() {
            return executionCycleEnabled;
        }

        public void setExecutionCycleEnabled(boolean executionCycleEnabled) {
            this.executionCycleEnabled = executionCycleEnabled;
        }

        public long getExecutionCycleFixedDelayMs() {
            return executionCycleFixedDelayMs;
        }

        public void setExecutionCycleFixedDelayMs(long executionCycleFixedDelayMs) {
            this.executionCycleFixedDelayMs = executionCycleFixedDelayMs;
        }

        public double getPnlEpsilon() {
            return pnlEpsilon;
        }

        public void setPnlEpsilon(double pnlEpsilon) {
            this.pnlEpsilon = pnlEpsilon;
        }

        public long getTriggerEvaluationFixedDelayMs() {
            return triggerEvaluationFixedDelayMs;
        }

        public void setTriggerEvaluationFixedDelayMs(long triggerEvaluationFixedDelayMs) {
            this.triggerEvaluationFixedDelayMs = triggerEvaluationFixedDelayMs;
        }

        public boolean isEodSweepEnabled() {
            return eodSweepEnabled;
        }

        public void setEodSweepEnabled(boolean eodSweepEnabled) {
            this.eodSweepEnabled = eodSweepEnabled;
        }

        public long getEodSweepFixedDelayMs() {
            return eodSweepFixedDelayMs;
        }

        public void setEodSweepFixedDelayMs(long eodSweepFixedDelayMs) {
            this.eodSweepFixedDelayMs = eodSweepFixedDelayMs;
        }

        public String getEodCutoffTime() {
            return eodCutoffTime;
        }

        public void setEodCutoffTime(String eodCutoffTime) {
            this.eodCutoffTime = eodCutoffTime;
        }
    }

    public static class Kite {
        private String baseUrl = "https://api.kite.trade";
        private boolean liveOrdersEnabled;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public boolean isLiveOrdersEnabled() {
            return liveOrdersEnabled;
        }

        public void setLiveOrdersEnabled(boolean liveOrdersEnabled) {
            this.liveOrdersEnabled = liveOrdersEnabled;
        }
    }

    public static class Reconciliation {
        private boolean enabled;
        private long fixedDelayMs = 5000L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getFixedDelayMs() {
            return fixedDelayMs;
        }

        public void setFixedDelayMs(long fixedDelayMs) {
            this.fixedDelayMs = fixedDelayMs;
        }
    }

    public static class Guardrails {
        private int maxOpenOrdersPerAccount = 300;
        private int maxOrdersPerMinute = 180;

        public int getMaxOpenOrdersPerAccount() {
            return maxOpenOrdersPerAccount;
        }

        public void setMaxOpenOrdersPerAccount(int maxOpenOrdersPerAccount) {
            this.maxOpenOrdersPerAccount = maxOpenOrdersPerAccount;
        }

        public int getMaxOrdersPerMinute() {
            return maxOrdersPerMinute;
        }

        public void setMaxOrdersPerMinute(int maxOrdersPerMinute) {
            this.maxOrdersPerMinute = maxOrdersPerMinute;
        }
    }

    public static class Intent {
        private int dedupeTtlSeconds = 86400;

        public int getDedupeTtlSeconds() {
            return dedupeTtlSeconds;
        }

        public void setDedupeTtlSeconds(int dedupeTtlSeconds) {
            this.dedupeTtlSeconds = dedupeTtlSeconds;
        }
    }
}
