package com.readywealth.trading.trade_engine.marketdata.application;

import com.readywealth.trading.trade_engine.marketdata.domain.TypedTick;
import com.zerodhatech.models.Tick;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TickMapperTest {

    @Test
    void normalizesFractionalLtqUsingFloor() {
        Tick tick = Mockito.mock(Tick.class);
        Mockito.when(tick.isTradable()).thenReturn(true);
        Mockito.when(tick.getInstrumentToken()).thenReturn(256265L);
        Mockito.when(tick.getMode()).thenReturn("full");
        Mockito.when(tick.getLastTradedPrice()).thenReturn(123.45);
        Mockito.when(tick.getTickTimestamp()).thenReturn(new Date(1739863200000L));
        Mockito.when(tick.getVolumeTradedToday()).thenReturn(100L);
        Mockito.when(tick.getLastTradedQuantity()).thenReturn(12.9);

        TickMapper mapper = new TickMapper();
        TypedTick typed = mapper.map(tick);

        assertEquals(12, typed.tickQuote().getLastTradedQuantity());
    }

    @Test
    void keepsOverflowLtqAsNull() {
        Tick tick = Mockito.mock(Tick.class);
        Mockito.when(tick.isTradable()).thenReturn(true);
        Mockito.when(tick.getInstrumentToken()).thenReturn(256265L);
        Mockito.when(tick.getMode()).thenReturn("full");
        Mockito.when(tick.getLastTradedPrice()).thenReturn(123.45);
        Mockito.when(tick.getTickTimestamp()).thenReturn(new Date(1739863200000L));
        Mockito.when(tick.getLastTradedQuantity()).thenReturn((double) Integer.MAX_VALUE + 1000.0);

        TickMapper mapper = new TickMapper();
        TypedTick typed = mapper.map(tick);

        assertNull(typed.tickQuote().getLastTradedQuantity());
    }
}
