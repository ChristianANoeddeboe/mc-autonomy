# Plan 15 — Equipment use (armor + tools)

**Priority:** 🟢 Gameplay · **Effort:** M · **Depends on:** 01, 03

## Problem

The companion carries a 36-slot `SimpleInventory` but never *wears* or *wields* anything.
Armor it picks up is dead weight, `AttackBehavior` swings bare-handed regardless of
swords in the bag, and mining ignores tool tiers (Plan 04's `MineResourcesGoal` will care
once iron+ ores matter). The objective system (`FULL_DIAMOND_GEAR`) literally tracks gear
the companion can't equip.

## Approach

A small `EquipmentManager` owned by `CompanionEntity`, invoked on inventory change (item
pickup, crafting) and by goals/behaviors when they need a tool.

### Armor

- On inventory change, for each armor slot: find the best armor item in inventory
  (compare protection value; later toughness), `equipStack(slot, best)`, move the
  displaced piece back to inventory.
- `LivingEntity` armor attribute handling then works for free.

### Tools

- `selectBestTool(BlockState target)`: pick the inventory tool with the highest mining
  speed against the target that is also *correct* for drops (`isSuitableFor`), set it as
  main hand before `ActionExecutor.breakBlock` — which should also start applying
  tool-based mining time instead of instant `sw.breakBlock` (stretch; see note).
- `selectWeapon()`: highest attack damage item for `AttackBehavior`; fall back to fist.
- Durability: skip tools at <5% durability when a same-tier spare exists; never *use up*
  the last pickaxe below 5% unless no alternative (configurable).

### Rendering

Equipped items render automatically once Plan 02's biped renderer is in (armor +
held-item feature renderers come with the vanilla model — verify layers are attached).

### Note on mining realism

Instant `breakBlock` + correct-tool selection is acceptable for v1. Simulated mining
time (progress + `world.setBlockBreakingInfo`) is a follow-up inside this plan, flagged
separately because it touches every mining goal's tick logic.

## Implementation steps

1. `EquipmentManager` (new, `entity` package): `onInventoryChanged()`, `selectBestTool`,
   `selectWeapon`, durability policy.
2. Hook `onInventoryChanged` into `CompanionEntity.loot(...)`, `ScanUtils.addToInventory`
   call sites, and `CraftItemGoal` completion.
3. Update `AttackBehavior` to call `selectWeapon()` on activation.
4. Update `MineResourcesGoal`/`GatherWoodGoal` to call `selectBestTool` before breaking.
5. Persist equipped slots — verify `LivingEntity` already NBT-saves equipment (it does);
   ensure Plan 03's inventory serialization doesn't double-count equipped items.
6. `/companion status` lists equipped gear.

## Files to touch

- `src/main/java/com/example/companion/entity/EquipmentManager.java` (new)
- `src/main/java/com/example/companion/entity/CompanionEntity.java`
- `src/main/java/com/example/companion/behavior/AttackBehavior.java`
- `src/main/java/com/example/companion/goal/goals/{MineResourcesGoal,GatherWoodGoal}.java`
- `src/main/java/com/example/companion/util/ScanUtils.java`

## Acceptance criteria

- [ ] Dropping iron armor near the companion results in it being worn (and visible).
- [ ] Companion holds its best sword when fighting, best pickaxe when mining.
- [ ] Better gear replaces worse gear automatically; the displaced piece stays in inventory.
- [ ] `FULL_DIAMOND_GEAR` objective progress reflects worn gear.
