# Script Runtime README

## Script types
- `INDICATOR`: writes plots/drawings/alerts.
- `STRATEGY`: same as indicator + emits intents.

## Main interfaces
- `ScriptDefinition`
- `ScriptParamsBinder<T>`
- `ScriptRuntime`
- `ScriptExecutionContext`
- `ScriptCatalog`

## Param model
Input params are dynamic (`ScriptParams` map) at API boundary.
Each script must bind/validate to typed params using `paramsBinder()`.

## Module dependencies
Scripts can declare required module specs via:
- `requiredModules(Object typedParams)`

Session runtime merges these specs globally for the session and rebuilds context when needed.

## Attach model
- Same `scriptId` can be attached multiple times.
- Every attachment has a unique `ScriptInstanceId`.
- Runtime stores attach sequence for deterministic ordering.

## Minimal implementation checklist for a new script
1. Implement `ScriptDefinition`.
2. Provide stable `ScriptId`.
3. Implement `paramsBinder()`.
4. (Optional) declare module deps in `requiredModules(...)`.
5. Return `ScriptRuntime` from `createRuntime(...)`.

See examples:
- `examples/SampleIndicatorScriptDefinition`
- `examples/SampleBosIntentStrategyScriptDefinition`
