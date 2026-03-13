package com.readywealth.trading.trade_engine.engine.api.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.readywealth.trading.trade_engine.engine.api.http.dto.ScriptCatalogDetailsItem;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptCatalog;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptDefinition;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptId;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptKind;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptParamsBinder;
import com.readywealth.trading.trade_engine.engine.domain.script.ScriptRuntime;

class EngineRuntimeControllerCatalogDetailsTest {

    private static final ScriptDefinition INDICATOR_ALPHA = new TestScriptDefinition(
            "sample.indicator.alpha",
            "Alpha Indicator",
            ScriptKind.INDICATOR);
    private static final ScriptDefinition STRATEGY_BETA = new TestScriptDefinition(
            "sample.strategy.beta",
            "Beta Strategy",
            ScriptKind.STRATEGY);
    private static final ScriptDefinition INDICATOR_GAMMA = new TestScriptDefinition(
            "sample.indicator.gamma",
            "Gamma Indicator",
            ScriptKind.INDICATOR);

    private final EngineRuntimeController controller = new EngineRuntimeController(
            null,
            new ScriptCatalog(List.of(INDICATOR_GAMMA, STRATEGY_BETA, INDICATOR_ALPHA)));

    @Test
    void catalogDetailsReturnsSortedItemsByNameThenScriptId() {
        List<ScriptCatalogDetailsItem> items = controller.catalogDetails(null, null, 100, 0);

        assertEquals(3, items.size());
        assertEquals("Alpha Indicator", items.get(0).name());
        assertEquals("Beta Strategy", items.get(1).name());
        assertEquals("Gamma Indicator", items.get(2).name());
    }

    @Test
    void catalogDetailsFiltersByKind() {
        List<ScriptCatalogDetailsItem> items = controller.catalogDetails("INDICATOR", null, 100, 0);

        assertEquals(2, items.size());
        assertEquals(List.of("INDICATOR", "INDICATOR"), items.stream().map(ScriptCatalogDetailsItem::kind).toList());
    }

    @Test
    void catalogDetailsFiltersByQueryOnNameAndScriptId() {
        List<ScriptCatalogDetailsItem> byName = controller.catalogDetails(null, "strategy", 100, 0);
        List<ScriptCatalogDetailsItem> byId = controller.catalogDetails(null, "sample.indicator.gamma", 100, 0);

        assertEquals(1, byName.size());
        assertEquals("sample.strategy.beta", byName.get(0).scriptId());
        assertEquals(1, byId.size());
        assertEquals("sample.indicator.gamma", byId.get(0).scriptId());
    }

    @Test
    void catalogDetailsSupportsCombinedFilterAndPagination() {
        List<ScriptCatalogDetailsItem> combined = controller.catalogDetails("INDICATOR", "indicator", 100, 0);
        List<ScriptCatalogDetailsItem> paged = controller.catalogDetails(null, null, 1, 1);

        assertEquals(2, combined.size());
        assertEquals(1, paged.size());
        assertEquals("sample.strategy.beta", paged.get(0).scriptId());
    }

    @Test
    void catalogDetailsRejectsInvalidInputs() {
        assertThrows(ResponseStatusException.class, () -> controller.catalogDetails("invalid", null, 100, 0));
        assertThrows(ResponseStatusException.class, () -> controller.catalogDetails(null, null, 0, 0));
        assertThrows(ResponseStatusException.class, () -> controller.catalogDetails(null, null, 501, 0));
        assertThrows(ResponseStatusException.class, () -> controller.catalogDetails(null, null, 100, -1));
    }

    private static final class TestScriptDefinition implements ScriptDefinition {
        private final ScriptId id;
        private final String name;
        private final ScriptKind kind;

        private TestScriptDefinition(String id, String name, ScriptKind kind) {
            this.id = new ScriptId(id);
            this.name = name;
            this.kind = kind;
        }

        @Override
        public ScriptId id() {
            return id;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public ScriptKind kind() {
            return kind;
        }

        @Override
        public ScriptParamsBinder<?> paramsBinder() {
            return raw -> null;
        }

        @Override
        public ScriptRuntime createRuntime(
                com.readywealth.trading.trade_engine.engine.domain.script.ScriptInitContext init,
                Object typedParams) {
            return (ctx, index) -> {
            };
        }
    }
}
