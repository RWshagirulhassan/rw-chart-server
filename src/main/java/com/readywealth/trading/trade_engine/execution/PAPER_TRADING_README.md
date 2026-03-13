# Paper Trading README (Linked TP/SL, OCO, Broker-Replaceable)

## Purpose
This document describes the current Paper Trading implementation in `execution` with TradingView-style entry + TP/SL attachments, and how it is designed to swap to Kite later without rewriting core business logic.

## What Is Implemented
- Manual order ticket supports `MARKET`, `LIMIT`, `STOP`, `STOP-LIMIT` and optional TP/SL attachments.
- Linked-order domain model in backend: `ExitPlan`, `Trigger`, `OcoGroup`.
- Orders Timeline projection: TP/SL appears as logical child rows under entry order (no fake pre-trigger broker orders).
- Default arm policy: TP/SL triggers arm only after entry order reaches `FILLED`.
- OCO behavior for TP+SL: one trigger fires, sibling trigger is cancelled atomically.
- Trigger engine in paper mode evaluates active triggers from live LTP snapshots.
- Trigger hit creates a normal reduce-only `LIMIT` exit order attempt.
- EOD sweep marks residual exit plans `REARM_REQUIRED` when triggered exit orders stay open.
- Separate trigger book APIs and trading websocket events.

## Architecture (SOLID + Ports/Adapters)
- Core domain and use-cases are broker-agnostic (`execution.domain`, `execution.application`).
- Broker/margin/storage/event dependencies are abstracted via ports (`execution.application.port`).
- Paper and Kite are adapters (`execution.infrastructure.paper`, `execution.infrastructure.kite`).

### Key patterns used
- Hexagonal (Ports and Adapters): core depends on interfaces, not infrastructure.
- Application services/orchestrators: order flow and linked-order flow stay centralized.
- State machines: explicit transitions for order, trigger, exit-plan, OCO lifecycle.
- Repository + atomic script pattern: Redis store with Lua for trigger fire/OCO cancellation race-safety.
- Event-driven read model updates: canonical events published to `/trading/ws`.

## New/Updated Files for Linked Orders

### Domain
- `execution/domain/OrderAttachments.java`
- `execution/domain/ExitPlan.java`
- `execution/domain/ExitLegSpec.java`
- `execution/domain/Trigger.java`
- `execution/domain/OcoGroup.java`
- `execution/domain/LinkedExitSummary.java`
- `execution/domain/TradingOrderRole.java`
- `execution/domain/ArmPolicy.java`
- `execution/domain/ExitPlanStatus.java`
- `execution/domain/ExitPlanMode.java`
- `execution/domain/ExitLegType.java`
- `execution/domain/TriggerStatus.java`
- `execution/domain/TriggerConditionOp.java`
- `execution/domain/OcoGroupStatus.java`
- `execution/domain/TriggerOrderAttempt.java`
- `execution/domain/TriggerOrderAttemptStatus.java`
- `execution/domain/TriggerFireResult.java`
- `execution/domain/OrderTimelineRow.java`
- `execution/domain/OrderTimelineRowType.java`
- `execution/domain/OrderTimelineStatus.java`
- `execution/domain/OrderTimelineIds.java`
- `execution/domain/OrderTimelineUpdatePayload.java`

Why:
- These types explicitly model linked orders and trigger-book behavior instead of hiding it inside generic order fields.

### Existing domain extensions
- `execution/domain/OrderDraft.java`
- `execution/domain/TradingOrder.java`
- `execution/domain/TradingEventType.java`

Why:
- Adds metadata for parent/linked relationships and role (`ENTRY`, `EXIT_TP`, `EXIT_SL`) while keeping backward compatibility.

### Ports
- `execution/application/port/TriggerStorePort.java`
- `execution/application/port/TriggerEnginePort.java`
- `execution/application/port/ExitPlanOrchestratorPort.java`
- `execution/application/port/ConditionalTriggerGateway.java` (future Kite-GTT adapter seam)

