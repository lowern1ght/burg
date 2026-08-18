# ADR-0007: Rename mod identity from `onceuponatown` to `burg`

- **Status**: Accepted
- **Date**: 2026-08-19
- **Decided by**: owner

## Context

The fork was originally a 1.21.1 port of the upstream mod
`TheGoldenWorld/OnceUponATown` (mod id `onceuponatown`, package
`org.dawnoftime.onceuponatown`, display name "Once Upon a Town"). The fork
diverged in philosophy (`docs/01-vision/PHILOSOPHY.md`) and direction, and the
upstream `org.dawnoftime` package was always an artifact of the original
author's group, not the fork's. Continuing to ship under a name and namespace
that are not ours misleads pack authors and creates a permanent attribution
leak that a `FORK_NOTICE.md` cannot repair on its own.

This is the rename the fork's [`README.md`](../../README.md) has been calling
"issue #11" and the [`PORT-STATUS.md`](../04-engineering/PORT-STATUS.md) calls
"a separate, deliberate post-port task". The port itself
(`feat/port-neoforge-1.21.1`) landed; the rename was the explicit follow-up.

## Decision

Rename the mod identity in one sweep:

| Surface | Before | After |
|---|---|---|
| `gradle.properties` `mod_id` | `onceuponatown` | `burg` |
| `gradle.properties` `mod_name` | `Once upon a Town` | `Burg` |
| `gradle.properties` `group` | `org.dawnoftime.onceuponatown` | `org.lowern1ght.burg` |
| `gradle.properties` `mod_author` | `TheGoldenWorld` | `TheGoldenWorld, lowern1ght (fork maintainer)` |
| `gradle.properties` `credits` | (Poulpinou, Zadrac only) | + upstream + fork attribution |
| `settings.gradle` `rootProject.name` | `onceuponatown` | `burg` |
| Java package (common + neoforge) | `org.dawnoftime.onceuponatown` | `org.lowern1ght.burg` |
| Resource namespace (assets/) | `assets/onceuponatown/` | `assets/burg/` |
| Resource namespace (data/) | `data/onceuponatown/` | `data/burg/` |
| Mixin config filenames | `onceuponatown.mixins.json`, `onceuponatown.forge.mixins.json` | `burg.mixins.json`, `burg.forge.mixins.json` |
| `Constants.MOD_ID` / `MOD_NAME` | `onceuponatown` / `Once Upon a Town` | `burg` / `Burg` |
| `neoforge.mods.toml` `issueTrackerURL` | `github.com/DawnOfTimeMC/onceuponatown/issues` | `github.com/lowern1ght/burg/issues` |
| Translation keys | `block.onceuponatown.*`, `quest.onceuponatown.*` | `block.burg.*`, `quest.burg.*` |
| Tooling scripts (`tools/`, `studio/`) | hard-coded `data/onceuponatown/`, `assets/onceuponatown/`, `org.dawnoftime.onceuponatown/` | same paths under `burg/` |

**Authorship is preserved.** `mod_author` keeps `TheGoldenWorld` as the
original author and adds `lowern1ght (fork maintainer)`. `credits` keeps
the existing acknowledgements (Poulpinou, Zadrac) and adds a line for the
upstream + the fork. `FORK_NOTICE.md` is unchanged — the GPL-3 attribution
chain remains intact. `LICENSE.md` is unchanged. `docs/01-vision/FOCUS.md`
is unchanged (historical focus document for the upstream port).

**Internal class names left alone for now.** `Ouat`, `OuatForge`, `OuatForgeClient`,
the `ouat` command prefix, and `Town`/`TownNpc`/etc. remain as-is. These are
internal API; renaming them is a separate, larger refactor that belongs to a
post-rename cleanup window, not this identity sweep. Renaming the `Constants`
fields is sufficient for the identity surface; everything else is internal.

## What this breaks (residual risk)

