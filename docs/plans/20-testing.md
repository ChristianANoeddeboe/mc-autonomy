# Plan 20 — Test suites + CI coverage

**Priority:** ⚙️ Engineering · **Effort:** M · **Depends on:** nothing (do early — everything after gets cheaper)

## Problem

Zero tests exist in either half of the project, and `.github/workflows/build.yml` only
runs `./gradlew build` for the Java side — the Python sidecar isn't even syntax-checked
in CI. The sidecar is the *easy* half to test and carries the most subtle logic
(parsing, fallback routing, config layering, memory persistence).

## Approach

### Python (highest value, do first)

Layout: `sidecar/tests/`, run with `pytest`; add `pytest`, `pytest-asyncio`, `respx`
(httpx mocking) to a new `sidecar/requirements-dev.txt`.

| Module | Key cases |
|---|---|
| `action_parser` | clean JSON; ```json fenced; prose-wrapped; truncated JSON → IDLE; invalid enum → IDLE; params preserved |
| `model_router` | Ollama OK → no Anthropic call; Ollama timeout → Anthropic called; both fail → IDLE with reason; (after Plan 05) schema sent |
| `config` | defaults only; JSON file overrides; env beats file; bad JSON falls back; type coercion (`"true"` → bool, str port → int) |
| `memory` | note persistence round-trip via `tmp_path`; deque cap on recent_decisions; (after Plan 06) per-entity isolation + path traversal rejection |
| `main` (FastAPI `TestClient`) | `/decide` stub mode returns IDLE; `/health`; `/memory` CRUD; invalid world state → 422 |

Gotcha to fix while writing tests: `config.CONFIG` is computed at import time —
tests need a `reload_config()` helper or `_load()` exposed for monkeypatching env vars.

### Java

- **Unit-testable logic first:** extract pure logic where cheap — e.g. Plan 04's
  `GoalParams` (param coercion/defaults) and Plan 07's watchdog arithmetic are plain
  JUnit. Add JUnit 5 via `testImplementation` in `build.gradle`.
- **Fabric gametest** (`fabric-gametest-api-v1`) for in-world behavior: spawn companion
  (attributes registered — Plan 01), assert goal transitions
  IDLE → set goal → complete/fail callbacks fire. Keep to a few smoke tests; gametests
  are slow to write and brittle across MC versions.
- Don't chase coverage on goal FSMs until the mod is verified spawning at all.

### CI (`.github/workflows/build.yml`)

Add a `sidecar` job alongside the existing `build` job:

```yaml
sidecar:
  runs-on: ubuntu-24.04
  steps:
    - uses: actions/checkout@v4
    - uses: actions/setup-python@v5
      with: { python-version: '3.12' }
    - run: pip install -r sidecar/requirements.txt -r sidecar/requirements-dev.txt
    - run: ruff check sidecar/          # lint (add ruff to dev reqs)
    - run: pytest sidecar/tests/ -q
```

Java job additionally runs `./gradlew test` (covered by `build`, but make it explicit
once tests exist). Wire `runGametest` in only after gametests exist and prove stable.

## Files to touch

- `sidecar/tests/` (new), `sidecar/requirements-dev.txt` (new), `sidecar/config.py` (reload helper)
- `src/test/java/...` (new), `build.gradle`
- `.github/workflows/build.yml`

## Acceptance criteria

- [ ] `pytest sidecar/tests -q` green locally and in CI; a broken parser edge case fails CI.
- [ ] `ruff check` green in CI.
- [ ] `./gradlew test` runs JUnit tests in CI.
- [ ] CI fails if either half breaks — not just the Java build.