Why:
- Keeps trigger-book and linked-order orchestration behind interfaces so Paper/Kite switching is wiring-only.

### Services
- `execution/application/service/LinkedOrderOrchestratorService.java`
- `execution/application/service/ExitPlanArmingService.java`
- `execution/application/service/LinkedOrderLifecycleService.java`
- `execution/application/service/TriggerEvaluationService.java`
- `execution/application/service/ExitPlanEodService.java`
- `execution/application/service/ExitPlanQueryService.java`
- `execution/application/service/TriggerQueryService.java`
- `execution/application/service/ExitPlanManagementService.java`
- `execution/application/service/OrderTimelineQueryService.java`
- `execution/application/service/OrderTimelineStatusResolver.java`
- `execution/application/service/OrderTimelineEventService.java`
- `execution/application/service/TradeOrchestratorService.java` (extended)
- `execution/application/service/FillApplicationService.java` (extended)

Why:
- Separate concerns: create/arm/evaluate/complete/cancel linked exits without polluting base order placement.

### Redis adapter
- `execution/infrastructure/redis/RedisTriggerStore.java`

Why:
- Stores exit plans/triggers/OCO groups and provides atomic `fire trigger + cancel sibling` transition.

### API layer
- `execution/api/http/TradingController.java` (extended)
- DTOs:
  - `PlaceOrderRequest.java` (attachments added)
  - `PlaceOrderResponse.java`
  - `OrderAttachmentsRequest.java`
  - `ExitLegRequest.java`
  - `LinkedExitSummaryResponse.java`
  - `CreateExitPlanRequest.java`
  - `UpdateExitPlanRequest.java`
  - `ExitPlanResponse.java`
  - `ExitLegResponse.java`
  - `TriggerResponse.java`
  - `OrderResponse.java` (linked order fields added)
  - `OrderTimelineRowResponse.java`

Why:
- Public contract now exposes linked-order creation, trigger book read APIs, and exit-plan management.

### Client wiring
- `client/src/lib/api/trading.ts`
- `client/src/components/organisms/trading/TradeTicketOverlay.tsx`
- `client/src/components/organisms/trading/TradingAccountPanel.tsx`

Why:
- Ticket sends optional attachments in the same entry request and surfaces OCO/arm-after-fill UX.
- Orders tab consumes timeline rows, renders TP/SL as child rows, and listens to timeline update events.

## API Contract (Current)

### Entry order with attachments
- `POST /v1/trading/{accountId}/orders`
- `attachments` is optional and broker-agnostic.

Response:
- `order`: canonical order response.
- `linkedExit`: optional summary (`exitPlanId`, `status`, `mode`, `armPolicy`).

### Trigger/Exit plan endpoints
- `GET /v1/trading/{accountId}/exit-plans`
- `GET /v1/trading/{accountId}/triggers`
- `GET /v1/trading/{accountId}/orders/timeline?includeTerminal=true&limit=500`
- `POST /v1/trading/{accountId}/positions/{instrumentToken}/exit-plan`
- `PUT /v1/trading/{accountId}/exit-plans/{exitPlanId}`
- `DELETE /v1/trading/{accountId}/exit-plans/{exitPlanId}`

### Orders Timeline row model
- `rowType`: `ENTRY_ORDER | LINKED_EXIT`
- `rowId`: `order:{orderId}` or `lexit:{exitPlanId}:TP|SL`
- `parentOrderId`: present for child rows
- `status`: `PENDING | ARMED | TRIGGERED | OPEN | PARTIALLY_FILLED | FILLED | CANCELLED_OCO | REJECTED | REARM_REQUIRED | CANCELLED`

### Timeline websocket updates
- New event type: `ORDER_TIMELINE_UPDATED`
- Payload:
  - `rowIds`
  - `parentOrderIds`
  - `reason`
  - `fullRefresh`
  - `tsEpochMs`
