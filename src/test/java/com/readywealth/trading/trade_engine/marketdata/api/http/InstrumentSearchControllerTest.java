package com.readywealth.trading.trade_engine.marketdata.api.http;

import com.readywealth.trading.trade_engine.marketdata.application.InstrumentSearchService;
import com.readywealth.trading.trade_engine.marketdata.domain.InstrumentSearchResult;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstrumentSearchControllerTest {

    @Test
    void bySymbolAndExchangeReturnsOkWhenInstrumentExists() {
        InstrumentSearchService service = Mockito.mock(InstrumentSearchService.class);
        InstrumentSearchResult instrument = new InstrumentSearchResult("408065", "Infosys Ltd", "INFY", "NSE");
        Mockito.when(service.lookupBySymbolAndExchange("INFY", "NSE"))
                .thenReturn(Optional.of(instrument));

        InstrumentSearchController controller = new InstrumentSearchController(service);

        ResponseEntity<InstrumentSearchResult> response = controller.bySymbolAndExchange("INFY", "NSE");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(instrument, response.getBody());
    }

    @Test
    void bySymbolAndExchangeReturnsNotFoundWhenInstrumentMissing() {
        InstrumentSearchService service = Mockito.mock(InstrumentSearchService.class);
        Mockito.when(service.lookupBySymbolAndExchange("INFY", "NSE"))
                .thenReturn(Optional.empty());

        InstrumentSearchController controller = new InstrumentSearchController(service);

        ResponseEntity<InstrumentSearchResult> response = controller.bySymbolAndExchange("INFY", "NSE");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
