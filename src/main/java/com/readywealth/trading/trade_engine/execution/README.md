# Execution Context

## Purpose
Owns broker-agnostic order lifecycle orchestration, account state, and broker adapters.

## Allowed Layers
- `api/http`
- `api/ws`
- `application`
- `application/port`
- `domain`
- `infrastructure`

## Key Entrypoints
- `api/http/TradingController`
- `api/ws/TradingWebSocketHandler`
- `application/service/TradeOrchestratorService`
- `application/service/OrderStateMachineService`
- `infrastructure/paper/PaperBrokerGateway`
- `infrastructure/redis/RedisTradingStore`

## Forbidden Dependencies
- Keep broker/storage implementations in `infrastructure`.
- Keep `domain` framework-free.
- Keep transport DTOs in `api` only.
