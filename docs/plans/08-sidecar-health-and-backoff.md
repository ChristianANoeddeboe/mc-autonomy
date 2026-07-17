# Plan 08 — Sidecar health check + backoff

**Priority:** 🟠 Robustness · **Effort:** M · **Depends on:** nothing (pairs well with 07)

## Problem

Two gaps:

1. When the sidecar is down, the only signal is a server-log error. Players in-game see a
   companion that silently stands still — they should be told.
2. The periodic fallback keeps firing full `/decide` world-state POSTs every interval at a
   dead endpoint, forever, with no backoff.

## Approach

### Health probe

- `SidecarClient` gains `getHealthAsync(Consumer<JsonObject>)` hitting `GET /health`.
- On mod init (`CompanionMod.onInitialize`, deferred to first server tick so config is
  loaded), probe once and log a clear one-line verdict, including `stub_mode` from the
  response — players have been confused by STUB/IDLE behavior before; surface it:
  `Sidecar reachable at localhost:8765 (stub_mode=true — companion will only IDLE)`.

### Reachability state + narration

- `AIGoalPlanner` (or a small `SidecarStatus` holder) tracks `reachable: boolean`,
  updated by every request outcome.
- On transition reachable→unreachable: narrate once to nearby players via the existing
  narration path: `[Companion] I've lost my link to the AI sidecar — idling.`
- On transition unreachable→reachable: `[Companion] AI link restored.` and trigger an
  immediate replan.

### Backoff

- While unreachable, stretch the periodic fallback: interval × 2 per consecutive failure,
  capped at 5 minutes; reset to configured interval on first success.
- Event-driven triggers (chat, goal complete/fail) still fire immediately — a player
  talking to the companion is a natural "retry now".

## Implementation steps

1. Add health-probe method to `SidecarClient` (reuse the existing async plumbing).
2. Add reachability tracking + backoff multiplier to `AIGoalPlanner.tick()`'s fallback
   branch.
3. Route the two transition narrations through `CompanionEntity` (respecting
   `narrationEnabled`).
4. Wire the startup probe + log line in `CompanionMod`.
5. Manual test: start game without sidecar (observe narration + backoff in logs), start
   sidecar mid-session (observe recovery narration + immediate replan).

## Files to touch

- `src/main/java/com/example/companion/net/SidecarClient.java`
- `src/main/java/com/example/companion/goal/AIGoalPlanner.java`
- `src/main/java/com/example/companion/entity/CompanionEntity.java`
- `src/main/java/com/example/companion/CompanionMod.java`

## Acceptance criteria

- [ ] Startup log states sidecar reachability and stub_mode in one obvious line.
- [ ] Players near the companion are told (once) when the AI link drops and when it returns.
- [ ] With the sidecar down, request frequency decays to ≤1 per 5 minutes.
- [ ] First successful reconnect restores the normal interval and replans immediately.
