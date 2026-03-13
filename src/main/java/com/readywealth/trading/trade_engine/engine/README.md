# Engine Context

## Purpose
Owns chart-session runtime orchestration used by live and backtest progression.

## Allowed Layers
- `api/http`
- `application`
- `domain`
- `infrastructure`

## Key Entrypoints
- `api/http/EngineRuntimeController`
- `application/runtime/SeriesRuntimeManager`
- `application/runtime/ChartSessionRuntime`
- `domain/module/*`
- `domain/script/*`
- `domain/series/*`

## Forbidden Dependencies
- Do not introduce root-level legacy folders (`controller`, `runtime`, `aggregation`).
- Keep non-domain framework adapters outside `domain`.
- Keep module package names lowercase (for example `boschoch`, `swingpivots`).
