# MC Companion — Part 4 Pre-check Resolution (NOTES.md)

Resolved: 2026-09-21, against the REAL 1.21.11 decompiled source
(query source: `~/.gradle/caches/fabric-loom/1.21.11` Loom 1.14.10 `genSources`
result, cross-checked against `/home/ollama/code/mc-mcp/index/source`, verify
`version.json` = 1.21.11, world 4671, built 2025-12-09).

## TL;DR — the Council's Part 4 corrections are largely INVERTED

The council review that produced Part 4 answered memory with memory. Where it
_contradicted_ Fable's plan, the actual jar almost always sided with **Fable's
original spelling**. Any implementer who blindly followed Part 4 would have
written code that does not compile against 1.21.11.

Ground-truth changes to make to the plan before coding:

| # | Part 4 claim | Ground truth (1.21.11 jar) | Verdict |
|---|---|---|---|
| §1 | use `ResourceLocation`, `Identifier` is Yarn alias | `net.minecraft.resources.Identifier` is the REAL Mojang class. `ResourceLocation.java` DOES NOT EXIST anywhere in the decompile | **Part 4 §1 WRONG** — Fable's footnote was right |
| §3 | `ValueOutput`/`ValueInput` don't exist → use `CompoundTag` | `ValueOutput`/`ValueInput` EXIST (`net.minecraft.world.level.storage.ValueOutput/ValueInput`); `Entity.addAdditionalSaveData(ValueOutput)` / `readAdditionalSaveData(ValueInput)` are the abstract methods; `ContainerHelper.saveAllItems(ValueOutput, NonNullList<ItemStack>)` confirmed | **Part 4 §3 WRONG** — Fable right |
| §4 | `hurtServer` never called → override `hurt(DamageSource,float)` | `Entity.hurt(DamageSource,float)` is `@Deprecated @final`, just forwards to `hurtServer`; the real abstract dispatch is `hurtServer(ServerLevel, DamageSource, float)` (Entity:1834, LivingEntity:1153) | **Part 4 §4 WRONG** — Fable right, and `hurt()` can't even be overridden (final) |
| §5 | server world is `ServerLevel` | `net.minecraft.server.level.ServerLevel` confirmed | **Part 4 §5 CORRECT** |
| §6 | `recipeAccess().getRecipes()` is "fantasy API" | `ServerLevel.recipeAccess(): RecipeManager` (1392) and `RecipeManager.getRecipes(): Collection<RecipeHolder<?>>` (172) BOTH EXIST | **Part 4 §6 WRONG** — Fable right |
| §9 | `doHurtTarget(Entity)` only; `(ServerLevel,Entity)` may be wrong | `Mob.doHurtTarget(ServerLevel, Entity)` is the ONLY variant (Mob:1340) | **Part 4 §9 WRONG** — Fable right |
| §2 | `EntityType.Builder.build(ResourceKey<EntityType<?>>)` | confirmed (EntityType Builder:build(ResourceKey<EntityType<?>>)) | **Part 4 §2 CORRECT** |
| §7 | build versions may not resolve | Loom 1.14.10, fabric-api 0.139.4+1.21.11, fabric-loader 0.18.2, MC 1.21.11 all resolve; `genSources` + decompile green | **Part 4 §7 — resolved, no change** |
| §8 | Brain must drain async queue before goals tick | sound engineering; adopted | **keep** |
| §10 | `inventory.remove` not atomic | design note, keep (query-then-remove) | **keep** |

## Definite API signatures for implementation (verified)

- **Identity type**: `net.minecraft.resources.Identifier` (final class).
  `Identifier.fromNamespaceAndPath(String ns, String path)` (confirmed).
  `Identifier.parse`, `.tryParse`, `.withDefaultNamespace` all present.
- **Registry keys**: `net.minecraft.resources.ResourceKey<T>`;
  static `ResourceKey.create(ResourceKey<? extends Registry<T>> type, Identifier id)`;
  instance `.identifier(): Identifier` (no `.location()` — the plan's
  `KEY.location()` does NOT exist; use `KEY.identifier()`).
- **Registry read**: `net.minecraft.core.Registry<T>` interface has
  `getOptional(Identifier)`, `getOptional(ResourceKey<T>)`, `containsKey`, `getAny`.
  Registry value: `Holder.Reference<T>`.
- **Registry write (vanilla)**: `MappedRegistry.register(ResourceKey<T>, T, RegistrationInfo)`
  (3-arg). For mod registration prefer the Fabric helper
  (`net.fabricmc.fabric.api.registry.Registry` / object-builder) — verify exact
  signature at Phase-2 build; the plan's `Registry.register(BuiltInRegistries.ENTITY_TYPE, KEY, ...)` needs the Fabric 3-arg form and `.identifier()` not `.location()`.
