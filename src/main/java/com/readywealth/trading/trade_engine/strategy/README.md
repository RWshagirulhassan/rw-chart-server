# Strategy Context

## Purpose
Owns strategy-facing orchestration adapters and runtime snapshot integration points.

## Allowed Layers
- `application`
- `application/port`
- `domain`
- `infrastructure`

## Key Entrypoints
- `application/SeriesSnapshotAdapter`
- `application/port/SeriesSnapshotPort`

## Forbidden Dependencies
- Keep transport controllers out unless strategy API is intentionally introduced.
- Keep business state abstractions in `domain`.
