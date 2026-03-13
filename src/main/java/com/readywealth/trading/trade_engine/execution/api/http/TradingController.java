package com.readywealth.trading.trade_engine.execution.api.http;

import com.readywealth.trading.trade_engine.execution.api.http.dto.BasketRequest;
import com.readywealth.trading.trade_engine.execution.api.http.dto.ChargesResponse;
import com.readywealth.trading.trade_engine.execution.api.http.dto.CreateExitPlanRequest;
import com.readywealth.trading.trade_engine.execution.api.http.dto.CreateTradingAccountRequest;
import com.readywealth.trading.trade_engine.execution.api.http.dto.ExitPlanResponse;
import com.readywealth.trading.trade_engine.execution.api.http.dto.FundsResponse;
import com.readywealth.trading.trade_engine.execution.api.http.dto.LinkedExitSummaryResponse;
import com.readywealth.trading.trade_engine.execution.api.http.dto.MarginCheckResponse;
import com.readywealth.trading.trade_engine.execution.api.http.dto.ModifyOrderRequest;
import com.readywealth.trading.trade_engine.execution.api.http.dto.OrderDraftRequest;
import com.readywealth.trading.trade_engine.execution.api.http.dto.OrderAttachmentsRequest;
import com.readywealth.trading.trade_engine.execution.api.http.dto.OrderResponse;
import com.readywealth.trading.trade_engine.execution.api.http.dto.OrderTimelineRowResponse;
import com.readywealth.trading.trade_engine.execution.api.http.dto.PlaceOrderRequest;
import com.readywealth.trading.trade_engine.execution.api.http.dto.PlaceOrderResponse;
import com.readywealth.trading.trade_engine.execution.api.http.dto.PositionResponse;
import com.readywealth.trading.trade_engine.execution.api.http.dto.StrategySettingsResponse;
import com.readywealth.trading.trade_engine.execution.api.http.dto.TriggerResponse;
import com.readywealth.trading.trade_engine.execution.api.http.dto.TradingAccountResponse;
import com.readywealth.trading.trade_engine.execution.api.http.dto.UpdateExitPlanRequest;
import com.readywealth.trading.trade_engine.execution.api.http.dto.UpdateStrategySettingsRequest;
import com.readywealth.trading.trade_engine.execution.application.port.InstrumentMetadataPort;
import com.readywealth.trading.trade_engine.execution.application.service.ExitPlanManagementService;
import com.readywealth.trading.trade_engine.execution.application.service.ExitPlanQueryService;
import com.readywealth.trading.trade_engine.execution.application.service.LinkedOrderOrchestratorService;
import com.readywealth.trading.trade_engine.execution.application.service.MarginGatewayService;
import com.readywealth.trading.trade_engine.execution.application.service.OrderTimelineQueryService;
import com.readywealth.trading.trade_engine.execution.application.service.OrderQueryService;
import com.readywealth.trading.trade_engine.execution.application.service.StrategySettingsService;
import com.readywealth.trading.trade_engine.execution.application.service.TradeOrchestratorService;
import com.readywealth.trading.trade_engine.execution.application.service.TriggerQueryService;
import com.readywealth.trading.trade_engine.execution.application.service.TradingAccountService;
import com.readywealth.trading.trade_engine.execution.domain.ArmPolicy;
import com.readywealth.trading.trade_engine.execution.domain.BrokerType;
import com.readywealth.trading.trade_engine.execution.domain.ExitLegSpec;
import com.readywealth.trading.trade_engine.execution.domain.ExitLegType;
import com.readywealth.trading.trade_engine.execution.domain.ExitPlanStatus;
import com.readywealth.trading.trade_engine.execution.domain.OrderAttachments;
import com.readywealth.trading.trade_engine.execution.domain.OrderDraft;
import com.readywealth.trading.trade_engine.execution.domain.TradingAccount;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventSource;
import com.readywealth.trading.trade_engine.execution.domain.TradingMode;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderStatus;
import com.readywealth.trading.trade_engine.execution.domain.TradingOrderType;
import com.readywealth.trading.trade_engine.execution.domain.TradingProduct;
import com.readywealth.trading.trade_engine.execution.domain.TradingSide;
import com.readywealth.trading.trade_engine.execution.domain.TriggerStatus;
import com.readywealth.trading.trade_engine.session.application.SessionIdentityService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/v1/trading")
public class TradingController {