- **Entity type build**: `EntityType.Builder.of(EntityFactory<T>, MobCategory)` →
  `.sized(w,h)` → `.build(ResourceKey<EntityType<?>>)`. Confirmed. Constructor
  ref `CompanionEntity::new` matches `EntityFactory<T>` = `T create(EntityType<T>, Level)`.
- **Damage override**: override `public boolean hurtServer(ServerLevel, DamageSource, float)`.
  Do NOT override `hurt()`. (Fabric alternative exists: `ServerLivingEntityEvents.AFTER_DAMAGE`.)
- **NBT persistence**: `protected void addAdditionalSaveData(ValueOutput)` /
  `protected void readAdditionalSaveData(ValueInput)` (BOTH abstract on Entity,
  LivingEntity:729/781). `ContainerHelper.saveAllItems(ValueOutput, NonNullList<ItemStack>)`,
  `loadAllItems(ValueInput, NonNullList<ItemStack>)`. ValueOutput has `putInt/putUUID/...`;
  ValueInput has `getInt/getUUID/hasUUID`. No CompoundTag involved for the override.
  (`CompoundTag` class exists for other uses but is NOT the persistence arg type.)
- **Recipes**: `ServerLevel.recipeAccess()` → `RecipeManager.getRecipes()`
  → `Collection<RecipeHolder<?>>`. `RecipeManager.getRecipeFor(RecipeType<T>, RecipeInput, Level)`.
  `RecipeHolder<T>` = `{ResourceKey<Recipe<?>> key; T value()}`.
- **Melee attack**: `Mob.doHurtTarget(ServerLevel, Entity)` — the only variant.
  (Damage via `target.hurtServer(level, level.damageSources().mobAttack(mob), dmg)`.)
- **Starve damage**: `Level.damageSources().starve()` confirmed
  (`net.minecraft.world.damagesource.DamageSources`, field + builder `starve()`).
- **Components**: `DataComponents` moved to `net.minecraft.core.component.DataComponents`
  (NOT `world.item.components`). Confirmed members: `FOOD`, `WEAPON`,
  `EQUIPPABLE`, `PIERCING_WEAPON`, `KINETIC_WEAPON` (using the new Weapon-family
  types). `ATTRIBUTE_MODIFIERS` — verify exact member name at Phase-2/4 compile
  (the plan's `DataComponents.ATTRIBUTE_MODIFIERS` may be renamed).
- **ItemEntity**: `getOwner(): Entity` confirmed; `hasPickUpDelay()`,
  `setPickUpDelay(int)`, `setItem(ItemStack)` all present in class.
- **Navigation float**: `PathNavigation.setCanFloat(boolean)` / `.canFloat()`
  (PathNavigation:397) inherited by `GroundPathNavigation`.
- **Teleport**: `Entity.teleportTo(double,double,double)` confirmed (Entity:3248).
- **Command arg**: `ItemInput.createItemStack(int count, boolean trim) throws CommandSyntaxException`
  confirmed (`net.minecraft.commands.arguments.item.ItemInput`, :39).
- **Attributes**: `Mob.createMobAttributes()` static returns `AttributeSupplier.Builder`
  (Mob:163). PathfinderMob does not redefine; subclass adds `Attributes.*`.

## Corrections applied to the working plan (Phase 0/2 code)
1. Use `Identifier` everywhere; never `ResourceLocation`; use `KEY.identifier()` not `.location()`.
2. Persistence overrides take `ValueOutput`/`ValueInput`, NOT `CompoundTag`; route hunger/owner via the ValueOutput bag alongside `ContainerHelper.saveAllItems/loadAllItems`.
3. Damage hooks override `hurtServer(ServerLevel, DamageSource, float)`.
4. Recipe lookup via `ServerLevel.recipeAccess().getRecipes()` (real API).
5. `doHurtTarget(ServerLevel, Entity)` for combat.
6. `DataComponents` imports from `net.minecraft.core.component`.

## Still-open / verify at Phase-2 compile (not resolvable from source alone)
- Exact Fabric `Registry.register` helper signature (3-arg vanilla form known;
  Fabric convenience overload TBD).
- `DataComponents.ATTRIBUTE_MODIFIERS` exact member name (equipment tiering).
- `EntityType.Builder.build` needs the entity `.identifier()` to be free before
  registration; registration order in `CompanionRegistry.register()`.
- `getItemBySlot(EquipmentSlot)` / `setItemSlot(EquipmentSlot, ItemStack)` /
  `setDropChance(EquipmentSlot, float)` signatures on LivingEntity (verify).
- `Mob.setPathfindingMalus(PathType, float)`, `DefaultRandomPos.getPosAway`.
  (Present in vanilla; verify param order at compile.)
- `getLastHurtByMob()` / `tickCount` exposure on the companion entity.

These are all "open at compile" — Phase 0 build will surface any mismatch; each
substitution logged here as it lands.