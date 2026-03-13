package com.readywealth.trading.trade_engine.execution.infrastructure.paper;

import com.readywealth.trading.trade_engine.execution.application.port.LastPricePort;
import com.readywealth.trading.trade_engine.execution.application.port.MarginService;
import com.readywealth.trading.trade_engine.execution.application.port.TradingStorePort;
import com.readywealth.trading.trade_engine.execution.domain.ChargesResult;
import com.readywealth.trading.trade_engine.execution.domain.MarginCheckResult;
import com.readywealth.trading.trade_engine.execution.domain.MarginSource;
import com.readywealth.trading.trade_engine.execution.domain.OrderDraft;
import com.readywealth.trading.trade_engine.execution.domain.PriceSnapshot;
import com.readywealth.trading.trade_engine.execution.domain.TradingFundsView;
import com.readywealth.trading.trade_engine.execution.domain.TradingProduct;
import com.readywealth.trading.trade_engine.execution.domain.TradingSide;
import com.readywealth.trading.trade_engine.execution.infrastructure.config.TradingProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component("simulatedMarginService")
public class SimulatedMarginService implements MarginService {

    private final TradingStorePort tradingStorePort;
    private final LastPricePort lastPricePort;
    private final TradingProperties tradingProperties;

    public SimulatedMarginService(
            TradingStorePort tradingStorePort,
            LastPricePort lastPricePort,
            TradingProperties tradingProperties) {
        this.tradingStorePort = tradingStorePort;
        this.lastPricePort = lastPricePort;
        this.tradingProperties = tradingProperties;
    }

    @Override
    public MarginCheckResult checkMargins(String accountId, List<OrderDraft> basket) {
        if (basket == null || basket.isEmpty()) {
            return MarginCheckResult.rejected("basket cannot be empty", MarginSource.SIMULATED);
        }

        TradingFundsView funds = tradingStorePort.findFunds(accountId)
                .orElseGet(() -> TradingFundsView.opening(0.0d, System.currentTimeMillis()));
        double available = funds.availableCash();

        double required = 0.0d;
        Map<String, Double> breakdown = new LinkedHashMap<>();
        for (OrderDraft orderDraft : basket) {
            double price = resolvedPrice(orderDraft);
            double notional = Math.max(0.0d, price * orderDraft.qty());
            double perOrder;
            if (orderDraft.product() == TradingProduct.CNC) {
                if (orderDraft.side() == TradingSide.SELL) {
                    return new MarginCheckResult(
                            false,
                            required,
                            required,
                            breakdown,
                            "CNC SELL is blocked in simulated equity-first mode",
                            MarginSource.SIMULATED,
                            false);
                }
                perOrder = notional;
            } else if (orderDraft.product() == TradingProduct.MIS) {
                perOrder = notional * tradingProperties.getMargin().getMisPercent();
            } else {
                perOrder = notional * tradingProperties.getMargin().getNrmlPercent();
            }
            required += perOrder;
            breakdown.put("order_" + orderDraft.instrumentToken(), perOrder);
        }

        boolean allowed = required <= available;
        return new MarginCheckResult(
                allowed,
                required,
                required,
                Map.copyOf(breakdown),
                allowed ? "simulated_margin_ok" : "insufficient_margin",
                MarginSource.SIMULATED,
                false);
    }

    @Override
    public ChargesResult estimateCharges(String accountId, List<OrderDraft> basket) {
        if (basket == null || basket.isEmpty()) {
            return new ChargesResult(0.0d, Map.of(), MarginSource.SIMULATED, false, "basket empty");
        }
        double total = 0.0d;
        Map<String, Double> breakdown = new LinkedHashMap<>();
        for (OrderDraft orderDraft : basket) {
            double notional = resolvedPrice(orderDraft) * orderDraft.qty();
            double charge = notional * tradingProperties.getMargin().getChargeBps() / 10000.0d;
            breakdown.put("order_" + orderDraft.instrumentToken(), charge);
            total += charge;
        }
        breakdown.put("total", total);
        return new ChargesResult(total, Map.copyOf(breakdown), MarginSource.SIMULATED, false, "simulated_charges");
    }

    private double resolvedPrice(OrderDraft draft) {
        if (draft.price() != null && draft.price() > 0) {
            return draft.price();
        }
        return lastPricePort.getPriceSnapshot(draft.instrumentToken())
                .map(PriceSnapshot::lastPrice)
                .orElse(0.0d);
    }
}