1. **World saves from before this commit will not load.** The mod id is part
   of every persisted `BlockEntity.getPersistentData()` tag (`"id"` /
   `"forge:boss_event_data"`), every `SavedData` class name
   (`<MOD_ID>:<saved_data_name>`), and every namespace under `level.dat`'s
   `DataPacks` section. Loading a save written under `onceuponatown` into
   a build of Burg will fail with "Unknown data pack: onceuponatown" / missing
   `SavedData` exceptions.

   - **Stance**: pre-release, no saved data exists in the wild. Document the
     break in release notes; do not ship a save migrator yet.
   - **Post-release stance (deferred)**: when v0.1 ships, write a one-time
     `MigrationHelper` that runs on `ServerStartingEvent`, reads the old
     `onceuponatown` data-files (`data/<level>/data/onceuponatown/...`) and
     `BlockEntity` NBT, and rewrites the keys. Until then, saves from
     pre-rename builds are unsupported. Track in
     `issues/lowern1ght/burg#next-after-rename`.

2. **Third-party datapacks written under `data/onceuponatown/` are now
   invisible to the mod** because the loader namespace is gated to the new
   `burg` id (`datapack/BuildingDataHandler.java` namespace check). The fix
   for pack authors is to rename `data/onceuponatown/` to `data/burg/` in
   their pack.

3. **Jigsaw structure references** in `data/burg/worldgen/structure_set/*.json`,
   `data/burg/worldgen/template_pool/**/*.json`, and the buildings / eras
   JSONs reference `burg:plains/jobs/...` now — same path under a new
   namespace. Vanilla-format structure NBTs are untouched (their
   `data_string` block-entity fields still name `burg:town_anchor`, etc.).

4. **Soft-dependency surfaces.** `Xaero`'s minimap integration lives in
   `common/.../integration/xaero/`. The package rename moves the integration
   class from `org.dawnoftime.onceuponatown.integration.xaero` to
   `org.lowern1ght.burg.integration.xaero`. Any pack that depended on the
   class by FQN (uncommon but possible) breaks. The integration itself is
   addressed by `feat/integrations/xaero` and is unaffected by this commit
   beyond the package move.

5. **Studio / Tauri app paths.** `studio/vite.config.ts`,
   `studio/src-tauri/src/lib.rs`, and several scripts under `studio/scripts/`
   carry `data/onceuponatown/...` paths. They have been rewritten to
   `data/burg/...` in this commit. The `studio/scripts/bicalibration-report.json`
   generated report was regenerated in the same pass; older reports are
   obsolete.

## What this does NOT touch (intentional, per scope)

- `FORK_NOTICE.md` — GPL-3 attribution record, must retain upstream name.
- `LICENSE.md` — unchanged.
- `docs/01-vision/FOCUS.md` — historical focus document for the upstream port.
- `docs/04-engineering/PORT-STATUS.md` — explicitly a snapshot of pre-rename
  reality, retained as audit trail. Future readers: that doc says "rename
  has not landed" because, at the time it was written, it had not.
- `README.md`, `CONTRIBUTING.md`, `docs/04-engineering/ARCHITECTURE.md`,
  `docs/04-engineering/DATA-FORMATS.md`, `docs/05-craft/*`, `docs/07-state/*`,
  `openspec/*`, `openspec/config.yaml` — left of the identity sweep. These
  describe the data layout; updating them to say `data/burg/` is a
  documentation-only follow-up. Tracked for a separate "docs refresh" PR.
- Java Javadoc comments that quote historical bug reports
  (`NpcHeadModels.java:15`, `NpcHeadLayer.java:96,149`, `NpcHairLayer.java:25`)
  retain the original `onceuponatown:npc_beard#v0` reference. Those comments
  describe a bug that was fixed in commit `a6ada7f` and are true to history;
  rewriting them would falsify the record.
- The `Ouat` / `OuatForge` / `OuatForgeClient` class names and the `ouat`
  command prefix — internal API, out of scope for this identity sweep.

## Verification

- `:common:compileJava` + `:neoforge:compileJava` must succeed before merge.
  This catches every package / import / mixin-package / refmap rename.
- A grep for `org.dawnoftime.onceuponatown` and bare `onceuponatown` outside
  the historical-residue set above must return zero hits in
  `common/src/main/{java,resources}` and `neoforge/src/main/{java,resources}`.
- `neoforge.mods.toml` `issueTrackerURL` and `Constants.MOD_ID` updated; runtime
  smoke test (`./gradlew runClient`) deferred — pre-release.

## Rollback

Trivial. `git revert` the merge commit; the renamed files are a clean rename
(`git log --stat` will show all-RM/all-A+ entries that git heuristics detect
as renames), and reverting puts the old paths and contents back. The blast
radius is exactly one merge commit; nothing in this sweep depends on anything
later than this commit landing first.