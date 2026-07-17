# Plan 19 — Navigation improvements

**Priority:** 🟢 Gameplay · **Effort:** L (staged) · **Depends on:** 01; stage 3 needs 15

## Problem

The companion relies on vanilla `PathAwareEntity` navigation, which is built for mobs,
not teammates: it balks at 1-block water crossings, can't descend cliffs safely, never
bridges gaps, and can't mine through a hill it's asked to cross. Goals like `EXPLORE`,
`MINE_RESOURCES`, and `FOLLOW_PLAYER` will fail constantly in real terrain, flooding the
planner with failure round-trips.

**Explicit non-goal:** a Baritone-class pathfinder. Stay with vanilla navigation plus
targeted escape hatches; re-evaluate only if stage 3 proves insufficient.

## Staged approach

### Stage 1 — vanilla knob tuning (S)

- Path node penalties via `setPathfindingPenalty` (mappings-dependent name): make WATER
  passable (0.0 instead of avoid), keep LAVA/FIRE strongly negative, tolerate rain.
- Step height 1.0 (walk up single blocks without jumping) if the attribute/setter exists
  in this version.
- Enable swimming: add vanilla `SwimGoal`-equivalent behavior or tick
  `getMoveControl` buoyancy — companion must not drown crossing a river (pairs with a
  `FleeBehavior`-style "surface if air low" reflex).

### Stage 2 — stuck detection + local escape (M)

- `StuckDetector` (util): position window over the last N=60 ticks; "stuck" = moved
  < 1.5 blocks while navigation reports an active path.
- Escape ladder, tried in order, each with its own cooldown:
  1. re-path with small random offset;
  2. jump;
  3. **place a block under feet / ahead** (pillar or 1-gap bridge) if a placeable block
     is in inventory (`ActionExecutor.placeBlock` exists);
  4. **mine the obstructing block(s)** — the ≤2 blocks in the movement direction at
     feet/head height (`ActionExecutor.breakBlock` exists; tool selection via Plan 15);
  5. give up → let the goal's own failure path report to the planner.
- Safety rails: never break blocks under liquids' faces (flood), never pillar above
  y-limit or on unstable support, respect a config `terraformingEnabled` (default true)
  for players who don't want the companion editing terrain.

### Stage 3 — goal-aware movement helpers (M)

- `NavHelper.descendSafely(targetY)`: staircase-mine downward (never dig straight down)
  — unblocks `MINE_RESOURCES` reaching diamond depth.
- `NavHelper.bridgeToward(target, maxLength)`: sneak-bridge across gaps up to N blocks.
- Both consume inventory blocks and are invoked *by goals* (not by the stuck escapes),
  e.g. `MineResourcesGoal` when target ore is below, `FollowPlayerGoal` when the player
  is across a gorge.

## Files to touch

- `src/main/java/com/example/companion/entity/CompanionEntity.java` (penalties, swim)
- `src/main/java/com/example/companion/util/StuckDetector.java` (new)
- `src/main/java/com/example/companion/util/NavHelper.java` (new, stage 3)
- `src/main/java/com/example/companion/entity/ActionExecutor.java`
- goal classes as each stage lands; `CompanionConfig` (`terraformingEnabled`)

## Acceptance criteria

- [ ] Stage 1: companion crosses a 3-wide river following the player without drowning.
- [ ] Stage 2: walled into a 1-block pit with cobble in inventory, it escapes within 10 s.
- [ ] Stage 2: `terraformingEnabled=false` disables block place/break escapes only.
- [ ] Stage 3: `MINE_RESOURCES` reaches y=-58 from the surface via staircase, not free-fall.
