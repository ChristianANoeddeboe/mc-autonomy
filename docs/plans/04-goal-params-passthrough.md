# Plan 04 — Pass goal params from sidecar to goals

**Priority:** 🔴 Must-fix · **Effort:** M · **Depends on:** nothing

## Problem

The sidecar's `GoalDecision` includes a `params` dict and every `CompanionGoal.start(Map)`
accepts params — but `AIGoalPlanner`'s response handler hardcodes `pendingParams = Map.of()`
(AIGoalPlanner.java:186). The AI literally cannot say *what* to craft, *what* to mine, or
*how far* to explore. `CRAFT_ITEM` and `MINE_RESOURCES` are close to useless without this.

## Approach

Parse the `params` JSON object from the sidecar response into a `Map<String, Object>`,
pass it through, and define a documented param contract per goal on both sides (Java
consumption + Python prompt).

## Param contract (v1)

| Goal | Param | Type | Default |
|---|---|---|---|
| `CRAFT_ITEM` | `item` | string (item id, e.g. `"wooden_pickaxe"`) | fail goal if absent |
| `MINE_RESOURCES` | `resource` | string (e.g. `"iron_ore"`, `"stone"`) | `"stone"` |
| `GATHER_WOOD` | `count` | int (logs wanted) | 8 |
| `EXPLORE` | `distance` | int (blocks from start) | 64 |
| `FOLLOW_PLAYER` | `player` | string (name; nearest if absent) | nearest player |
| `FIND_FOOD` | — | | |
| `BUILD_SHELTER` | — | | |
| `SLEEP` / `IDLE` | — | | |

Unknown params are ignored with a debug log; wrong types fall back to defaults.

## Implementation steps

1. **Java parse.** In `AIGoalPlanner.triggerRequest`'s callback, convert
   `response.getAsJsonObject("params")` to `Map<String, Object>` (strings, numbers,
   booleans — Gson primitives only; nested objects flattened away or ignored). Store in
   `pendingParams`.
2. **Atomic handoff.** While here, fix the volatile-pair race: replace the two volatile
   fields `pendingGoalType`/`pendingParams` with a single `AtomicReference<PendingDecision>`
   record so type and params can never be observed torn.
3. **Goal consumption.** Add a small `GoalParams` helper (typed getters with defaults:
   `getString(params, "item", null)`, `getInt(params, "count", 8)`). Update
   `CraftItemGoal`, `MineResourcesGoal`, `GatherWoodGoal`, `ExploreGoal`,
   `FollowPlayerGoal` to read their params in `start(...)`.
4. **Prompt update.** In `sidecar/prompt_builder.py`, document the param contract in the
   system prompt (table above, condensed) so the model knows what it may send.
5. **Command parity.** Extend `/companion goal <type>` with an optional
   `[key=value ...]` tail for manual testing of parameterized goals.

## Files to touch

- `src/main/java/com/example/companion/goal/AIGoalPlanner.java`
- `src/main/java/com/example/companion/goal/goals/{CraftItemGoal,MineResourcesGoal,GatherWoodGoal,ExploreGoal,FollowPlayerGoal}.java`
- `src/main/java/com/example/companion/goal/GoalParams.java` (new helper)
- `src/main/java/com/example/companion/command/CompanionCommands.java`
- `sidecar/prompt_builder.py`

## Acceptance criteria

- [ ] Telling the companion "companion: craft a wooden pickaxe" results in `CRAFT_ITEM`
      starting with `item=wooden_pickaxe` (visible in logs).
- [ ] `/companion goal CRAFT_ITEM item=stick` works for manual testing.
- [ ] Malformed/missing params never crash — goals fall back to defaults or fail cleanly.
- [ ] No torn reads: goal type and params are applied together.
