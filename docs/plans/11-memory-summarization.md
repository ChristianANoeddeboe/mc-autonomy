# Plan 11 — Memory summarization

**Priority:** 🟡 AI layer · **Effort:** M · **Depends on:** 06 (per-entity stores)

## Problem

`MemoryStore.persistent_notes` is an append-only list injected into every prompt. Over a
long-lived companion it grows without bound: prompts get slower and costlier, the local
model's effective context fills with stale trivia, and old notes crowd out new ones. The
same applies (less severely) to `player_preferences`.

## Approach

Cap the note list and compress overflow with the LLM itself, asynchronously, off the
`/decide` hot path.

### Policy

- `MAX_NOTES = 30` (config: `memory_max_notes`), `TARGET_AFTER_SUMMARY = 10`.
- When a note is added and the list exceeds `MAX_NOTES`, schedule a summarization task
  (`asyncio.create_task`) — never block the decision response on it.
- Summarization prompt: "Merge these companion memory notes into at most
  {TARGET} concise notes. Preserve player preferences, named places/coordinates, and
  standing instructions. Drop transient events." Route through the normal
  `model_router` fallback chain; use structured output (Plan 05) with a
  `list[str]` schema.
- Concurrency: one summarization in flight per entity (per-entity `asyncio.Lock`);
  notes added mid-summarization are appended after the compressed list replaces the old
  one (compress the snapshot, then re-append anything newer).
- Failure: if the LLM call fails, fall back to dumb truncation — keep the newest
  `MAX_NOTES` notes — so memory can never grow unbounded even with no LLM available.
- Audit: log the before/after note lists at INFO so lost information is diagnosable.

### Recent-decisions history

`recent_decisions` is already a bounded deque — no change. But add the last 3 decision
reasons to the prompt context (if not already) since compressed notes lose recency.

## Implementation steps

1. Add cap/config knobs to `config.py` (`memory_max_notes`, `memory_summary_target`).
2. Implement `MemoryStore.maybe_summarize()` + `summarize_notes()` in `memory.py` with
   the lock + snapshot/re-append logic.
3. Hook into `add_note()` (called from `/decide` and Plan 10's `/chat`).
4. Add `POST /memory/{entity_id}/summarize` endpoint to trigger manually for testing.
5. Tests: overflow triggers summarization; LLM failure truncates instead; notes added
   during summarization survive.

## Files to touch

- `sidecar/memory.py`, `sidecar/config.py`, `sidecar/main.py`, `sidecar/prompt_builder.py`

## Acceptance criteria

- [ ] Note list never exceeds `MAX_NOTES` regardless of LLM availability.
- [ ] `/decide` latency is unaffected by summarization (runs async).
- [ ] Player preferences and standing instructions survive compression in manual testing.
