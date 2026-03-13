package com.readywealth.trading.trade_engine.execution.api.ws;

import com.readywealth.trading.trade_engine.execution.application.port.TradingEventPublisherPort;
import com.readywealth.trading.trade_engine.execution.application.service.OrderTimelineEventService;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventEnvelope;
import com.readywealth.trading.trade_engine.execution.domain.TradingEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

@Component
public class TradingWebSocketBroadcaster implements TradingEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(TradingWebSocketBroadcaster.class);

    private final TradingWebSocketHandler tradingWebSocketHandler;
    private final ObjectMapper objectMapper;
    private final OrderTimelineEventService orderTimelineEventService;

    public TradingWebSocketBroadcaster(
            TradingWebSocketHandler tradingWebSocketHandler,
            ObjectMapper objectMapper,
            OrderTimelineEventService orderTimelineEventService) {
        this.tradingWebSocketHandler = tradingWebSocketHandler;
        this.objectMapper = objectMapper;
        this.orderTimelineEventService = orderTimelineEventService;
    }

    @Override
    public void publish(TradingEventEnvelope envelope) {
        try {
            broadcastEnvelope(envelope);
            orderTimelineEventService.toTimelineUpdate(envelope).ifPresent(update -> {
                TradingEventEnvelope timelineEnvelope = new TradingEventEnvelope(
                        UUID.randomUUID().toString(),
                        envelope.accountId(),
                        TradingEventType.ORDER_TIMELINE_UPDATED,
                        System.currentTimeMillis(),
                        envelope.source(),
                        update);
                broadcastEnvelope(timelineEnvelope);
            });
        } catch (Exception ex) {
            log.warn("trading_ws_publish_failed accountId={} reason='{}'", envelope.accountId(), ex.getMessage());
        }
    }

    private void broadcastEnvelope(TradingEventEnvelope envelope) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "type", "trading_event",
                    "ts", System.currentTimeMillis(),
                    "data", envelope));
            tradingWebSocketHandler.broadcastToAccount(envelope.accountId(), payload);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
