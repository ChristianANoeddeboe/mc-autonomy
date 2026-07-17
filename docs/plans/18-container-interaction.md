# Plan 18 — Container interaction / home storage

**Priority:** 🟢 Gameplay · **Effort:** L · **Depends on:** 04, 15, 16

## Problem

The companion hoards everything in its 36 slots until full, then item pickup starts
failing silently. There's no concept of "home", no depositing into chests, and the
`BUILD_HOUSE` objective produces a house the companion never uses. Long sessions need a
logistics loop: gather → return home → deposit → continue.

## Approach

### Home position

- `CompanionEntity` gains a persisted (Plan 03) `homePos: BlockPos?`.
- Set via: `/companion home set` (player command, uses player position),
  `BUILD_SHELTER`/`BUILD_HOUSE` completion (auto-set to the built structure), or a
  future chat instruction ("this is home").
- Serialized into world state (`"home": {"position": [...], "distance": 42.5}`) so the
  AI can reason about returning.

### New goal: `STORE_ITEMS`

New `GoalType.STORE_ITEMS` + `StoreItemsGoal` FSM:

1. Fail fast if no `homePos` or no chest found within `scanRadius` of home
   (`ScanUtils` scan for `ChestBlockEntity`).
2. Navigate to the chest (existing navigation patterns from other goals).
3. Within reach: transfer surplus (same keep-list as Plan 16's dump — keep equipped
   gear, one tool each, food, objective-relevant materials) into the chest inventory via
   `ChestBlockEntity`/`Inventory` API, respecting chest capacity; animate arm swing +
   play chest sounds for believability.
4. Complete when surplus is transferred or chest is full (full chest = complete-with-
   narration, not failure — don't loop).

Also the inverse `RETRIEVE_ITEMS` (params: `item`, `count`) — same skeleton, opposite
transfer direction; lets the AI fetch stored materials for crafting. Ship `STORE_ITEMS`
first.

### Planner integration

- Add both goals to the sidecar `GoalType` enum, prompt goal list, and param contract
  (Plan 04 table).
- `WorldPerception` adds inventory fullness (`"inventory_slots_used": 30`) so the
  model knows *when* to store.
- Deterministic nudge: when inventory ≥ 90% full and `homePos` exists, fire
  `onSignificantEvent("inventory nearly full")`.

## Implementation steps

1. Home position field + persistence + `/companion home set|clear|status` subcommands.
2. `StoreItemsGoal` + registration in `GoalExecutor` + `GoalType` (both languages).
3. Chest scan support in `ScanUtils`; shared `ContainerHelper` for transfer logic.
4. World-state additions (home, slot usage) — additive, no schema bump per Plan 09 policy.
5. `RETRIEVE_ITEMS` as a follow-up commit reusing `ContainerHelper`.

## Files to touch

- `src/main/java/com/example/companion/entity/CompanionEntity.java`
- `src/main/java/com/example/companion/goal/{GoalType,GoalExecutor}.java`
- `src/main/java/com/example/companion/goal/goals/StoreItemsGoal.java` (new)
- `src/main/java/com/example/companion/util/{ScanUtils,ContainerHelper}.java`
- `src/main/java/com/example/companion/command/CompanionCommands.java`
- `src/main/java/com/example/companion/entity/WorldPerception.java`
- `sidecar/models.py`, `sidecar/prompt_builder.py`

## Acceptance criteria

- [ ] With a home + chest set, a nearly-full companion autonomously deposits surplus.
- [ ] Equipped gear, tools, and food are never deposited.
- [ ] Chest-full and no-chest cases end cleanly with narration, no failure loop.
- [ ] Home position survives restart.
