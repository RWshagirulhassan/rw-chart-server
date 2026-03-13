# Auth Context

## Purpose
Owns user authentication, broker callback exchange, and token lifecycle.

## Allowed Layers
- `api/http`
- `application`
- `domain`
- `infrastructure`

## Key Entrypoints
- `api/http/KiteAuthController`
- `api/http/InternalController`
- `application/AuthService`
- `application/KiteService`
- `infrastructure/redis/RedisTokenStore`

## Forbidden Dependencies
- Do not add root-level legacy folders (`controller`, `service`, `store`, `model`, `config`) outside canonical layers.
- Keep `domain` free from Spring/Redis/broker SDK imports.
