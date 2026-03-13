package com.readywealth.trading.trade_engine.execution.application.service;

import com.readywealth.trading.trade_engine.execution.application.port.MarginService;
import com.readywealth.trading.trade_engine.execution.application.port.TradingStorePort;
import com.readywealth.trading.trade_engine.execution.domain.ChargesResult;
import com.readywealth.trading.trade_engine.execution.domain.MarginCheckResult;
import com.readywealth.trading.trade_engine.execution.domain.MarginSource;
import com.readywealth.trading.trade_engine.execution.domain.OrderDraft;
import com.readywealth.trading.trade_engine.execution.domain.TradingAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MarginGatewayService {

    private static final Logger log = LoggerFactory.getLogger(MarginGatewayService.class);

    private final MarginService kiteMarginService;
    private final MarginService simulatedMarginService;
    private final TradingStorePort tradingStorePort;

    public MarginGatewayService(
            @Qualifier("kiteMarginService") MarginService kiteMarginService,
            @Qualifier("simulatedMarginService") MarginService simulatedMarginService,
            TradingStorePort tradingStorePort) {
        this.kiteMarginService = kiteMarginService;
        this.simulatedMarginService = simulatedMarginService;
        this.tradingStorePort = tradingStorePort;
    }

    public MarginCheckResult checkMargins(TradingAccount account, List<OrderDraft> basket) {
        try {
            MarginCheckResult result = kiteMarginService.checkMargins(account.accountId(), basket);
            tradingStorePort.saveLastMargin(account.accountId(), result);
            return result;
        } catch (Exception ex) {
            log.warn("margin_check_kite_failed accountId={} reason='{}'", account.accountId(), ex.getMessage());
            MarginCheckResult fallback = simulatedMarginService.checkMargins(account.accountId(), basket);
            MarginCheckResult degraded = new MarginCheckResult(
                    fallback.allowed(),
                    fallback.requiredMargin(),
                    fallback.finalMargin(),
                    fallback.breakdown(),
                    "kite_margin_failed_fallback_simulated: " + ex.getMessage(),
                    MarginSource.SIMULATED,
                    true);
            tradingStorePort.saveLastMargin(account.accountId(), degraded);
            return degraded;
        }
    }

    public ChargesResult estimateCharges(TradingAccount account, List<OrderDraft> basket) {
        try {
            return kiteMarginService.estimateCharges(account.accountId(), basket);
        } catch (Exception ex) {
            log.warn("charges_estimate_kite_failed accountId={} reason='{}'", account.accountId(), ex.getMessage());
            ChargesResult fallback = simulatedMarginService.estimateCharges(account.accountId(), basket);
            Map<String, Double> breakdown = new HashMap<>(fallback.breakdown());
            breakdown.put("degraded", 1.0d);
            return new ChargesResult(
                    fallback.totalCharges(),
                    Map.copyOf(breakdown),
                    MarginSource.SIMULATED,
                    true,
                    "kite_charges_failed_fallback_simulated: " + ex.getMessage());
        }
    }
}
