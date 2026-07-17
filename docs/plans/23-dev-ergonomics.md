# Plan 23 — Dev ergonomics (compose, make, quickstart)

**Priority:** ⚙️ Engineering · **Effort:** S · **Depends on:** benefits from 21, 22

## Problem

A working session currently requires: pip environment + uvicorn invocation with the
right flags, an Ollama daemon with the right model pulled, `STUB_MODE` toggling, and a
Gradle client — each documented only in scattered docstrings and the design doc. Every
piece of friction here is paid by every contributor (and future-you) every session.

## Approach

### `docker-compose.yml` (repo root) — sidecar + Ollama

```yaml
services:
  sidecar:
    build: ./sidecar            # new small Dockerfile: python:3.12-slim + reqs
    ports: ["8765:8765"]
    environment:
      - STUB_MODE=${STUB_MODE:-false}
      - OLLAMA_BASE_URL=http://ollama:11434
      - ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY:-}
    volumes:
      - ./sidecar/memory:/app/memory   # persist memory across restarts
    depends_on: [ollama]
  ollama:
    image: ollama/ollama
    volumes: [ollama-models:/root/.ollama]
    # GPU passthrough notes in comments (deploy.resources / --gpus)
volumes:
  ollama-models:
```

Note in comments: Minecraft itself always runs on the host (Gradle), only the AI stack
is containerized. Model pull is a documented one-time `docker compose exec ollama
ollama pull qwen2.5-coder:7b`.

### `Makefile` (repo root)

| Target | Does |
|---|---|
| `make sidecar` | `cd sidecar && uvicorn main:app --port 8765 --reload` (venv-aware) |
| `make sidecar-stub` | same with `STUB_MODE=true` |
| `make ai` | `docker compose up` (sidecar + ollama) |
| `make client` | `./gradlew runClient` |
| `make server` | `./gradlew runServer` |
| `make test` | pytest + `./gradlew test` (Plan 20) |
| `make check` | ruff + build, what CI runs |

### Supporting files

- `sidecar/Dockerfile` (slim, non-root, `CMD python run.py` per Plan 22).
- `.env.example` at root: `ANTHROPIC_API_KEY=`, `STUB_MODE=false` — compose picks it up;
  add `.env` to `.gitignore`.
- README quickstart (Plan 21) reduced to: `cp .env.example .env`, `make ai`,
  `make client`, `/companion spawn`.

## Files to touch

- `docker-compose.yml`, `Makefile`, `.env.example` (all new, repo root)
- `sidecar/Dockerfile` (new)
- `.gitignore`, `README.md`

## Acceptance criteria

- [ ] Fresh clone → `make ai` + `make client` reaches an in-game companion making real
      AI decisions with no other setup beyond `.env` and the one-time model pull.
- [ ] `make sidecar-stub` supports LLM-free development.
- [ ] Sidecar memory survives `docker compose down && up`.
- [ ] `make test` mirrors CI exactly.
