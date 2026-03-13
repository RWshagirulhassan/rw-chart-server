package com.readywealth.trading.trade_engine.execution.infrastructure.kite;

import com.readywealth.trading.trade_engine.auth.infrastructure.config.KiteProperties;
import com.readywealth.trading.trade_engine.execution.application.port.BrokerEventListener;
import com.readywealth.trading.trade_engine.execution.application.port.BrokerGateway;
import com.readywealth.trading.trade_engine.execution.application.port.BrokerStreamSubscription;
import com.readywealth.trading.trade_engine.execution.application.port.CancelOrderResult;
import com.readywealth.trading.trade_engine.execution.application.port.ModifyOrderResult;
import com.readywealth.trading.trade_engine.execution.application.port.PlaceOrderResult;
import com.readywealth.trading.trade_engine.execution.domain.OrderDraft;
import com.readywealth.trading.trade_engine.execution.domain.TradingFundsView;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrder;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderStatus;
import com.readywealth.trading.trade_engine.execution.domain.TradingPosition;
import com.readywealth.trading.trade_engine.execution.infrastructure.config.TradingProperties;
import com.readywealth.trading.trade_engine.marketdata.application.AuthTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component("kiteBrokerGateway")
public class KiteBrokerGateway implements BrokerGateway {

    private static final Logger log = LoggerFactory.getLogger(KiteBrokerGateway.class);

    private final AuthTokenProvider authTokenProvider;
    private final KiteProperties kiteProperties;
    private final TradingProperties tradingProperties;
    private final RestTemplate restTemplate;

    public KiteBrokerGateway(
            AuthTokenProvider authTokenProvider,
            KiteProperties kiteProperties,
            TradingProperties tradingProperties) {
        this.authTokenProvider = authTokenProvider;
        this.kiteProperties = kiteProperties;
        this.tradingProperties = tradingProperties;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public PlaceOrderResult placeOrder(OrderDraft orderDraft, TradingOrder canonicalOrder) {
        long now = Instant.now().toEpochMilli();
        if (!tradingProperties.getKite().isLiveOrdersEnabled()) {
            TradingOrder rejected = canonicalOrder.withStatus(
                    TradingOrderStatus.REJECTED,
                    now,
                    "kite_live_orders_disabled");
            return new PlaceOrderResult(rejected, List.of(), "kite_live_orders_disabled");
        }
        TradingOrder rejected = canonicalOrder.withStatus(
                TradingOrderStatus.REJECTED,
                now,
                "kite_live_order_place_not_implemented_v1");
        return new PlaceOrderResult(rejected, List.of(), "kite_place_stubbed");
    }

    @Override
    public CancelOrderResult cancelOrder(TradingOrder currentOrder, String reason) {
        if (!tradingProperties.getKite().isLiveOrdersEnabled()) {
            throw new IllegalStateException("kite live order mutations are disabled");
        }
        throw new IllegalStateException("kite cancel skeleton not implemented yet");
    }

    @Override
    public ModifyOrderResult modifyOrder(TradingOrder currentOrder, Integer qty, Double price, Double triggerPrice) {
        if (!tradingProperties.getKite().isLiveOrdersEnabled()) {
            throw new IllegalStateException("kite live order mutations are disabled");
        }
        throw new IllegalStateException("kite modify skeleton not implemented yet");
    }

    @Override
    public List<TradingOrder> getOrders(String accountId) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(tradingProperties.getKite().getBaseUrl())
                    .path("/orders")
                    .build()
                    .toUri();
            RequestEntity<Void> request = RequestEntity
                    .get(uri)
                    .headers(authHeaders())
                    .build();
            ResponseEntity<String> response = restTemplate.exchange(request, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                return List.of();
            }
        } catch (Exception ex) {
            log.warn("kite_get_orders_failed accountId={} reason='{}'", accountId, ex.getMessage());
        }
        return List.of();
    }

    @Override
    public List<TradingPosition> getPositions(String accountId) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(tradingProperties.getKite().getBaseUrl())
                    .path("/portfolio/positions")
                    .build()
                    .toUri();
            RequestEntity<Void> request = RequestEntity
                    .get(uri)
                    .headers(authHeaders())
                    .build();
            ResponseEntity<String> response = restTemplate.exchange(request, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                return List.of();
            }
        } catch (Exception ex) {
            log.warn("kite_get_positions_failed accountId={} reason='{}'", accountId, ex.getMessage());
        }
        return List.of();
    }

    @Override
    public TradingFundsView getFunds(String accountId) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(tradingProperties.getKite().getBaseUrl())
                    .path("/user/margins")
                    .queryParam("segment", "equity")
                    .build()
                    .toUri();
            RequestEntity<Void> request = RequestEntity
                    .get(uri)
                    .headers(authHeaders())
                    .build();
            ResponseEntity<String> response = restTemplate.exchange(request, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                return TradingFundsView.opening(0.0d, System.currentTimeMillis());
            }
        } catch (Exception ex) {
            log.warn("kite_get_funds_failed accountId={} reason='{}'", accountId, ex.getMessage());
        }
        return TradingFundsView.opening(0.0d, System.currentTimeMillis());
    }

    @Override
    public BrokerStreamSubscription subscribe(String accountId, BrokerEventListener listener) {
        // v1 skeleton: postback/ws bridge is modeled but not wired here yet.
        return () -> {
            // no-op
        };
    }

    private HttpHeaders authHeaders() {
        String accessToken = authTokenProvider.getValidAccessToken()
                .orElseThrow(() -> new IllegalStateException("kite access token unavailable"));
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "token " + kiteProperties.getApiKey() + ":" + accessToken);
        headers.set("X-Kite-Version", "3");
        headers.set("X-Request-Id", UUID.randomUUID().toString());
        return headers;
    }
}
