# Platform Context

## Purpose
Owns cross-cutting Spring platform wiring shared across contexts.

## Allowed Layers
- `config`
- `web`

## Key Entrypoints
- `config/JacksonConfig`
- `config/OpenApiConfig`
- `web/EngineApiExceptionHandler`
- `web/MarketdataApiExceptionHandler`

## Forbidden Dependencies
- Do not place business workflows in this context.
- Keep feature-specific rules inside their bounded context.
