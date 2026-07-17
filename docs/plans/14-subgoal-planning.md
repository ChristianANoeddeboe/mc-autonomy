# Plan 14 — Sub-goal plan decomposition

**Priority:** 🟡 AI layer · **Effort:** L · **Depends on:** 04 (params), 09 (versioning); benefits from 05

## Problem

The sidecar returns exactly one goal per round-trip. Multi-step intentions ("craft a
stone pickaxe" = gather wood → craft sticks → mine stone → craft pickaxe) require a full
LLM round-trip between every step, each with fresh context and a chance to wander off.
Result: slow, incoherent sequences and unnecessary LLM cost.

## Approach

Let the sidecar return a short ordered **plan** (1–3 goals) instead of a single goal.
The mod executes the queue locally, only calling back when the queue empties, a step
fails, or an event invalidates the plan.

### API (schema_version bump per Plan 09)

`GoalDecision` becomes (backward compatible — `goal` stays as the first step):

```jsonc
{
  "goal": "GATHER_WOOD",                // = plan[0], kept for compat
  "params": {"count": 4},
  "plan": [                              // NEW, optional, max 3 entries
    {"goal": "GATHER_WOOD", "params": {"count": 4}},
    {"goal": "CRAFT_ITEM",  "params": {"item": "stick"}},
    {"goal": "CRAFT_ITEM",  "params": {"item": "wooden_pickaxe"}}
  ],
  "reason": "...",
  "memory_note": null
}
```

### Execution semantics (mod side)

- New `GoalQueue` in `AIGoalPlanner` (or a thin `PlanExecutor` wrapper over
  `GoalExecutor`).
- Step **complete** → pop next step, start it, *no sidecar call*. Narrate step
  transitions ("Now: craft stick (2/3)").
- Step **fails** → discard remainder of queue, run the existing failure-recovery path
  (Plans 13 → AI escalation). A plan built on a failed premise is stale.
- **Interruptions:** reactive behaviors already pause/resume the active goal — the queue
  is unaffected. Planner triggers that indicate new information (player message,
  significant event) **clear the queue** and replan; the periodic fallback timer does
  NOT fire while a queue is progressing (reset it on each step completion).
- Queue length hard-capped at 3 on both sides — long plans go stale in Minecraft.

### Prompt

Update the system prompt: "You may return a plan of up to 3 goals when the next steps
are certain (e.g. gather-then-craft chains). Return a single goal when the world may
change your mind." Few-shot the pickaxe chain.

## Implementation steps

1. Sidecar: extend `GoalDecision` with `plan: list[PlanStep] | None` (+ validation:
   ≤3 steps, `plan[0]` consistent with `goal`); update prompt + structured-output schema.
2. Mod: parse `plan` array; add queue + semantics above to `AIGoalPlanner`.
3. `/companion status` shows remaining plan steps.
4. Metrics: log plan adherence (completed fully vs. invalidated) to judge whether 3 is
   the right cap.

## Files to touch

- `sidecar/models.py`, `sidecar/prompt_builder.py`, `sidecar/main.py`
- `src/main/java/com/example/companion/goal/AIGoalPlanner.java`
- `src/main/java/com/example/companion/command/CompanionCommands.java`

## Acceptance criteria

- [ ] A gather→craft→craft chain executes with exactly one `/decide` call.
- [ ] A mid-plan player instruction abandons the queue and replans immediately.
- [ ] A failed step never continues into later steps built on its output.
- [ ] Old sidecar (no `plan` field) keeps working against a new mod and vice versa.
