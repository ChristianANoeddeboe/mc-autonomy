# Plan 02 — Client-side entity renderer

**Priority:** 🔴 Must-fix · **Effort:** M · **Depends on:** 01

## Problem

There is no renderer registered for `COMPANION_ENTITY_TYPE`. The client half of the repo
still only contains the template's `ExampleModClient`, and `fabric.mod.json` declares no
`client` entrypoint at all. When the companion spawns, the client will crash (missing
renderer for entity type) or fail to display it.

## Approach

Register a humanoid renderer with a bundled placeholder texture. Reuse the vanilla player
model (`PlayerEntityModel` / biped model layer) so no custom modeling is needed — the
companion looks like a Steve-style NPC until custom art exists.

## Implementation steps

1. **Client entrypoint.** Create `src/client/java/com/example/companion/client/CompanionModClient.java`
   implementing `ClientModInitializer`, and add to `fabric.mod.json`:
   ```json
   "entrypoints": {
     "main": ["com.example.companion.CompanionMod"],
     "client": ["com.example.companion.client.CompanionModClient"]
   }
   ```
2. **Renderer.** Create `CompanionEntityRenderer extends MobEntityRenderer<CompanionEntity, PlayerEntityModel<CompanionEntity>>`
   (or `BipedEntityRenderer` subclass, whichever fits the target mappings) using the vanilla
   player model layer (`EntityModelLayers.PLAYER`). If the render-state refactor in this
   Minecraft version requires an `EntityRenderState`, add the minimal state class.
3. **Texture.** Add `assets/companion/textures/entity/companion.png` — a recolored copy of a
   64×64 player skin (CC0 skin or a simple recolor) so the companion is visually distinct.
   `getTexture(...)` returns `Identifier.of("companion", "textures/entity/companion.png")`.
4. **Register.** In `CompanionModClient.onInitializeClient()`:
   ```java
   EntityRendererRegistry.register(CompanionMod.COMPANION_ENTITY_TYPE, CompanionEntityRenderer::new);
   ```
5. **Asset namespace.** While here, note assets currently live under `assets/modid/` — full
   cleanup is Plan 21, but the new texture goes under `assets/companion/` from the start.
6. Verify in `runClient`: spawn, walk around it, confirm model + texture render and animate.

## Files to touch

- `src/client/java/com/example/companion/client/CompanionModClient.java` (new)
- `src/client/java/com/example/companion/client/render/CompanionEntityRenderer.java` (new)
- `src/main/resources/fabric.mod.json`
- `src/main/resources/assets/companion/textures/entity/companion.png` (new)

## Acceptance criteria

- [ ] Companion is visible in-game with the bundled texture, no client crash.
- [ ] Walking/attacking animations play (inherited from the biped model).
- [ ] Works in both singleplayer and when connected to a dedicated server.
