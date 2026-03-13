# Session Context

## Purpose
Owns user chart sessions, series ownership, and session lifecycle orchestration.

## Allowed Layers
- `api/http`
- `application`
- `application/port`
- `domain`
- `infrastructure`

## Key Entrypoints
- `api/http/UiSessionController`
- `application/SessionSeriesCoordinator`
- `application/SessionIdentityService`
- `application/StressSessionStartupService`
- `infrastructure/memory/InMemoryConcurrentBarSeriesRegistry`

## Forbidden Dependencies
- Keep session HTTP mapping in `api`.
- Keep storage/framework adapters in `infrastructure`.
- Keep domain state objects framework-free.
