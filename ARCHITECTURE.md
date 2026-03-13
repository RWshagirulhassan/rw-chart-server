# Trade-Engine Architecture

## Goal
Keep backend structure predictable for new contributors while preserving runtime behavior and external API contracts.

## Canonical Context Template
Each business context follows this structure:

```
<context>/
  api/http            # optional
  api/ws              # optional
  application
  application/port    # optional
  domain
  infrastructure
  README.md
  package-info.java
```

Rules:
- Directory and package names are lowercase.
- Do not use root-level legacy folders like `controller`, `service`, `model`, `store`, `config`, `websocket`, `ticks`.
- Cross-context integration happens through ports/contracts; avoid infrastructure-to-infrastructure coupling.

## Structure Inventory (Current)
Business contexts:
- `alerts`: `application`, `domain`, `infrastructure`
- `auth`: `api/http`, `application`, `domain`, `infrastructure`
- `engine`: `api/http`, `application/runtime`, `domain`, `infrastructure`
- `execution`: `api/http`, `api/ws`, `application`, `application/port`, `domain`, `infrastructure`
- `marketdata`: `api/http`, `api/ws`, `application`, `application/port`, `domain`, `infrastructure`
- `session`: `api/http`, `application`, `application/port`, `domain`, `infrastructure`
- `strategy`: `application`, `application/port`, `domain`, `infrastructure`

Shared/support contexts:
- `platform`: cross-cutting Spring/web configuration and exception mapping.
- `shared`: cross-context contracts and utility primitives.

## Dependency Rules
1. `domain` must not import Spring, servlet/websocket, Redis, broker SDK, or infrastructure classes.
2. `application` coordinates use cases and depends on `domain` plus `application/port`.
3. `infrastructure` implements `application/port` contracts and adapter logic.
4. `api` maps transport payloads to application commands/queries.
5. Keep external REST/WS endpoints stable during internal refactors.

## API Compatibility
- Existing REST/WS routes remain stable during structure refactor.
- Introduce versioned endpoints only when payload contracts must change.

## Bootstrap Pipeline (Current)
- Session creation triggers async bootstrap for each `SeriesKey`.
- Pipeline:
  1. Start `RUNNING` with timeframe-aligned cutoff.
  2. Seed historical candles up to cutoff.
  3. Replay buffered live ticks after cutoff.
  4. Complete with `COMPLETED` or `DEGRADED`.

## Refactor Policy
- Refactor in small context slices.
- Run compile + focused tests + architecture guardrails after each slice.
- Use deprecated compatibility bridges only when absolutely required.
