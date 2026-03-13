package com.readywealth.trading.trade_engine.execution.application.service;

import com.readywealth.trading.trade_engine.engine.domain.script.ScriptAttachment;
import com.readywealth.trading.trade_engine.engine.domain.series.SeriesKey;
import com.readywealth.trading.trade_engine.engine.application.runtime.SeriesRuntimeManager;
import com.readywealth.trading.trade_engine.engine.application.runtime.model.TradeIntent;
import com.readywealth.trading.trade_engine.execution.application.port.InstrumentMetadataPort;
import com.readywealth.trading.trade_engine.execution.application.port.TradingStorePort;
import com.readywealth.trading.trade_engine.execution.domain.OrderDraft;
import com.readywealth.trading.trade_engine.execution.domain.StrategyExecutionSettings;
import com.readywealth.trading.trade_engine.execution.domain.StrategyOrderContract;
import com.readywealth.trading.trade_engine.execution.domain.TradingAccount;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventSource;
import com.readywealth.trading.trade_engine.execution.domain.TradingProduct;
import com.readywealth.trading.trade_engine.execution.domain.TradingSide;
import com.readywealth.trading.trade_engine.execution.infrastructure.config.TradingProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class StrategyIntentDispatchService {

    private static final Logger log = LoggerFactory.getLogger(StrategyIntentDispatchService.class);

    private final SeriesRuntimeManager runtimeManager;
    private final TradingStorePort tradingStorePort;
    private final TradeOrchestratorService tradeOrchestratorService;
    private final StrategyContractResolverService strategyContractResolverService;
    private final InstrumentMetadataPort instrumentMetadataPort;
    private final TradingProperties tradingProperties;
    private final ExecutorService dispatcher;
    private final Counter droppedCounter;

    public StrategyIntentDispatchService(
            SeriesRuntimeManager runtimeManager,
            TradingStorePort tradingStorePort,
            TradeOrchestratorService tradeOrchestratorService,
            StrategyContractResolverService strategyContractResolverService,
            InstrumentMetadataPort instrumentMetadataPort,
            TradingProperties tradingProperties,
            MeterRegistry meterRegistry) {
        this.runtimeManager = runtimeManager;
        this.tradingStorePort = tradingStorePort;
        this.tradeOrchestratorService = tradeOrchestratorService;
        this.strategyContractResolverService = strategyContractResolverService;
        this.instrumentMetadataPort = instrumentMetadataPort;
        this.tradingProperties = tradingProperties;
        this.dispatcher = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "strategy-intent-dispatch");
            thread.setDaemon(true);
            return thread;
        });
        this.droppedCounter = meterRegistry.counter("trading_intent_dispatch_dropped_total");
    }

    public void dispatchAsync(SeriesKey seriesKey, TradeIntent intent) {
        if (seriesKey == null || intent == null) {
            return;
        }
        dispatcher.submit(() -> dispatchInternal(seriesKey, intent));
    }

    private void dispatchInternal(SeriesKey seriesKey, TradeIntent intent) {
        Optional<ScriptAttachment> scriptOpt = runtimeManager.scriptAttachment(seriesKey, intent.scriptInstanceId());
        if (scriptOpt.isEmpty()) {
            droppedCounter.increment();
            return;
        }
        ScriptAttachment scriptAttachment = scriptOpt.get();
        if (scriptAttachment.ownerUserId() == null || scriptAttachment.ownerUserId().isBlank()) {
            droppedCounter.increment();
            return;
        }

        Optional<TradingAccount> accountOpt = tradingStorePort.findAccountByUserId(scriptAttachment.ownerUserId());
        if (accountOpt.isEmpty()) {
            droppedCounter.increment();
            return;
        }
        TradingAccount account = accountOpt.get();
        if (!account.autoTradeEnabled()) {
            return;
        }

        String scriptId = scriptAttachment.scriptId().value();
        Optional<StrategyOrderContract> contractOpt = strategyContractResolverService.resolve(scriptId);
        if (contractOpt.isEmpty()) {
            droppedCounter.increment();
            return;
        }
        StrategyOrderContract contract = contractOpt.get();
        if (!contract.autoTradable()) {
            droppedCounter.increment();
            return;
        }

        Optional<StrategyExecutionSettings> settingsOpt = tradingStorePort.getStrategySettings(account.accountId(), scriptId);
        if (settingsOpt.isEmpty()) {
            droppedCounter.increment();
            return;
        }
        StrategyExecutionSettings settings = settingsOpt.get();
        if (!settings.enabled() || settings.fixedQty() <= 0) {
            droppedCounter.increment();
            return;
        }

        String intentDedupe = account.accountId() + ":" + intent.scriptInstanceId() + ":" + intent.barIndex() + ":"
                + intent.side() + ":" + intent.reason();
        boolean shouldProceed = tradingStorePort.registerIntentExecution(
                account.accountId(),
                intentDedupe,
                tradingProperties.getIntent().getDedupeTtlSeconds());
        if (!shouldProceed) {
            return;
        }

        TradingSide side = mapSide(intent.side());
        if (side == null) {
            droppedCounter.increment();
            return;
        }

        InstrumentMetadataPort.InstrumentMeta instrumentMeta = instrumentMetadataPort.resolve(intent.instrumentToken())
                .orElseGet(() -> new InstrumentMetadataPort.InstrumentMeta(intent.instrumentToken(), null, "NSE"));

        OrderDraft draft = new OrderDraft(
                account.accountId(),
                intent.instrumentToken(),
                instrumentMeta.tradingsymbol(),
                instrumentMeta.exchange(),
                settings.product() == null ? TradingProduct.MIS : settings.product(),
                contract.requiredOrderType(),
                side,
                settings.fixedQty(),
                intent.price(),
                null,
                TradingEventSource.CORE,
                scriptId,
                "intent:" + intent.reason() + ":at=" + Instant.ofEpochMilli(intent.timestampEpochMs()));

        try {
            tradeOrchestratorService.placeOrder(account, draft);
        } catch (Exception ex) {
            droppedCounter.increment();
            log.warn("intent_dispatch_failed accountId={} scriptId={} reason='{}'",
                    account.accountId(), scriptId, ex.getMessage());
        }
    }

    private TradingSide mapSide(String rawSide) {
        if (rawSide == null) {
            return null;
        }
        return switch (rawSide.trim().toUpperCase()) {
            case "UP", "BUY" -> TradingSide.BUY;
            case "DOWN", "SELL" -> TradingSide.SELL;
            default -> null;
        };
    }

    @PreDestroy
    public void shutdown() {
        dispatcher.shutdownNow();
    }
}
