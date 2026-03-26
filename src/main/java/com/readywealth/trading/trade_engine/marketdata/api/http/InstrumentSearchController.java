package com.readywealth.trading.trade_engine.marketdata.api.http;

import com.readywealth.trading.trade_engine.marketdata.application.InstrumentSearchService;
import com.readywealth.trading.trade_engine.marketdata.domain.InstrumentSearchResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/instruments")
public class InstrumentSearchController {

    private final InstrumentSearchService service;

    public InstrumentSearchController(InstrumentSearchService service) {
        this.service = service;
    }

    @GetMapping("/lookup")
    public List<InstrumentSearchResult> lookup(@RequestParam String symbol) {
        return service.lookupExact(symbol);
    }

    @GetMapping("/by-symbol")
    public ResponseEntity<InstrumentSearchResult> bySymbolAndExchange(
            @RequestParam String symbol,
            @RequestParam String exchange) {
        return ResponseEntity.of(service.lookupBySymbolAndExchange(symbol, exchange));
    }

    @GetMapping("/suggest")
    public List<InstrumentSearchResult> suggest(
            @RequestParam("q") String q,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return service.suggest(q, limit);
    }
}
