# Plan 10 — Companion chat replies

**Priority:** 🟡 AI layer · **Effort:** M · **Depends on:** 09 (API change) · pairs with 06

## Problem

Saying "companion: what are you doing?" today only triggers a *replan* — the message is
stuffed into `last_player_message` and the AI picks a new goal. The companion cannot
answer questions, explain itself, or negotiate. This is the single highest
fun-per-effort feature missing, and the narration plumbing (`narrateGoal` broadcasting
`[Companion] ...` to nearby players) already exists to deliver the reply.

## Approach

New sidecar endpoint `POST /chat` that produces a conversational reply *and* decides
whether the message warrants a replan. The mod routes prefix-addressed chat there first.

### API (schema_version bump per Plan 09)

```jsonc
// POST /chat
{
  "schema_version": 2,
  "entity_id": "uuid",
  "player_name": "Steve",
  "message": "what are you doing?",
  "world_state": { /* same WorldInfo payload as /decide */ }
}
// → response
{
  "reply": "Chopping oak for a shelter before dusk — we're low on planks.",
  "should_replan": false,
  "memory_note": null
}
```

`should_replan` lets the model distinguish questions/banter (reply only) from
instructions ("go get food" → reply *and* replan). On replan, the follow-up `/decide`
already sees the message via `last_player_message`.

### Prompt

Separate, shorter system prompt built from the same `personality.json` (name, style),
plus current goal, objective + milestone, and the entity's memory context. Hard cap the
reply length in the prompt (≤ 2 sentences) — chat spam kills the charm.

## Implementation steps

1. **Sidecar:** add `ChatRequest`/`ChatReply` models; `/chat` endpoint; `build_chat_prompt`
   in `prompt_builder.py`; route through the same `model_router` fallback chain. Record
   the exchange in the entity's memory (rolling, not persistent notes unless
   `memory_note` set).
2. **Mod:** `SidecarClient.postChatAsync(...)`. In `CompanionEntity.onPlayerMessage`,
   call `/chat` instead of triggering the planner directly; on response, narrate
   `[Companion] <reply>` (reuse/generalize `narrateGoal`'s broadcast into
   `narrate(String)`), and call `goalPlanner.onPlayerMessage(message)` only when
   `should_replan` is true.
3. **Fallbacks:** `/chat` timeout or error → fall back to today's behavior (silent
   replan trigger) so a dead LLM never makes the companion *less* responsive than now.
   Stub mode returns a canned reply mentioning stub mode.
4. **Config:** `chatRepliesEnabled` in `CompanionConfig` (default true).

## Files to touch

- `sidecar/main.py`, `sidecar/models.py`, `sidecar/prompt_builder.py`, `sidecar/memory.py`
- `src/main/java/com/example/companion/net/SidecarClient.java`
- `src/main/java/com/example/companion/entity/CompanionEntity.java`
- `src/main/java/com/example/companion/CompanionConfig.java`

## Acceptance criteria

- [ ] "companion: what are you doing?" gets an in-character chat answer and does NOT
      interrupt the current goal.
- [ ] "companion: go gather wood" gets a short acknowledgment AND switches the goal.
- [ ] Sidecar down → behavior degrades to exactly today's (replan trigger, no reply).
- [ ] Replies never exceed a couple of sentences.
