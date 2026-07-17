# Plan 21 — Template cleanup & rebranding

**Priority:** ⚙️ Engineering · **Effort:** M · **Depends on:** nothing (coordinate with 02 — asset moves overlap)

## Problem

The repo still wears its Fabric-example-template skin:

- `README.md` says "Fabric Example Mod" and describes the template, not this project.
- Dead template code compiled into the jar: `ExampleMod`, `ExampleMixin`,
  `ExampleModClient`, `ExampleClientMixin`.
- Placeholder namespaces: `modid.mixins.json`, `modid.client.mixins.json`,
  `assets/modid/icon.png`, everything under `com.example`.
- `gradle.properties` still has `maven_group=com.example`.

Cosmetic, but it confuses contributors, pollutes the jar, and `com.example` shouldn't
ship in a published mod.

## Approach

One focused PR, no behavior changes, ordered so each step compiles:

1. **Delete template leftovers:** `ExampleMod.java`, `ExampleMixin.java`,
   `ExampleModClient.java`, `ExampleClientMixin.java`, `modid.mixins.json`,
   `modid.client.mixins.json`. Verify `fabric.mod.json` references none of them
   (it already only lists `companion.mixins.json` + `CompanionMod`) and check
   `companion.mixins.json` for a package pointing at `com.example.mixin` — fix if so.
2. **Asset namespace:** move `assets/modid/icon.png` → `assets/companion/icon.png`;
   update the `icon` path in `fabric.mod.json`.
3. **Package rename:** `com.example.companion` → the real group (suggest
   `dev.noeddeboe.companion` or similar — **owner's call, decide before merging**).
   Pure IDE/`git mv` + import rewrite. Update: `fabric.mod.json` entrypoints,
   `companion.mixins.json` package field, `gradle.properties` `maven_group`,
   `build.gradle` loom `mods` block if needed.
4. **README rewrite:** the design doc already contains 90% of the needed prose. New
   README: one-paragraph pitch, architecture diagram (lift from design doc), quickstart
   (sidecar + client, lifted from design doc "Running Locally" + STUB_MODE explanation
   from `main.py`'s docstring), link to `minecraft-ai-companion-design.md` and
   `docs/plans/`, license note. Move the design doc to `docs/` while at it.
5. **Metadata:** fill `authors` in `fabric.mod.json`; sanity-check `LICENSE` (currently
   CC0 from the template — confirm that's intended for this project, flag if not).

## Verification

- `./gradlew build` green; jar contains no `example`/`modid` entries
  (`unzip -l build/libs/*.jar | grep -iE 'example|modid'` → empty).
- `runClient`: mod loads, `/companion spawn` works, icon shows in Mod Menu (if installed).

## Files to touch

Most of `src/` (mechanical rename), `README.md`, `fabric.mod.json`,
`companion.mixins.json`, `gradle.properties`, deletions listed above.

## Acceptance criteria

- [ ] No `com.example`, `ExampleMod*`, or `modid` references anywhere in the repo
      (`grep -riE 'com\.example|modid|example ?mod' --exclude-dir=.git` clean, modulo docs history).
- [ ] README describes *this* project with a working quickstart.
- [ ] Build + in-game smoke test pass identically to before the rename.
