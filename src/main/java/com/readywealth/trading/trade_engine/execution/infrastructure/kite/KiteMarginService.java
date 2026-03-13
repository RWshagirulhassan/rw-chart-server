package com.readywealth.trading.trade_engine.execution.infrastructure.kite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.readywealth.trading.trade_engine.auth.infrastructure.config.KiteProperties;
import com.readywealth.trading.trade_engine.execution.application.port.MarginService;
import com.readywealth.trading.trade_engine.execution.domain.ChargesResult;
import com.readywealth.trading.trade_engine.execution.domain.MarginCheckResult;
import com.readywealth.trading.trade_engine.execution.domain.MarginSource;
import com.readywealth.trading.trade_engine.execution.domain.OrderDraft;
import com.readywealth.trading.trade_engine.marketdata.application.AuthTokenProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component("kiteMarginService")
public class KiteMarginService implements MarginService {

    private final AuthTokenProvider authTokenProvider;
    private final KiteProperties kiteProperties;
    private final com.readywealth.trading.trade_engine.execution.infrastructure.config.TradingProperties tradingProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public KiteMarginService(
            AuthTokenProvider authTokenProvider,
            KiteProperties kiteProperties,
            com.readywealth.trading.trade_engine.execution.infrastructure.config.TradingProperties tradingProperties,
            ObjectMapper objectMapper) {
        this.authTokenProvider = authTokenProvider;
        this.kiteProperties = kiteProperties;
        this.tradingProperties = tradingProperties;
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }

    @Override
    public MarginCheckResult checkMargins(String accountId, List<OrderDraft> basket) {
        if (basket == null || basket.isEmpty()) {
            return MarginCheckResult.rejected("basket cannot be empty", MarginSource.KITE);
        }

        String token = authTokenProvider.getValidAccessToken()
                .orElseThrow(() -> new IllegalStateException("kite access token unavailable"));

        String endpoint = basket.size() > 1 ? "/margins/basket" : "/margins/orders";
        URI uri = UriComponentsBuilder.fromUriString(tradingProperties.getKite().getBaseUrl())
                .path(endpoint)
                .build()
                .toUri();

        List<Map<String, Object>> payload = basket.stream().map(this::toKiteOrder).toList();
        RequestEntity<List<Map<String, Object>>> request = RequestEntity
                .post(uri)
                .header(HttpHeaders.AUTHORIZATION, "token " + kiteProperties.getApiKey() + ":" + token)
                .header("X-Kite-Version", "3")
                .body(payload);

        ResponseEntity<String> response = restTemplate.exchange(request, String.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("kite margin endpoint failed");
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.path("data");
            double required = 0.0d;
            double finalMargin = 0.0d;
            if (data.isArray()) {
                for (JsonNode node : data) {
                    required += readFirstDouble(node, "total", "initial", "required");
                    finalMargin += readFirstDouble(node, "final", "final_total", "total");
                }
            } else {
                required = readFirstDouble(data, "total", "initial", "required");
                finalMargin = readFirstDouble(data, "final", "final_total", "total");
            }
            Map<String, Double> breakdown = new LinkedHashMap<>();
            breakdown.put("required", required);
            breakdown.put("final", finalMargin);
            return new MarginCheckResult(
                    true,
                    required,
                    finalMargin,
                    Map.copyOf(breakdown),
                    "kite_margin_ok",
                    MarginSource.KITE,
                    false);
        } catch (Exception ex) {
            throw new IllegalStateException("kite_margin_parse_failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public ChargesResult estimateCharges(String accountId, List<OrderDraft> basket) {
        if (basket == null || basket.isEmpty()) {
            return new ChargesResult(0.0d, Map.of(), MarginSource.KITE, false, "basket empty");
        }

        String token = authTokenProvider.getValidAccessToken()
                .orElseThrow(() -> new IllegalStateException("kite access token unavailable"));
        URI uri = UriComponentsBuilder.fromUriString(tradingProperties.getKite().getBaseUrl())
                .path("/charges/orders")
                .build()
                .toUri();
        List<Map<String, Object>> payload = basket.stream().map(this::toKiteOrder).toList();

        RequestEntity<List<Map<String, Object>>> request = RequestEntity
                .post(uri)
                .header(HttpHeaders.AUTHORIZATION, "token " + kiteProperties.getApiKey() + ":" + token)
                .header("X-Kite-Version", "3")
                .body(payload);
        ResponseEntity<String> response = restTemplate.exchange(request, String.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("kite charges endpoint failed");
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.path("data");
            double total = 0.0d;
            if (data.isArray()) {
                for (JsonNode node : data) {
                    total += readFirstDouble(node, "total", "charges", "transaction_tax");
                }
            } else {
                total = readFirstDouble(data, "total", "charges", "transaction_tax");
            }
            return new ChargesResult(total, Map.of("total", total), MarginSource.KITE, false, "kite_charges_ok");
        } catch (Exception ex) {
            throw new IllegalStateException("kite_charges_parse_failed: " + ex.getMessage(), ex);
        }
    }

    private Map<String, Object> toKiteOrder(OrderDraft orderDraft) {
        if (orderDraft.tradingsymbol() == null || orderDraft.tradingsymbol().isBlank()) {
            throw new IllegalArgumentException("tradingsymbol is required for kite margin calls");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exchange", orderDraft.exchange());
        payload.put("tradingsymbol", orderDraft.tradingsymbol());
        payload.put("transaction_type", orderDraft.side().name());
        payload.put("variety", "regular");
        payload.put("product", orderDraft.product().name());
        payload.put("order_type", orderDraft.orderType().name());
        payload.put("quantity", orderDraft.qty());
        if (orderDraft.price() != null) {
            payload.put("price", orderDraft.price());
        }
        if (orderDraft.triggerPrice() != null) {
            payload.put("trigger_price", orderDraft.triggerPrice());
        }
        return payload;
    }

    private double readFirstDouble(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode child = node.path(field);
            if (child.isNumber()) {
                return child.asDouble();
            }
            if (child.isTextual()) {
                try {
                    return Double.parseDouble(child.asText());
                } catch (Exception ignored) {
                    // ignore parse errors and continue
                }
            }
        }
        return 0.0d;
    }
}
