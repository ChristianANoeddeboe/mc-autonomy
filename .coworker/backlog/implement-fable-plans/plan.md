# Implement Fable Plans — parent tracker

Parent tracking plan for the 23 improvement plans in `docs/plans/`. This plan does not
implement anything itself: each child plan gets its own `/cw-plan` run. Phases mirror the
priority tiers in `docs/plans/README.md`, reshaped by the 2026-07-21 audit, with the lower
tiers treated as a living backlog.

Per-child gate: `gradlew build` green, sidecar pytest green, and a manual in-game session.

**Audit result (2026-07-21, base `d32c401` + uncommitted work):** 1 done · 9 partial · 13 not-started.
Full per-plan detail lives in `docs/plans/README.md` — that table is the source of truth,
not the checkboxes inside individual plan files.

Review pass corrected two statuses the first audit called not-started: **08** (the sidecar
half — `GET /health` at `sidecar/main.py:50` — already exists; only mod-side polling and
backoff are missing) and **13** (objective context already rides in every `/plan` request
via `ObjectiveTracker.serialize`; only the heuristic recovery table is absent).

**Baseline verified 2026-07-21:** `./gradlew build` → BUILD SUCCESSFUL; `uv run pytest` in
`sidecar/` → 16 passed.

**Standing instruction for every child plan:** read the "Divergences" section of
`docs/plans/README.md` first. `AIGoalPlanner`, `ActionExecutor`, `SidecarClient` and the
`/decide` endpoint no longer exist — the real shape is the `plan/` package
(`PlanExecutor`, `CompanionPlan`, `PlanStep`) + `objective/` layer, over `POST /plan` and
`POST /step_complete`. Plans 04, 07, 09, 10, 13, 14 all cite the dead shape.

## Phase 0: Audit & Reconcile — mostly complete

**Goal:** Every plan in `docs/plans/` carries an accurate status so no child re-plans finished work.

**Success criteria:**
- [x] Each of the 23 plans marked done / partial / not-started against the actual tree
- [x] Partials list exactly which acceptance criteria remain
- [x] Uncommitted working-tree work is committed or explicitly parked
- [x] `gradlew build` green and sidecar pytest green as a baseline

**Tasks:**
- [x] Commit the uncommitted main-tree work — branch `refactor/plan-executor-architecture`, commit `0698e66`, 59 files
- [x] Audit plans 01, 02, 03, 20, 21 against the code that exists
- [x] Audit the remaining 18 plans for accidental overlap
- [x] Record status per plan in `docs/plans/README.md`
- [x] Run a baseline build + test (build green, 16 sidecar tests pass)
- [ ] Run one manual in-game session against the baseline
- [ ] Land the `implement-fable-plans` worktree branch before committing main-tree work, so the audited `docs/plans/README.md` does not fork

**Divergence guard:** the audited `docs/plans/README.md` exists **only** on the
`implement-fable-plans` worktree branch. The main tree still holds the pre-audit copy. Do
not edit the main-tree copy — merge this branch instead, or the two versions fork
permanently.

## Phase 1: Must-fix remainder (02, 03)

**Goal:** Companion looks like itself and survives the world — the only must-fix work the audit
found still outstanding.

Audit reshaped this tier: **01 is done** (attributes registered). **04 dropped out** — param
passthrough already works through `PlanExecutor`; only polish remains, moved to Phase 4.

**Success criteria:**
- [ ] Companion renders with a dedicated texture on a biped/player model, not the vanilla zombie
- [ ] Companion never despawns when the player walks away
- [ ] `lastPlayerMessage` survives a restart alongside inventory/hunger/objective
- [ ] Manual in-game session: spawn, walk away, return, restart server — companion intact

**Tasks:**
- [ ] Run `/cw-plan` for plan 02, scoped to the two remaining criteria (texture + biped model), noting the client entrypoint lives in `src/main`, not a `src/client` sourceset
- [ ] Run `/cw-plan` for plan 03, scoped to despawn-prevention + `lastPlayerMessage`, targeting `CompanionPersistentData`/attachments rather than the plan's NBT hooks
- [ ] Implement both child plans
- [ ] Manual in-game verification of the tier

## Phase 2: Clean Base (21, 20, 22, 23)

**Goal:** Tested, rebranded, single-source-of-truth base so later tiers are cheaper.

Reordered: **21 goes first** — it is a 37-file package rename, and every later plan that
touches Java paths gets cheaper once it lands, more expensive if it doesn't.

**Success criteria:**
- [ ] `com.example` package, `maven_group`, and `assets/modid/` icon path all replaced with real project identity
- [ ] Root `README.md` describes this project, not the Fabric template; `fabric.mod.json` `authors` populated
- [ ] Sidecar tests cover `action_parser`, `model_router`, `config`, `memory`, and `main` via `TestClient`
- [ ] Java test infrastructure exists (JUnit dependency + `src/test`) with at least smoke coverage
- [ ] CI runs `pytest` and `ruff` alongside `./gradlew build`
- [ ] Config has one source of truth across mod and sidecar
- [ ] Build + tests runnable from one documented command

**Tasks:**
- [ ] Run `/cw-plan` for 21 and implement it before the rest of this tier
- [ ] Run `/cw-plan` for 20, scoped to the five remaining gaps (module coverage, `test_manual.sh` wiring, sidecar CI job, Java test infra, `config.py` reload helper)
- [ ] Run `/cw-plan` for 22 and 23
- [ ] Implement each child plan
- [ ] Manual in-game verification after the rename

## Phase 3: Robustness (05–09)

**Goal:** Sidecar failures degrade gracefully instead of hanging or corrupting state.

