package com.readywealth.trading.trade_engine.marketdata.application;

import com.readywealth.trading.trade_engine.marketdata.api.ws.TicksWebSocketBroadcaster;
import com.readywealth.trading.trade_engine.marketdata.infrastructure.config.PublisherProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TickDispatcherTest {

    @Test
    void enqueuePreservesInterruptStatusWhenPutIsInterrupted() {
        PublisherProperties properties = new PublisherProperties();
        TickMapper mapper = Mockito.mock(TickMapper.class);
        TicksWebSocketBroadcaster broadcaster = Mockito.mock(TicksWebSocketBroadcaster.class);
        TickToSeriesIngestService ingestService = Mockito.mock(TickToSeriesIngestService.class);

        TickDispatcher dispatcher = new TickDispatcher(
                properties,
                mapper,
                broadcaster,
                ingestService,
                new SimpleMeterRegistry());

        try {
            Thread.currentThread().interrupt();
            dispatcher.enqueue(new ArrayList<>(java.util.List.of(Mockito.mock(com.zerodhatech.models.Tick.class))));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
