# Plan 03 — NBT persistence for inventory, hunger, and objective

**Priority:** 🔴 Must-fix · **Effort:** M · **Depends on:** nothing

## Problem

`CompanionEntity` never overrides the NBT read/write hooks. Its `SimpleInventory` (36
slots), `hunger`, and long-term `objective` live only in RAM, so a chunk unload or server
restart silently wipes everything the companion has gathered and the objective the player
set. Phase 5 claims "persistent memory survives server restarts" but only the *sidecar's*
`memory.json` persists today.

## Approach

Override the entity data serialization hooks (`writeCustomDataToNbt` / `readCustomDataFromNbt`,
or the `WriteView`/`ReadView` variants if the target mappings use them) and round-trip all
mutable companion state.

## State to persist

| Field | NBT key | Encoding |
|---|---|---|
| `inventory` | `CompanionInventory` | `Inventories.writeNbt` / `readNbt` list |
| `hunger` | `CompanionHunger` | int |
| `objective` | `CompanionObjective` | enum name string; default `NONE` on unknown |
| `lastPlayerMessage` | `LastPlayerMessage` | string (optional but cheap; keeps AI context) |

Notes:

- Health persists automatically via `LivingEntity`. Goal state deliberately does **not**
  persist — on load the entity starts at `IDLE` and the existing 1-second initial
  `scheduleRequest(20)` kicks off a fresh sidecar decision, which is the correct behavior.
- Guard `readCustomDataFromNbt` against missing keys (older saves) with sensible defaults.
- Unknown objective names (e.g. after an enum rename) must fall back to `NONE` with a log
  warning, not crash.

## Implementation steps

1. Implement the two overrides in `CompanionEntity`.
2. Ensure `initialRequestScheduled` stays `false` on load so the initial replan fires.
3. Add `setPersistent()` / ensure `cannotDespawn()` semantics — the companion must never
   despawn like an ambient mob. Verify `SpawnGroup.CREATURE` despawn rules don't cull it;
   if they can, override `canImmediatelyDespawn`/`isPersistent` accordingly.
4. Manual test: spawn, give items (drop near it), set objective, `/save-all`, restart
   server, verify `/companion status` shows same hunger/objective and inventory intact.

## Files to touch

- `src/main/java/com/example/companion/entity/CompanionEntity.java`

## Acceptance criteria

- [ ] Inventory, hunger, and objective survive a full server restart.
- [ ] Companion never despawns when the player walks away.
- [ ] Loading a save from before this change works (defaults applied, no crash).
- [ ] After restart the companion requests a fresh goal from the sidecar within ~1 s.