05, 06, 07, 09 are not-started; **08 is partial** — `GET /health` already exists sidecar-side,
so its child plan is mod-side only (poll, track consecutive failures, back off). 07 and 09
cite the dead `/decide` shape and must be re-authored against `/plan` + `/step_complete`
before implementation. Sequence 08's mod-side work after 07 — both wrap the same call path.

**Success criteria:**
- [ ] Schema-enforced LLM output, no free-form parse failures
- [ ] Per-entity memory keyed on stable UUIDs
- [ ] Stuck planner requests time out and recover
- [ ] Sidecar down → backoff, no companion freeze
- [ ] API version negotiated between mod and sidecar
- [ ] Token baseline captured before 05 restructures the Anthropic call, so 12 can be measured

**Tasks:**
- [ ] Capture a token/cost baseline for the current prompt path before touching 05
- [ ] Run `/cw-plan` for 05, 06, 07, 08, 09 (03 lands before 06; 09 before 10/14)
- [ ] Implement each child plan
- [ ] Manual in-game test with the sidecar deliberately killed mid-session

## Phase 4: AI Layer (10–13, plus 04 and 14 polish)

**Goal:** Companion feels intelligent — talks, remembers, recovers, decomposes goals.

Absorbs the polish left over from 04 and 14, which the audit found functionally delivered
by `PlanExecutor`. **13 is partial, not greenfield:** every replan already carries objective
context, so its child plan must first answer whether the heuristic `ObjectiveRecovery` table
is still worth building at all, or whether the existing LLM escalation path suffices.

**Success criteria:**
- [ ] Companion replies in chat in character
- [ ] Memory summarizes instead of growing unbounded
- [ ] Prompt caching cuts token cost measurably against the Phase 3 baseline
- [ ] Failures recover with objective awareness
- [ ] `/companion goal <type> [key=value ...]` manual override works
- [ ] Malformed AI params fall back to defaults instead of throwing `ClassCastException`
- [ ] `/companion status` shows remaining plan steps; plan length is clamped on both sides

**Tasks:**
- [ ] Run `/cw-plan` for 10, 11, 12, 13
- [ ] Run `/cw-plan` for the 04 + 14 polish remainder as one combined child plan
- [ ] Implement each child plan
- [ ] Manual in-game session judging conversational + planning quality

## Phase 5: Gameplay (15–19) — living backlog

**Goal:** Companion participates in gameplay: gear, trading, death, storage, movement.

15–18 not-started; 19 partial (only an ad-hoc water malus + swim-up reflex exist — stages 2
and 3 and the `terraformingEnabled` flag are absent).

**Success criteria:**
- [ ] Each of 15–19 is either implemented, re-scoped, or explicitly dropped
- [ ] Tier status reflected in `docs/plans/README.md`

**Tasks:**
- [ ] Re-prioritize 15–19 after Phase 4 lands
- [ ] Run `/cw-plan` for each surviving plan (15 before 16 and 18)
- [ ] Implement each child plan
- [ ] Manual in-game verification per plan

## Decisions & Risks

| Decision | Options Considered | Chosen | Reason |
|----------|--------------------|--------|--------|
| Role of this plan | Implement the 23 plans directly here / parent tracker delegating to child cw-plans | Parent tracker | 23 plans too large for one plan; each needs its own Q&A |
| Handling existing overlap | Run 01/02/03/20/21 as written / audit first | Audit first | Paid off immediately — audit removed 01 and 04 from the must-fix tier |
| Tier commitment | Commit to all 23 / living tracker | Living tracker | Gameplay + hygiene priorities expected to shift after AI layer lands |
| Ordering of hygiene tier | README order (05, 10 before 20/21) / clean base first | Clean base first (Phase 2) | README itself notes a tested base makes later work cheaper |
| Fate of plans 04 and 14 | Implement as written / treat as delivered / polish-only remainder | Polish-only, folded into Phase 4 | `PlanExecutor` already delivers param passthrough and multi-step execution; only CLI/UX gaps remain |
| Persistence approach for 03 | Follow plan's NBT hooks / keep the shipped attachment approach | Keep attachments | `CompanionPersistentData` already works and serializes into entity NBT anyway; rewriting is churn |
| Order within Phase 2 | 20 first / 21 first | 21 first | 37-file package rename makes every later Java-touching plan cheaper, and conflicts worse if deferred |

**Risks:**
- Six plan docs (04, 07, 09, 10, 13, 14) cite `AIGoalPlanner` / `ActionExecutor` / `/decide`, all deleted — a child plan authored without reading the divergence notes will plan against fiction
- The Phase 0 audit was run against uncommitted work; if that work is reverted rather than committed, every status in `docs/plans/README.md` becomes wrong at once
- The audited README lives only on the worktree branch while the main tree keeps the pre-audit copy — editing both forks them permanently; land the branch first
- The first audit pass called 08 and 13 not-started when both were partial; treat every ⬜ as "verify before planning", not as proven-absent
- Plan 21's 37-file rename will conflict with any in-flight branch that touches Java — sequence it, don't parallelize it
- Plan dependencies (03→06, 09→10/14, 15→16/18) break if tiers are reordered ad hoc
- Manual in-game test gate is unautomatable — slow, and easy to skip under momentum
- `minecraft-ai-companion-design.md` remains stale vs. the code and was not corrected by this audit
- Phase 5 as a living backlog risks indefinite drift with no forcing function to close it
- Prompt-caching gains (12) can't be proven without the pre-05 token baseline; if Phase 3 skips it, 12's acceptance criterion is unmeasurable

## Deferred Ideas

(none)
