# Plan 12 — Anthropic prompt caching

**Priority:** 🟡 AI layer · **Effort:** S · **Depends on:** 05 (call restructure)

## Problem

Every Anthropic fallback call resends the full system prompt (persona, rules, goal list,
param contract — the static bulk of the prompt) at full input-token price. Anthropic
prompt caching can serve the static prefix from cache: cache reads cost ~10% of base
input tokens, and latency drops too. For a fallback that fires in bursts (exactly when
Ollama is down), this is nearly free money.

## Approach

Split the prompt into a static prefix and dynamic suffix, and mark the static part with
`cache_control`.

1. **Verify the split.** `build_prompt` returns `(system, user)`. Everything varying
   per-request (world state JSON, memory context, last player message) must live in the
   `user` message; the `system` string must be byte-identical across calls (persona +
   rules + goal list + schema notes). Audit `prompt_builder.py` for anything dynamic in
   `system` (e.g. memory context) and move it to `user`.
2. **Mark the cache breakpoint.** In `_anthropic_complete`:
   ```python
   system=[{
       "type": "text",
       "text": system,
       "cache_control": {"type": "ephemeral"},
   }]
   ```
   If Plan 05's tool definition is in place, the `tools` block sits before `system` in
   cache order and is also static — the single breakpoint after `system` covers both.
3. **Minimum size caveat.** Caching requires a minimum prefix (~1024 tokens for
   haiku-class models on current API rules — check docs at implementation time). The
   current system prompt may be under that; if so this is a no-op that costs nothing,
   and it starts paying off as the persona/rules grow (Plans 04, 10, 14 all grow it).
4. **Observe.** Log `usage.cache_read_input_tokens` / `cache_creation_input_tokens`
   from the response at DEBUG so cache effectiveness is visible.

The same idea applies to Plan 10's `/chat` system prompt and Plan 11's summarizer.

## Files to touch

- `sidecar/model_router.py`
- `sidecar/prompt_builder.py`

## Acceptance criteria

- [ ] `system` content is byte-identical across consecutive requests (assert in a test).
- [ ] Second consecutive Anthropic call within the cache TTL logs
      `cache_read_input_tokens > 0` (when the prefix meets the minimum size).
- [ ] No behavior change to decisions themselves.
