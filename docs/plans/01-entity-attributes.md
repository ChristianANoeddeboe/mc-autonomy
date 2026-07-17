# Plan 01 — Register entity default attributes

**Priority:** 🔴 Must-fix · **Effort:** S · **Depends on:** nothing

## Problem

`CompanionMod` registers `COMPANION_ENTITY_TYPE` (a `PathAwareEntity` subclass) but never
registers default attributes for it. Custom living entities in Fabric **require** a
`FabricDefaultAttributeRegistry.register(...)` call; without it, `/companion spawn` crashes
the server with an "entity has no attributes" error the moment the entity is constructed.

This strongly suggests the mod has never been spawned in a live game — treat this as the
first thing to fix and verify.

## Approach

Add an attribute container for the companion in `CompanionMod.onInitialize()` (or a static
`createCompanionAttributes()` factory on `CompanionEntity`, mirroring vanilla convention).

Baseline stats (tune later):

| Attribute | Value | Rationale |
|---|---|---|
| `GENERIC_MAX_HEALTH` | 20.0 | Player-equivalent |
| `GENERIC_MOVEMENT_SPEED` | 0.3 | Slightly faster than zombie (0.23), close to player walk |
| `GENERIC_ATTACK_DAMAGE` | 2.0 | Fist-equivalent; tool bonuses come with Plan 15 |
| `GENERIC_FOLLOW_RANGE` | 32.0 | Matches `chatTriggerRadius` / perception scale |

## Implementation steps

1. Add to `CompanionEntity`:
   ```java
   public static DefaultAttributeContainer.Builder createCompanionAttributes() {
       return PathAwareEntity.createMobAttributes()
               .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
               .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
               .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 2.0)
               .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
   }
   ```
   (Adapt attribute names to the mappings in use if they differ.)
2. In `CompanionMod.onInitialize()`:
   ```java
   FabricDefaultAttributeRegistry.register(COMPANION_ENTITY_TYPE, CompanionEntity.createCompanionAttributes());
   ```
3. Run `./gradlew runServer` (or `runClient`), execute `/companion spawn`, confirm no crash
   and `/companion status` reports health 20.

## Files to touch

- `src/main/java/com/example/companion/entity/CompanionEntity.java`
- `src/main/java/com/example/companion/CompanionMod.java`

## Acceptance criteria

- [ ] `/companion spawn` spawns the entity on a dedicated server without crashing.
- [ ] `/companion status` shows health 20/20.
- [ ] Companion pathfinds (follow-player goal moves it) at a reasonable speed.
