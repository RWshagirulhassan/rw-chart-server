# Module System Guide (Typed Specs + Factories)

This module system is designed for DRY compute-once behavior inside a single `BarSeriesContext`.

## Core idea
- Define module identity using typed specs (`record ...Spec(...)`), not string IDs.
- Build dependencies in factories (`dependencies(spec)`), not in runtime lookups.
- Create module instances once per `BarSeriesContext`.
- Run modules in topological order through `Context.ensureComputedUpTo(...)`.

## Main types
- `Module`: runtime logic (`onBar`, `onEvict`, `onInvalidateFrom`).
- `ModuleSpec<T extends Module>`: immutable typed identity + runtime arguments.
- `ModuleFactory<T,S>`: declares dependencies and constructs module with typed resolver.
- `StrategyModulePlan`: root specs for a series.
- `Context`: builds graph, caches instances, executes in dependency order.

## How to use modules in code
1. Build a plan with root specs:

```java
StrategyModulePlan plan = StrategyModulePlan.builder()
    .add(new BosChochSpec(20))
    .build();
```

2. Create `BarSeriesContext` with that plan:

```java
BarSeriesContext seriesCtx = new BarSeriesContext(
    key,
    series,
    numFactory,
    config,
    plan
);
```

3. Drive incremental computation:

```java
seriesCtx.context().ensureComputedUpTo(series.getEndIndex());
```

4. Query module outputs via typed `require`:

```java
BosChochModule bos = seriesCtx.context().require(new BosChochSpec(20));
List<BreakEvent> recent = bos.query(BreakQuery.all().limit(10));
```

## How dependencies work
Dependencies are declared in factory metadata, not in modules.

Example (`BosChochFactory`):
- `dependencies(new BosChochSpec(len)) -> [new SwingPivotsSpec(len)]`
- `create(...)` uses resolver: `resolver.require(new SwingPivotsSpec(len))`

This guarantees:
- compile-time type safety
- deterministic graph build
- single instance per spec per context

## Adding a new module (recommended pattern)
Assume new module `AtrBandModule` with argument `period` and dependency on `SwingPivotsModule(period)`.

1. Create spec record:

```java
public record AtrBandSpec(int period) implements ModuleSpec<AtrBandModule> {}
```

2. Implement module class:

```java
public final class AtrBandModule implements Module {
    private final int period;
    private final SwingPivotsModule swings;

    public AtrBandModule(int period, SwingPivotsModule swings) {
        this.period = period;
        this.swings = swings;
    }

    @Override
    public void onBar(BarSeriesContext ctx, int index) {
        // compute once for this index; store state inside this module
    }

    @Override
    public void onInvalidateFrom(BarSeriesContext ctx, int index) {
        // remove cached state from index onward
    }
}
```

3. Implement factory:

```java
public final class AtrBandFactory implements ModuleFactory<AtrBandModule, AtrBandSpec> {
    @Override
    public Class<AtrBandSpec> specType() { return AtrBandSpec.class; }

    @Override
    public List<ModuleSpec<?>> dependencies(AtrBandSpec spec) {
        return List.of(new SwingPivotsSpec(spec.period()));
    }

    @Override
    public AtrBandModule create(AtrBandSpec spec, DependencyResolver resolver) {
        SwingPivotsModule swings = resolver.require(new SwingPivotsSpec(spec.period()));
        return new AtrBandModule(spec.period(), swings);
    }
}
```

4. Register factory in `ModuleFactoryRegistry` default list.

5. Add unit tests:
- graph order
- dedup behavior (`new AtrBandSpec(14)` reused)
- lifecycle behavior (`onEvict`, `onInvalidateFrom`)

## Practices to keep in mind
1. Keep specs immutable Java records.
2. Keep module constructors explicit and typed.
3. Do not access dependencies through IDs or string keys.
4. Avoid side effects in `onBar`; update only module-owned state.
5. Make `onInvalidateFrom` deterministic and idempotent.
6. Handle eviction (`onEvict`) to prevent stale memory growth.
7. Prefer append-only + prune data structures for speed.
8. Use context `require(spec)` only for querying already-built modules.
9. Keep one module instance per spec per `BarSeriesContext`; never share mutable module instances globally.
10. Keep logs concise; avoid per-bar noisy logs in production.

## Anti-patterns (avoid)
- `Map<String, Object>` args for modules.
- `requireById("...")` style lookups.
- Mutable spec objects used as map keys.
- Recomputing the same module from multiple strategy layers.
- Cross-context sharing of stateful module instances.

## Lifecycle reminders
- `ensureComputedUpTo(end)` computes only missing indexes.
- `invalidateFrom(i)` must clear state from `i` onward and force recompute.
- `onEvict(begin)` must drop data older than new begin index.

## Future extensions
- Add Spring auto-registration for `ModuleFactory` beans into `ModuleFactoryRegistry`.
- Add optional graph visualization/debug dump per strategy plan.
- Add module-level metrics (compute latency, cache size, invalidation count).
