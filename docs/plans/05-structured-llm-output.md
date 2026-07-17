# Plan 05 — Structured LLM output (schema-enforced JSON)

**Priority:** 🟠 Robustness · **Effort:** M · **Depends on:** nothing (12 builds on this)

## Problem

`action_parser.py` regex-scrapes JSON out of freeform LLM text (strip code fences, grab
first `{...}` block, hope). A 7B local model will regularly produce prose, truncated JSON,
or invalid enum values — every such failure silently degrades to `IDLE`. Both backends
support schema enforcement natively; we should use it.

## Approach

Enforce the `GoalDecision` schema at the API level on both backends, keeping
`parse_and_validate` as a last-resort safety net.

### Ollama

Ollama's `/api/chat` accepts `"format": <json-schema>` for structured outputs. Pass
`GoalDecision.model_json_schema()` directly:

```python
payload = {
    "model": CONFIG["ollama_model"],
    "messages": [...],
    "format": GoalDecision.model_json_schema(),
    "stream": False,
}
```

### Anthropic

Use **tool use** with `tool_choice` forced to a single `decide_goal` tool whose
`input_schema` is the `GoalDecision` schema:

```python
message = await client.messages.create(
    model=CONFIG["anthropic_model"],
    max_tokens=512,
    system=system,
    messages=[{"role": "user", "content": user}],
    tools=[{
        "name": "decide_goal",
        "description": "Choose the companion's next goal.",
        "input_schema": GoalDecision.model_json_schema(),
    }],
    tool_choice={"type": "tool", "name": "decide_goal"},
)
# result: message.content[0].input is already a dict matching the schema
```

## Implementation steps

1. Export a cleaned JSON schema from `GoalDecision` (Pydantic emits `$defs` for the
   `GoalType` enum — verify Ollama accepts it; inline the enum if not).
2. Update `_ollama_complete` to send `format` and return the (already-JSON) content.
3. Update `_anthropic_complete` to use forced tool use and return
   `json.dumps(tool_input)` — or better, refactor both helpers to return a `dict` and
   make `parse_and_validate` accept `str | dict`.
4. Keep the regex fallback path in `parse_and_validate` for models/backends that ignore
   the format hint.
5. Simplify the system prompt: with schema enforcement, the "Return ONLY valid JSON"
   scaffolding can shrink (keep the goal semantics, drop the format lecture).
6. Tests (see Plan 20): mock both transports, assert schema is sent, assert enum
   violations are impossible via the API path and still safely IDLE via the fallback path.

## Files to touch

- `sidecar/model_router.py`
- `sidecar/action_parser.py`
- `sidecar/prompt_builder.py`
- `sidecar/models.py` (schema export helper)

## Acceptance criteria

- [ ] With Ollama running, 50 consecutive `/decide` calls produce zero parse-failure
      IDLE fallbacks.
- [ ] Anthropic fallback path returns a valid `GoalDecision` via tool use.
- [ ] A backend that ignores the schema still degrades gracefully through the old parser.