- Websocket transport is unchanged (`type=trading_event` envelope).

## State Machines

### Order
- `NEW -> OPEN -> PARTIALLY_FILLED -> FILLED`
- `OPEN/PARTIALLY_FILLED -> CANCELLED`
- `NEW/OPEN -> REJECTED`

### Trigger
- `ACTIVE -> TRIGGERED -> ORDER_PLACED/ORDER_REJECTED -> COMPLETED`
- `ACTIVE -> CANCELLED` (plan/oco/user cancel)

### ExitPlan
- `PENDING -> ACTIVE` (entry filled and triggers armed)
- `ACTIVE -> COMPLETED`
- `PENDING/ACTIVE -> CANCELLED`
- `ACTIVE -> REARM_REQUIRED` (triggered order rejected or EOD-cancelled with residual qty)

### OCO group
- `ACTIVE -> ONE_TRIGGERED -> COMPLETED`
- `ACTIVE -> CANCELLED`

## End-to-End Flows

### A) New entry + TP/SL
1. UI places entry order with optional attachments.
2. Core places entry order.
3. If attachments exist, save `ExitPlan` as `PENDING`, publish `EXIT_PLAN_CREATED`.
4. On entry `FILLED`, arm triggers and optional OCO group, publish `EXIT_PLAN_ARMED`.
5. Trigger evaluator detects crossover and atomically marks trigger fired.
6. Trigger creates reduce-only `LIMIT` exit order.
7. OCO sibling trigger is cancelled immediately if applicable.
8. Fill/lifecycle marks plan `COMPLETED` or `REARM_REQUIRED`.
9. Orders Timeline reflects TP/SL row statuses continuously:
   - `PENDING` (waiting entry fill) -> `ARMED` -> `TRIGGERED/OPEN` -> `FILLED` or `REARM_REQUIRED`.

### B) Existing position TP/SL
1. Client calls create exit-plan-on-position endpoint.
2. Plan is created as `ACTIVE`.
3. Triggers are created immediately.
4. Trigger engine and lifecycle flow are same as above.
5. These plans remain in Exit Plans/Triggers views; they are intentionally excluded from Orders Timeline.

### C) EOD sweep
1. Scheduled sweep checks `ORDER_PLACED` triggers with still-open linked orders.
2. Cancels order with reason `paper_eod_cancel`.
3. If residual position remains, marks exit plan `REARM_REQUIRED`.
4. Otherwise marks `COMPLETED`.

## Redis Keys Used by Trigger Book
- `pta:exit:plan:{accountId}:{exitPlanId}`
- `pta:exit:plan:byEntry:{accountId}:{entryOrderId}`
- `pta:trg:{accountId}:{triggerId}`
- `pta:trg:active:{accountId}`
- `pta:trg:byInst:{accountId}:{instrumentToken}`
- `pta:oco:{accountId}:{ocoGroupId}`

## Pricing, Fills, and PnL
- Entry `MARKET` fills from latest price snapshot + configured slippage.
- Open `LIMIT` orders fill in paper execution cycle only on newer snapshots.
- Triggered exits are always `LIMIT` order attempts.
- Unrealized PnL updates in execution cycle using latest LTP and emits `POSITION_UPDATED`/`FUNDS_UPDATED`.

## Config Flags
In `application.yml` under `trading.paper`:
- `execution-cycle-enabled`
- `execution-cycle-fixed-delay-ms`
- `trigger-evaluation-fixed-delay-ms`
- `eod-sweep-enabled`
- `eod-sweep-fixed-delay-ms`
- `eod-cutoff-time`

## Known Gaps (Planned Next)
- Full Kite GTT adapter is not implemented yet (only architecture seam is present).
- Position model currently does not persist exchange; position-based plan creation defaults exchange to `NSE`.
- End-to-end load tests for high trigger counts are pending.
