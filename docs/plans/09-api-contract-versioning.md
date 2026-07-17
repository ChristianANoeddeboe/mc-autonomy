# Plan 09 — API contract versioning

**Priority:** 🟠 Robustness · **Effort:** S · **Depends on:** nothing (land before/with 10 and 14)

## Problem

The mod and sidecar are versioned and deployed independently (a JAR in `mods/`, a Python
process the user starts by hand). Nothing detects a mismatch: an old sidecar will happily
accept a new world-state payload, ignore fields it doesn't know (Pydantic default), and
return decisions missing fields the mod expects — failing subtly instead of loudly.

## Approach

A single integer `schema_version` owned by both sides, checked on every request and at
health-probe time.

- **Constant.** `SCHEMA_VERSION = 1` — defined in `WorldPerception` (Java) and
  `models.py` (Python). Bump both in the same commit whenever the wire format changes;
  a comment at each definition points at the other.
- **Request.** `WorldPerception.serialize` adds top-level `"schema_version": 1`.
- **Sidecar check.** `WorldState` gains `schema_version: int = 0`. In `/decide`:
  - equal → proceed.
  - different → log a WARNING with both versions and proceed *best-effort* (return a
    decision but include `"schema_warning": "sidecar expects v1, mod sent v2"` in the
    response). Hard-failing would brick older setups over cosmetic field additions;
    loud logging + response annotation is enough.
- **Health.** `GET /health` response gains `"schema_version": 1`; the Plan 08 startup
  probe compares and logs a mismatch prominently at WARN level.
- **Response.** `GoalDecision` responses gain `schema_version` too, checked by
  `AIGoalPlanner` the same way (log once per session, not per request — keep a
  `warnedAboutSchema` flag).

## Versioning policy

- Additive optional fields: no bump required (both sides tolerate unknown/missing).
- Renamed/removed/retyped fields, changed enum values, new required fields: bump.
- Record the history in a short table at the bottom of this file as bumps happen.

| Version | Date | Change |
|---|---|---|
| 1 | (on merge) | Initial versioned contract |

## Files to touch

- `src/main/java/com/example/companion/entity/WorldPerception.java`
- `src/main/java/com/example/companion/goal/AIGoalPlanner.java`
- `sidecar/models.py`
- `sidecar/main.py`

## Acceptance criteria

- [ ] Matching versions: zero new log noise.
- [ ] Mismatched versions: exactly one prominent WARN on each side per session, requests
      still flow best-effort.
- [ ] `/health` reports the sidecar's schema version.
