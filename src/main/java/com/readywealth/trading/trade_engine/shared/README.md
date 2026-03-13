# Shared Context

## Purpose
Holds cross-context contracts and shared utility primitives.

## Allowed Layers
- Context-level shared contracts/utilities only.

## Key Entrypoints
- `shared/*` package-level contracts (when present).

## Forbidden Dependencies
- No feature orchestration logic.
- Avoid framework/vendor-specific dependencies in shared primitives.
