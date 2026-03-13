# Marketdata Context

## Purpose
Owns market-data ingestion, subscription orchestration, and historical bootstrap feed.

## Allowed Layers
- `api/http`
- `api/ws`
- `application`
- `application/port`
- `domain`
- `infrastructure`

## Key Entrypoints
- `api/http/SubscriptionController`
- `api/http/HistoricalCandleController`
- `api/ws/TicksWebSocketHandler`
- `application/SubscriptionService`
- `application/TickToSeriesIngestService`
- `application/SessionBootstrapCoordinator`

## Forbidden Dependencies
- Keep websocket transport and HTTP DTO mapping in `api`.
- Keep vendor clients and Redis adapters in `infrastructure`.
- Keep `domain` free from Spring and external SDK imports.
