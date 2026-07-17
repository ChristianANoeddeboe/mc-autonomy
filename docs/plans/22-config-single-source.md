# Plan 22 — Single source of truth for config

**Priority:** ⚙️ Engineering · **Effort:** S · **Depends on:** 08 (health probe carries the data)

## Problem

Connection settings are defined independently on both sides and can silently desync:

- **Mod** (`config/companion.json` via `CompanionConfig`): `sidecarHost`, `sidecarPort`.
- **Sidecar** (`sidecar_config.json` + env): `port`, plus its own unrelated knobs.

A user who changes the sidecar port but not the mod config (or vice versa) gets a
companion that idles forever with only connection-refused log spam. There are also two
`8765` literals and two config file formats to document.

## Approach

Full unification (one shared config file) isn't worth it — the two processes have
legitimately different knobs and lifecycles. Instead: **detect and loudly report
mismatches, and document the split clearly.**

1. **Config echo in `/health`.** Sidecar's `/health` response gains an echo of its
   effective config (non-secret subset): `port`, `stub_mode`, `ollama_model`,
   `anthropic_model`, `schema_version` (Plan 09). Note: if the mod *reached* `/health`,
   host/port are by definition right — the echo exists for the startup log line and for
   humans debugging with `curl`.
2. **The real mismatch signal is the probe failing.** Extend Plan 08's startup probe
   failure log with actionable guidance:
   `Sidecar unreachable at localhost:8765 — check (a) sidecar is running, (b) 'port' in sidecar_config.json matches 'sidecarPort' in config/companion.json.`
3. **Uvicorn port drift.** `sidecar_config.json`'s `port` is only advisory today — the
   real port comes from the `uvicorn` CLI. Fix: add `sidecar/run.py` launcher
   (`python run.py` → `uvicorn.run(app, port=CONFIG["port"])`) and document it as THE
   way to start the sidecar, so the config value is authoritative. Keep raw uvicorn
   working for dev.
4. **Documentation table.** One table in the README (Plan 21) listing every knob, which
   side owns it, file + env var, and default — replacing the current situation where
   the knobs are only discoverable by reading `config.py` and `CompanionConfig.java`.

## Files to touch

- `sidecar/main.py` (health echo), `sidecar/run.py` (new)
- `src/main/java/com/example/companion/CompanionMod.java` (probe failure guidance)
- `README.md` (config table — fold into Plan 21's rewrite)
- `sidecar_config.json.example` (comment pointing at run.py)

## Acceptance criteria

- [ ] `curl :8765/health` shows the sidecar's effective config at a glance.
- [ ] Wrong-port setups produce a log line that names both config files and keys.
- [ ] `python sidecar/run.py` starts the server on the configured port.
- [ ] README documents every config knob on both sides in one table.
