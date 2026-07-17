# Plan 13 — Objective-aware failure recovery

**Priority:** 🟡 AI layer · **Effort:** S · **Depends on:** nothing

## Problem

`AIGoalPlanner.RECOVERY_MAP` hardcodes one recovery goal per failed goal type, ignoring
the long-term objective entirely. Example: with objective `FULL_DIAMOND_GEAR`, a failed
`MINE_RESOURCES` recovers to `EXPLORE` (wander anywhere) when the milestone system
already knows the companion should, say, gather wood for pickaxes or keep descending for
diamonds. The `ObjectiveTracker` computes milestones; recovery just doesn't consult them.

## Approach

Make recovery a function of `(failedGoal, currentMilestone)` instead of `(failedGoal)`,
with the existing map as the fallback.

1. **Expose the milestone.** `ObjectiveTracker.evaluate(...)` already returns a
   `Progress` with a milestone string. Add a small enum or well-known constant set for
   milestones (today they're display strings — brittle to switch on). Refactor:
   `Progress` gains a `MilestoneKey` enum next to the display text.
2. **Recovery hook.** In `AIGoalPlanner.onGoalFailed`, before consulting `RECOVERY_MAP`:
   ```java
   GoalType recovery = ObjectiveRecovery.suggest(failed, entity); // may return null
   if (recovery == null) recovery = RECOVERY_MAP.get(failed);
   ```
3. **`ObjectiveRecovery` table (new class, `objective` package).** Initial rules:

   | Failed goal | Milestone context | Recovery |
   |---|---|---|
   | `MINE_RESOURCES` | needs wood/tools first | `GATHER_WOOD` |
   | `MINE_RESOURCES` | has tools, needs ore | `EXPLORE` (with `distance` param biased down/caves once Plan 04 lands) |
   | `CRAFT_ITEM` | missing raw materials for milestone item | `MINE_RESOURCES` (param = missing resource) or `GATHER_WOOD` |
   | `BUILD_SHELTER` / `BUILD_HOUSE` objective | missing materials | `GATHER_WOOD` |
   | any | objective `NONE` | null → legacy map |

4. **Keep it small.** This is a heuristic bridge, not a planner — anything beyond one
   table lookup belongs to the AI request that fires on repeated failure. Do not grow
   this into a rules engine; Plan 14 (sub-goal planning) is the real fix long-term.

## Files to touch

- `src/main/java/com/example/companion/objective/ObjectiveTracker.java` (milestone keys)
- `src/main/java/com/example/companion/objective/ObjectiveRecovery.java` (new)
- `src/main/java/com/example/companion/goal/AIGoalPlanner.java`

## Acceptance criteria

- [ ] With `FULL_DIAMOND_GEAR` and no pickaxe, a failed `MINE_RESOURCES` recovers to
      `GATHER_WOOD`, not `EXPLORE`.
- [ ] With objective `NONE`, behavior is identical to today.
- [ ] Repeated failures still escalate to the AI exactly as before.
