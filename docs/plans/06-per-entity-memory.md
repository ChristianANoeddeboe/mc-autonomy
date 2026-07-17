# Plan 06 — Per-entity sidecar memory

**Priority:** 🟠 Robustness · **Effort:** M · **Depends on:** 03 (stable entity UUIDs across restarts)

## Problem

The sidecar has exactly one global `MemoryStore` (`main.py` module level). Every request
already carries `entity_id`, but notes, preferences, and decision history are shared by
all companions. Two companions — or a new companion after the old one died — will read
each other's memories and pollute each other's context.

## Approach

Replace the singleton with a registry keyed by `entity_id`, each with its own persistence
file.

```python
class MemoryRegistry:
    def __init__(self, base_dir: Path):
        self._stores: dict[str, MemoryStore] = {}

    def for_entity(self, entity_id: str) -> MemoryStore:
        # lazy-create; each store persists to memory/{entity_id}.json
```

## Implementation steps

1. Add `MemoryRegistry` to `memory.py`; `MemoryStore` gains a `path` constructor arg
   (currently hardcoded to `memory.json`).
2. Storage layout: `sidecar/memory/{entity_id}.json`. Sanitize `entity_id` to a safe
   filename (UUIDs already are; reject anything containing path separators — never trust
   request input in a filesystem path).
3. Migration: on startup, if legacy `memory.json` exists, load it as the store for the
   first entity that connects, then rename it to `memory/_legacy.json` (one-time, logged).
4. Update endpoints:
   - `/decide` → `memory = registry.for_entity(world_state.entity_id)`
   - `GET /memory` → list all entities + note counts; `GET /memory/{entity_id}` → detail
   - `DELETE /memory/{entity_id}` → clear one; `DELETE /memory` → clear all
5. Cap: keep at most N (default 20) entity stores in RAM (LRU); files stay on disk.
6. Add `memory/` to `.gitignore`.

## Files to touch

- `sidecar/memory.py`
- `sidecar/main.py`
- `.gitignore`

## Acceptance criteria

- [ ] Two companions with different UUIDs get fully separate notes/preferences/history.
- [ ] Memory survives sidecar restart, keyed by the same entity UUID.
- [ ] A malicious `entity_id` like `../../etc/passwd` is rejected with HTTP 422.
- [ ] Existing single-entity `memory.json` installs migrate without data loss.
