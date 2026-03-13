# Alerts Context

## Purpose
Owns alert rules and delivery notification orchestration.

## Allowed Layers
- `application`
- `domain`
- `infrastructure`

## Key Entrypoints
- `application/AlertRuleService`
- `domain/AlertRule`
- `infrastructure/NoOpAlertNotifier`

## Forbidden Dependencies
- No transport adapters in this context until an alert API is intentionally introduced.
- Keep domain models framework-free.
