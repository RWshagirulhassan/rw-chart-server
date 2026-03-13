package com.readywealth.trading.trade_engine.engine.domain.module.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.readywealth.trading.trade_engine.engine.domain.module.Module;
import com.readywealth.trading.trade_engine.engine.domain.module.factory.DependencyResolver;
import com.readywealth.trading.trade_engine.engine.domain.module.factory.ModuleFactory;
import com.readywealth.trading.trade_engine.engine.domain.module.factory.ModuleFactoryRegistry;
import com.readywealth.trading.trade_engine.engine.domain.module.spec.ModuleSpec;
import com.readywealth.trading.trade_engine.engine.domain.series.BarSeriesContext;

class ModuleGraphBuilderTest {

    @Test
    void buildsTopologicalOrderForDependencies() {
        ModuleFactoryRegistry registry = new ModuleFactoryRegistry(List.of(new AFactory(), new BFactory()));
        ModuleGraph graph = new ModuleGraphBuilder().build(List.of(new BSpec()), registry);

        assertEquals(List.of(new ASpec(), new BSpec()), graph.executionOrder());
        assertEquals(2, graph.instances().size());
    }

    @Test
    void dedupesSameDependencyBySpecEquality() {
        ModuleFactoryRegistry registry = new ModuleFactoryRegistry(List.of(new AFactory(), new BFactory(), new CFactory()));
        ModuleGraph graph = new ModuleGraphBuilder().build(List.of(new BSpec(), new CSpec()), registry);

        assertEquals(List.of(new ASpec(), new BSpec(), new CSpec()), graph.executionOrder());
        assertEquals(3, graph.instances().size());
    }

    @Test
    void detectsCycle() {
        ModuleFactoryRegistry registry = new ModuleFactoryRegistry(List.of(new XFactory(), new YFactory()));

        assertThrows(IllegalStateException.class, () -> new ModuleGraphBuilder().build(List.of(new XSpec()), registry));
    }

    private record ASpec() implements ModuleSpec<AModule> {
    }

    private record BSpec() implements ModuleSpec<BModule> {
    }

    private record CSpec() implements ModuleSpec<CModule> {
    }

    private record XSpec() implements ModuleSpec<XModule> {
    }

    private record YSpec() implements ModuleSpec<YModule> {
    }

    private static final class AFactory implements ModuleFactory<AModule, ASpec> {
        @Override
        public Class<ASpec> specType() {
            return ASpec.class;
        }

        @Override
        public AModule create(ASpec spec, DependencyResolver resolver) {
            return new AModule();
        }
    }

    private static final class BFactory implements ModuleFactory<BModule, BSpec> {
        @Override
        public Class<BSpec> specType() {
            return BSpec.class;
        }

        @Override
        public List<ModuleSpec<?>> dependencies(BSpec spec) {
            return List.of(new ASpec());
        }

        @Override
        public BModule create(BSpec spec, DependencyResolver resolver) {
            return new BModule(resolver.require(new ASpec()));
        }
    }

    private static final class CFactory implements ModuleFactory<CModule, CSpec> {
        @Override
        public Class<CSpec> specType() {
            return CSpec.class;
        }

        @Override
        public List<ModuleSpec<?>> dependencies(CSpec spec) {
            return List.of(new ASpec());
        }

        @Override
        public CModule create(CSpec spec, DependencyResolver resolver) {
            return new CModule(resolver.require(new ASpec()));
        }
    }

    private static final class XFactory implements ModuleFactory<XModule, XSpec> {
        @Override
        public Class<XSpec> specType() {
            return XSpec.class;
        }

        @Override
        public List<ModuleSpec<?>> dependencies(XSpec spec) {
            return List.of(new YSpec());
        }

        @Override
        public XModule create(XSpec spec, DependencyResolver resolver) {
            return new XModule(resolver.require(new YSpec()));
        }
    }

    private static final class YFactory implements ModuleFactory<YModule, YSpec> {
        @Override
        public Class<YSpec> specType() {
            return YSpec.class;
        }

        @Override
        public List<ModuleSpec<?>> dependencies(YSpec spec) {
            return List.of(new XSpec());
        }

        @Override
        public YModule create(YSpec spec, DependencyResolver resolver) {
            return new YModule(resolver.require(new XSpec()));
        }
    }

    private static final class AModule implements Module {
        @Override
        public void onBar(BarSeriesContext ctx, int index) {
        }
    }

    private static final class BModule implements Module {
        @SuppressWarnings("unused")
        private final AModule dependency;

        private BModule(AModule dependency) {
            this.dependency = dependency;
        }

        @Override
        public void onBar(BarSeriesContext ctx, int index) {
        }
    }

    private static final class CModule implements Module {
        @SuppressWarnings("unused")
        private final AModule dependency;

        private CModule(AModule dependency) {
            this.dependency = dependency;
        }

        @Override
        public void onBar(BarSeriesContext ctx, int index) {
        }
    }

    private static final class XModule implements Module {
        @SuppressWarnings("unused")
        private final YModule dependency;

        private XModule(YModule dependency) {
            this.dependency = dependency;
        }

        @Override
        public void onBar(BarSeriesContext ctx, int index) {
        }
    }

    private static final class YModule implements Module {
        @SuppressWarnings("unused")
        private final XModule dependency;

        private YModule(XModule dependency) {
            this.dependency = dependency;
        }

        @Override
        public void onBar(BarSeriesContext ctx, int index) {
        }
    }
}
