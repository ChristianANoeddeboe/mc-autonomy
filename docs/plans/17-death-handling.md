# Plan 17 — Death handling

**Priority:** 🟢 Gameplay · **Effort:** M · **Depends on:** 03, 06

## Problem

Companion death is currently undefined behavior by omission: the entity dies like any
mob, its `SimpleInventory` evaporates (nothing overrides drop logic), the sidecar's
memory for that UUID is orphaned forever, and the player's only recourse is
`/companion spawn` — a brand-new companion with amnesia. Death should be a meaningful
but fair event.

## Approach

### Drops

Override the death drop hook (`dropInventory` / `drop` in current mappings) to scatter
the full carried inventory + equipped gear at the death position, like a player. Config
flag `keepInventoryOnDeath` (default false) suppresses drops for a planned respawn
mechanic.

### Death notification

Broadcast to nearby players (and log): `[Companion] <name> died: <damage source>` using
the vanilla death message infrastructure. The player deserves to know *why*.

### Memory fate

Memory is keyed by entity UUID (Plan 06). On death:

- Do **not** delete the sidecar memory file. A future respawn/rebind feature reuses it.
- Mod fires a best-effort `POST /memory/{entity_id}/event` note: "I died to {source} at
  {pos}" — the next companion bound to that memory can reference it, and it's a great
  personality moment ("last time a creeper got me here").

### Respawn/rebind (follow-up, design-gated)

Options, pick ONE when implementing (decision deliberately deferred):

1. **Free respawn:** `/companion respawn` spawns a new entity *bound to the old UUID's
   memory* (pass old UUID as a `bound_memory_id` in world state; needs a sidecar alias
   map). Cheapest, no items back.
2. **Item-gated:** craftable "companion core" dropped on death; using it respawns the
   companion with memory (and inventory if `keepInventoryOnDeath`). Most Minecraft-y.

Tier 1 of this plan = drops + notification + memory note. Respawn is tier 2.

## Implementation steps

1. Override drop logic in `CompanionEntity`; respect `keepInventoryOnDeath`.
2. Death broadcast via `onDeath` override (guard server side).
3. Sidecar: `POST /memory/{entity_id}/event` endpoint (trivial note append).
4. `SidecarClient.postMemoryEventAsync` fired from `onDeath` (best-effort, no retry).
5. Config: `keepInventoryOnDeath` in `CompanionConfig`.

## Files to touch

- `src/main/java/com/example/companion/entity/CompanionEntity.java`
- `src/main/java/com/example/companion/net/SidecarClient.java`
- `src/main/java/com/example/companion/CompanionConfig.java`
- `sidecar/main.py`, `sidecar/memory.py`

## Acceptance criteria

- [ ] Death scatters all carried + equipped items at the death site.
- [ ] Nearby players see a death message naming the cause.
- [ ] Sidecar memory file survives death and contains the death note.
- [ ] `keepInventoryOnDeath=true` suppresses drops without errors.