    private final SessionIdentityService sessionIdentityService;
    private final TradingAccountService tradingAccountService;
    private final StrategySettingsService strategySettingsService;
    private final TradeOrchestratorService tradeOrchestratorService;
    private final LinkedOrderOrchestratorService linkedOrderOrchestratorService;
    private final OrderQueryService orderQueryService;
    private final OrderTimelineQueryService orderTimelineQueryService;
    private final ExitPlanQueryService exitPlanQueryService;
    private final TriggerQueryService triggerQueryService;
    private final ExitPlanManagementService exitPlanManagementService;
    private final MarginGatewayService marginGatewayService;
    private final InstrumentMetadataPort instrumentMetadataPort;

    public TradingController(
            SessionIdentityService sessionIdentityService,
            TradingAccountService tradingAccountService,
            StrategySettingsService strategySettingsService,
            TradeOrchestratorService tradeOrchestratorService,
            LinkedOrderOrchestratorService linkedOrderOrchestratorService,
            OrderQueryService orderQueryService,
            OrderTimelineQueryService orderTimelineQueryService,
            ExitPlanQueryService exitPlanQueryService,
            TriggerQueryService triggerQueryService,
            ExitPlanManagementService exitPlanManagementService,
            MarginGatewayService marginGatewayService,
            InstrumentMetadataPort instrumentMetadataPort) {
        this.sessionIdentityService = sessionIdentityService;
        this.tradingAccountService = tradingAccountService;
        this.strategySettingsService = strategySettingsService;
        this.tradeOrchestratorService = tradeOrchestratorService;
        this.linkedOrderOrchestratorService = linkedOrderOrchestratorService;
        this.orderQueryService = orderQueryService;
        this.orderTimelineQueryService = orderTimelineQueryService;
        this.exitPlanQueryService = exitPlanQueryService;
        this.triggerQueryService = triggerQueryService;
        this.exitPlanManagementService = exitPlanManagementService;
        this.marginGatewayService = marginGatewayService;
        this.instrumentMetadataPort = instrumentMetadataPort;
    }

    @PostMapping("/accounts")
    public TradingAccountResponse createOrUpdateAccount(@RequestBody(required = false) CreateTradingAccountRequest request) {
        String userId = sessionIdentityService.requireCurrentUserId();
        TradingMode mode = parseTradingMode(request == null ? null : request.mode());
        BrokerType brokerType = parseBrokerType(request == null ? null : request.brokerType());
        boolean autoTradeEnabled = request != null && Boolean.TRUE.equals(request.autoTradeEnabled());
        Double openingBalance = request == null ? null : request.openingBalance();
        TradingAccount account = tradingAccountService.createOrUpdate(
                userId,
                mode,
                brokerType,
                autoTradeEnabled,
                openingBalance);
        return TradingAccountResponse.from(account);
    }

    @GetMapping("/accounts/current")
    public TradingAccountResponse currentAccount() {
        String userId = sessionIdentityService.requireCurrentUserId();
        TradingAccount account = tradingAccountService.findCurrent(userId)
                .orElseThrow(() -> new IllegalArgumentException("No trading account linked to current user"));
        return TradingAccountResponse.from(account);
    }

    @PutMapping("/{accountId}/strategies/{scriptId}/settings")
    public StrategySettingsResponse upsertStrategySettings(
            @PathVariable String accountId,
            @PathVariable String scriptId,
            @RequestBody UpdateStrategySettingsRequest request) {
        TradingAccount account = requireOwnedAccount(accountId);
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        StrategySettingsResponse response = StrategySettingsResponse.from(strategySettingsService.upsert(
                account.accountId(),
                scriptId,
                Boolean.TRUE.equals(request.enabled()),
                request.fixedQty() == null ? 1 : request.fixedQty(),
                parseProduct(request.product())));
        return response;
    }

