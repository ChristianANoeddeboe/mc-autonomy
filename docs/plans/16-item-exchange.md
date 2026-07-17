# Plan 16 — Item exchange with the player

**Priority:** 🟢 Gameplay · **Effort:** M · **Depends on:** 03; benefits from 15

## Problem

There is no way to hand the companion a sword or take back the iron it mined, short of
throwing items on the ground and hoping (`canPickUpLoot` pickup). The companion is a
teammate; trading items with it should be first-class.

## Approach

Two tiers — ship tier 1 first, tier 2 is optional polish:

### Tier 1: right-click interactions (no GUI)

Override `CompanionEntity.interactMob(PlayerEntity, Hand)`:

- **Held item, click** → give: move the held stack into the companion inventory
  (via `ScanUtils.addToInventory`); narrate "Thanks for the {item}!". Fail gracefully
  (narrate "I'm full") when the inventory can't fit it.
- **Empty hand + sneak, click** → dump: companion drops its *surplus* at the player's
  feet — everything except equipped gear, one tool of each type, and food
  (keep-list logic in `EquipmentManager` once Plan 15 lands; before that, drop all).
- **Empty hand, click** → status: narrate current goal + objective progress one-liner
  (cheap discoverability; same text as `/companion status`).

Return `ActionResult.SUCCESS` server-side so the interaction doesn't fall through to
attack.

### Tier 2: inventory screen (follow-up)

A `ScreenHandler` + client screen showing the companion's 36 slots like a chest —
standard generic 9x4 container screen bound to the `SimpleInventory`. Only worthwhile
after tier 1 proves insufficient; requires client networking + screen registration.

### AI awareness

Given items should influence decisions: giving raw iron with `FULL_DIAMOND_GEAR` active
should nudge crafting. No sidecar change needed — inventory already rides in the world
state — but add a `significant event` trigger (`goalPlanner.onSignificantEvent("player
gave: ...")`) so a replan fires promptly after a gift.

## Implementation steps

1. Implement `interactMob` with the three branches; route narration through the
   Plan 10 `narrate(String)` helper (or a local copy until then).
2. Add the give-event planner trigger.
3. Sneak-dump keep-list: minimal version pre-Plan 15, refine after.
4. Manual test all three interactions in survival, including full-inventory edge case.

## Files to touch

- `src/main/java/com/example/companion/entity/CompanionEntity.java`
- `src/main/java/com/example/companion/util/ScanUtils.java`
- (tier 2) new `screen` package + client screen registration

## Acceptance criteria

- [ ] Right-click with an item transfers it to the companion (or politely refuses when full).
- [ ] Sneak + empty-hand click makes the companion hand over its surplus.
- [ ] Empty-hand click narrates goal/objective status.
- [ ] Giving a relevant item triggers a replan within a few seconds.
- [ ] Interactions never trigger the attack animation or damage.
