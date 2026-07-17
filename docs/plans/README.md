# Improvement Plans

Implementation plans for the improvement roadmap, derived from a full codebase review
(2026-07-17). Each plan is self-contained: problem, approach, files to touch, and
acceptance criteria. Pick one up, implement it, check it off.

## Priority: 🔴 Must-fix (mod likely cannot complete a play session without these)

| # | Plan | Effort |
|---|------|--------|
| 01 | [Register entity default attributes](01-entity-attributes.md) | S |
| 02 | [Client-side entity renderer](02-client-renderer.md) | M |
| 03 | [NBT persistence for inventory / hunger / objective](03-nbt-persistence.md) | M |
| 04 | [Pass goal params from sidecar to goals](04-goal-params-passthrough.md) | M |

## Priority: 🟠 Robustness

| # | Plan | Effort |
|---|------|--------|
| 05 | [Structured LLM output (schema-enforced JSON)](05-structured-llm-output.md) | M |
| 06 | [Per-entity sidecar memory](06-per-entity-memory.md) | M |
| 07 | [Watchdog for stuck planner requests](07-request-watchdog.md) | S |
| 08 | [Sidecar health check + backoff](08-sidecar-health-and-backoff.md) | M |
| 09 | [API contract versioning](09-api-contract-versioning.md) | S |

## Priority: 🟡 AI layer

| # | Plan | Effort |
|---|------|--------|
| 10 | [Companion chat replies](10-companion-chat-replies.md) | M |
| 11 | [Memory summarization](11-memory-summarization.md) | M |
| 12 | [Anthropic prompt caching](12-anthropic-prompt-caching.md) | S |
| 13 | [Objective-aware failure recovery](13-objective-aware-recovery.md) | S |
| 14 | [Sub-goal plan decomposition](14-subgoal-planning.md) | L |

## Priority: 🟢 Gameplay

| # | Plan | Effort |
|---|------|--------|
| 15 | [Equipment use (armor + tools)](15-equipment-use.md) | M |
| 16 | [Item exchange with the player](16-item-exchange.md) | M |
| 17 | [Death handling](17-death-handling.md) | M |
| 18 | [Container interaction / home storage](18-container-interaction.md) | L |
| 19 | [Navigation improvements](19-navigation-improvements.md) | L |

## Priority: ⚙️ Engineering hygiene

| # | Plan | Effort |
|---|------|--------|
| 20 | [Test suites + CI coverage](20-testing.md) | M |
| 21 | [Template cleanup & rebranding](21-template-cleanup.md) | M |
| 22 | [Single source of truth for config](22-config-single-source.md) | S |
| 23 | [Dev ergonomics (compose, make, quickstart)](23-dev-ergonomics.md) | S |

**Effort key:** S = under half a day · M = half a day to two days · L = multi-day / staged.

## Suggested order

1. **01 → 04** first, as one PR — without them the mod likely crashes on spawn and the
   AI cannot parameterize goals.
2. **20 + 21** next — a clean, tested base makes everything after cheaper.
3. **05, 10** — the two biggest wins for how the mod actually feels.
4. Everything else on demand.

## Dependencies between plans

- 03 (NBT) should land before 06 (per-entity memory) so entity UUIDs are stable across restarts.
- 09 (versioning) should land before or with 10 and 14, which both change the API surface.
- 12 (prompt caching) is trivial after 05 restructures the Anthropic call.
- 16 and 18 build on 15's equipment/inventory helpers.