    @GetMapping("/{accountId}/strategies/settings")
    public List<StrategySettingsResponse> listStrategySettings(@PathVariable String accountId) {
        TradingAccount account = requireOwnedAccount(accountId);
        return strategySettingsService.list(account.accountId()).stream().map(StrategySettingsResponse::from).toList();
    }

    @PostMapping("/{accountId}/orders")
    public ResponseEntity<PlaceOrderResponse> placeOrder(
            @PathVariable String accountId,
            @RequestBody PlaceOrderRequest request) {
        TradingAccount account = requireOwnedAccount(accountId);
        OrderDraft draft = toOrderDraft(account.accountId(), request);
        OrderAttachments attachments = toOrderAttachments(draft, request.attachments());
        var result = linkedOrderOrchestratorService.placeOrderWithLinkedExit(account, draft, attachments);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new PlaceOrderResponse(
                        OrderResponse.from(result.order()),
                        LinkedExitSummaryResponse.from(result.linkedExit())));
    }

    @PostMapping("/{accountId}/orders/{orderId}/cancel")
    public OrderResponse cancelOrder(
            @PathVariable String accountId,
            @PathVariable String orderId,
            @RequestParam(required = false) String reason) {
        TradingAccount account = requireOwnedAccount(accountId);
        return OrderResponse.from(tradeOrchestratorService.cancelOrder(account, orderId, reason));
    }

    @PostMapping("/{accountId}/orders/{orderId}/modify")
    public OrderResponse modifyOrder(
            @PathVariable String accountId,
            @PathVariable String orderId,
            @RequestBody ModifyOrderRequest request) {
        TradingAccount account = requireOwnedAccount(accountId);
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        return OrderResponse.from(tradeOrchestratorService.modifyOrder(
                account,
                orderId,
                request.qty(),
                request.price(),
                request.triggerPrice()));
    }

    @GetMapping("/{accountId}/orders")
    public List<OrderResponse> orders(
            @PathVariable String accountId,
            @RequestParam(required = false) String status) {
        TradingAccount account = requireOwnedAccount(accountId);
        TradingOrderStatus orderStatus = status == null || status.isBlank() ? null
                : TradingOrderStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        return orderQueryService.orders(account.accountId(), orderStatus).stream().map(OrderResponse::from).toList();
    }

    @GetMapping("/{accountId}/orders/timeline")
    public List<OrderTimelineRowResponse> orderTimeline(
            @PathVariable String accountId,
            @RequestParam(defaultValue = "true") boolean includeTerminal,
            @RequestParam(defaultValue = "500") Integer limit) {
        TradingAccount account = requireOwnedAccount(accountId);
        int safeLimit = limit == null ? 500 : Math.max(1, Math.min(1000, limit));
        return orderTimelineQueryService.timeline(account.accountId(), includeTerminal, safeLimit).stream()
                .map(OrderTimelineRowResponse::from)
                .toList();
    }

    @GetMapping("/{accountId}/positions")
    public List<PositionResponse> positions(@PathVariable String accountId) {
        TradingAccount account = requireOwnedAccount(accountId);
        return orderQueryService.positions(account.accountId()).stream().map(PositionResponse::from).toList();
    }

    @GetMapping("/{accountId}/exit-plans")
    public List<ExitPlanResponse> exitPlans(
            @PathVariable String accountId,
            @RequestParam(required = false) String status) {
        TradingAccount account = requireOwnedAccount(accountId);
        ExitPlanStatus planStatus = status == null || status.isBlank()
                ? null
                : ExitPlanStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        return exitPlanQueryService.list(account.accountId(), planStatus).stream().map(ExitPlanResponse::from).toList();
    }

    @GetMapping("/{accountId}/triggers")
    public List<TriggerResponse> triggers(
            @PathVariable String accountId,
            @RequestParam(required = false) String status) {
        TradingAccount account = requireOwnedAccount(accountId);
        TriggerStatus triggerStatus = status == null || status.isBlank()
                ? null
                : TriggerStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        return triggerQueryService.triggers(account.accountId(), triggerStatus).stream().map(TriggerResponse::from).toList();
    }

    @PostMapping("/{accountId}/positions/{instrumentToken}/exit-plan")
    public ExitPlanResponse createExitPlanForPosition(
            @PathVariable String accountId,
            @PathVariable Long instrumentToken,
            @RequestBody CreateExitPlanRequest request) {
        TradingAccount account = requireOwnedAccount(accountId);
        if (instrumentToken == null || instrumentToken <= 0) {
            throw new IllegalArgumentException("instrumentToken must be positive");
        }
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        var created = exitPlanManagementService.createForPosition(
                account,
                instrumentToken,
                parseProduct(request.product()),
                parseArmPolicy(request.armPolicy()),
                enabledTrigger(request.takeProfit()),
                enabledOffset(request.takeProfit()),
                enabledTrigger(request.stopLoss()),
                enabledOffset(request.stopLoss()));
        return ExitPlanResponse.from(created);
    }

    @PutMapping("/{accountId}/exit-plans/{exitPlanId}")
    public ExitPlanResponse updateExitPlan(
            @PathVariable String accountId,
            @PathVariable String exitPlanId,
            @RequestBody UpdateExitPlanRequest request) {
        TradingAccount account = requireOwnedAccount(accountId);
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        var updated = exitPlanManagementService.updatePlan(
                account,
                exitPlanId,
                enabledTrigger(request.takeProfit()),
                enabledOffset(request.takeProfit()),
                enabledTrigger(request.stopLoss()),
                enabledOffset(request.stopLoss()));
        return ExitPlanResponse.from(updated);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{accountId}/exit-plans/{exitPlanId}")
    public ExitPlanResponse cancelExitPlan(
            @PathVariable String accountId,
            @PathVariable String exitPlanId,
            @RequestParam(required = false) String reason) {
        TradingAccount account = requireOwnedAccount(accountId);
        return ExitPlanResponse.from(exitPlanManagementService.cancelPlan(account, exitPlanId, reason));
    }

    @GetMapping("/{accountId}/funds")
    public FundsResponse funds(@PathVariable String accountId) {
        TradingAccount account = requireOwnedAccount(accountId);
        return FundsResponse.from(orderQueryService.funds(account.accountId()));
    }

    @PostMapping("/{accountId}/margins/check")
    public MarginCheckResponse checkMargins(
            @PathVariable String accountId,
            @RequestBody BasketRequest request) {
        TradingAccount account = requireOwnedAccount(accountId);
        List<OrderDraft> basket = toBasket(account.accountId(), request);
        return MarginCheckResponse.from(marginGatewayService.checkMargins(account, basket));
    }

    @PostMapping("/{accountId}/charges/estimate")
    public ChargesResponse estimateCharges(
            @PathVariable String accountId,
            @RequestBody BasketRequest request) {
        TradingAccount account = requireOwnedAccount(accountId);
        List<OrderDraft> basket = toBasket(account.accountId(), request);
        return ChargesResponse.from(marginGatewayService.estimateCharges(account, basket));
    }

    private TradingAccount requireOwnedAccount(String accountId) {
        String userId = sessionIdentityService.requireCurrentUserId();
        return tradingAccountService.requireOwnedAccount(accountId, userId);
    }

    private List<OrderDraft> toBasket(String accountId, BasketRequest request) {
        if (request == null || request.orders() == null || request.orders().isEmpty()) {
            throw new IllegalArgumentException("orders basket is required");
        }
        return request.orders().stream().map(item -> toOrderDraft(accountId, item)).toList();
    }

    private OrderDraft toOrderDraft(String accountId, PlaceOrderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        return baseOrderDraft(
                accountId,
                request.instrumentToken(),
                request.tradingsymbol(),
                request.exchange(),
                request.product(),
                request.orderType(),
                request.side(),
                request.qty(),
                request.price(),
                request.triggerPrice(),
                request.reason());
    }

    private OrderAttachments toOrderAttachments(OrderDraft draft, OrderAttachmentsRequest request) {
        if (request == null) {
            return null;
        }
        ExitLegSpec tp = toExitLegSpec(request.takeProfit(), ExitLegType.TAKE_PROFIT, draft.side(), draft.qty());
        ExitLegSpec sl = toExitLegSpec(request.stopLoss(), ExitLegType.STOP_LOSS, draft.side(), draft.qty());
        if (tp == null && sl == null) {
            return null;
        }
        return new OrderAttachments(
                parseArmPolicy(request.armPolicy()),
                tp,
                sl);
    }

    private OrderDraft toOrderDraft(String accountId, OrderDraftRequest request) {
        return baseOrderDraft(
                accountId,
                request.instrumentToken(),
                request.tradingsymbol(),
                request.exchange(),
                request.product(),
                request.orderType(),
                request.side(),
                request.qty(),
                request.price(),
                request.triggerPrice(),
                request.reason());
    }

    private OrderDraft baseOrderDraft(
            String accountId,
            Long instrumentToken,
            String tradingsymbol,
            String exchange,
            String product,
            String orderType,
            String side,
            Integer qty,
            Double price,
            Double triggerPrice,
            String reason) {
        if (instrumentToken == null || instrumentToken <= 0) {
            throw new IllegalArgumentException("instrumentToken must be positive");
        }
        if (qty == null || qty <= 0) {
            throw new IllegalArgumentException("qty must be positive");
        }

        InstrumentMetadataPort.InstrumentMeta meta = instrumentMetadataPort.resolve(instrumentToken)
                .orElseGet(() -> new InstrumentMetadataPort.InstrumentMeta(instrumentToken, tradingsymbol, exchange));
        String resolvedExchange = exchange == null || exchange.isBlank() ? meta.exchange() : exchange;
        if (resolvedExchange == null || resolvedExchange.isBlank()) {
            throw new IllegalArgumentException("exchange is required");
        }
        String resolvedSymbol = tradingsymbol == null || tradingsymbol.isBlank() ? meta.tradingsymbol() : tradingsymbol;

        return new OrderDraft(
                accountId,
                instrumentToken,
                resolvedSymbol,
                resolvedExchange,
                parseProduct(product),
                parseOrderType(orderType),
                parseSide(side),
                qty,
                price,
                triggerPrice,
                TradingEventSource.CORE,
                null,
                reason == null ? "manual" : reason);
    }

    private ExitLegSpec toExitLegSpec(
            com.readywealth.trading.trade_engine.execution.api.http.dto.ExitLegRequest request,
            ExitLegType type,
            TradingSide entrySide,
            int qty) {
        if (request == null || !Boolean.TRUE.equals(request.enabled())) {
            return null;
        }
        if (request.triggerPrice() == null || request.triggerPrice() <= 0) {
            throw new IllegalArgumentException(type + " triggerPrice must be positive");
        }
        double offset = request.limitOffset() == null ? 0.0d : request.limitOffset();
        if (offset < 0) {
            throw new IllegalArgumentException(type + " limitOffset must be >= 0");
        }
        TradingSide exitSide = entrySide == TradingSide.BUY ? TradingSide.SELL : TradingSide.BUY;
        return new ExitLegSpec(type, request.triggerPrice(), offset, exitSide, qty);
    }

    private Double enabledTrigger(com.readywealth.trading.trade_engine.execution.api.http.dto.ExitLegRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.enabled())) {
            return null;
        }
        return request.triggerPrice();
    }

    private Double enabledOffset(com.readywealth.trading.trade_engine.execution.api.http.dto.ExitLegRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.enabled())) {
            return null;
        }
        return request.limitOffset();
    }

    private TradingMode parseTradingMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return TradingMode.PAPER;
        }
        return TradingMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }

    private BrokerType parseBrokerType(String raw) {
        if (raw == null || raw.isBlank()) {
            return BrokerType.PAPER;
        }
        return BrokerType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }

    private TradingProduct parseProduct(String raw) {
        if (raw == null || raw.isBlank()) {
            return TradingProduct.MIS;
        }
        return TradingProduct.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }

    private TradingOrderType parseOrderType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("orderType is required");
        }
        return TradingOrderType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }

    private TradingSide parseSide(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("side is required");
        }
        return TradingSide.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }

    private ArmPolicy parseArmPolicy(String raw) {
        if (raw == null || raw.isBlank()) {
            return ArmPolicy.AFTER_ENTRY_FILLED;
        }
        return ArmPolicy.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
