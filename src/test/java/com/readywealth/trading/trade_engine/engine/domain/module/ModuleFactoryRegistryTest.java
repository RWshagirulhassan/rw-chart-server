package com.readywealth.trading.trade_engine.engine.domain.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.readywealth.trading.trade_engine.engine.domain.module.factory.DependencyResolver;
import com.readywealth.trading.trade_engine.engine.domain.module.factory.ModuleFactory;
import com.readywealth.trading.trade_engine.engine.domain.module.factory.ModuleFactoryRegistry;
import com.readywealth.trading.trade_engine.engine.domain.module.spec.ModuleSpec;
import com.readywealth.trading.trade_engine.engine.domain.series.BarSeriesContext;

class ModuleFactoryRegistryTest {

    @Test
    void resolvesFactoryBySpecType() {
        ModuleFactoryRegistry registry = new ModuleFactoryRegistry(List.of(new AlphaFactory()));
        assertEquals(AlphaFactory.class, registry.getFactory(new AlphaSpec()).getClass());
    }

    @Test
    void failsOnDuplicateFactory() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new ModuleFactoryRegistry(List.of(new AlphaFactory(), new AlphaFactory())));
        assertTrue(ex.getMessage().contains("Duplicate ModuleFactory"));
    }

    @Test
    void failsWhenSpecHasNoFactory() {
        ModuleFactoryRegistry registry = new ModuleFactoryRegistry(List.of(new AlphaFactory()));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registry.getFactory(new MissingSpec()));
        assertTrue(ex.getMessage().contains("No ModuleFactory"));
    }

    private record AlphaSpec() implements ModuleSpec<AlphaModule> {
    }

    private record MissingSpec() implements ModuleSpec<MissingModule> {
    }

    private static final class AlphaFactory implements ModuleFactory<AlphaModule, AlphaSpec> {
        @Override
        public Class<AlphaSpec> specType() {
            return AlphaSpec.class;
        }

        @Override
        public AlphaModule create(AlphaSpec spec, DependencyResolver resolver) {
            return new AlphaModule();
        }
    }

    private static final class AlphaModule implements Module {
        @Override
        public void onBar(BarSeriesContext ctx, int index) {
        }
    }

    private static final class MissingModule implements Module {
        @Override
        public void onBar(BarSeriesContext ctx, int index) {
        }
    }
}
