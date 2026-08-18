# Port status — NeoForge 1.21.1

A living tracker of what has survived, changed, or is still pending in the port from the `1.20.1-reborn` branch to `feat/port-neoforge-1.21.1`. [ARCHITECTURE](ARCHITECTURE.md) describes the pre-port state and warns that paths/APIs will change; this doc records the deltas that have actually landed.

> Branch: `feat/port-neoforge-1.21.1`. Mod id, package and entry class are still `onceuponatown` / `org.dawnoftime.onceuponatown` / `OuatForge` — the rename to `burg` is issue [#11](https://github.com/lowern1ght/burg/issues/1) and has not landed. References to "Ouat" below are current reality, not stale text.

---

## Where the port stands

The loader migration itself is **complete**. It landed in two commits — `b8d94cb` (gradle + the `data/<ns>/structures/` → `data/<ns>/structure/` move, 124 NBTs byte-identical) and `51d1f11` ("finish the NeoForge 1.21.1 port, and make a citizen a villager", 2026-07-29) — and the branch has since accumulated ~20 feature commits on top of it (citizens, i18n, the `people/` layer, NPC skins and poses, plains/meadow worldgen replacement). The mod compiles, loads in dev (`neoforge/run/logs/latest.log` is clean of mod errors), and the `gameTestServer` run is configured. Work on this branch is now post-port feature work, not port work.

---

## What has landed

Each line carries a one-line evidence pointer (file or commit).

- **Build toolchain swapped to NeoForge moddev.** `net.neoforged.moddev` plugin 1.0.21, NeoForge 21.1.77, MC 1.21.1, Java 21. — `gradle.properties:9,12,25`, `neoforge/build.gradle:1-5`
- **Mixin rewired for a Mojang-mapped runtime.** No searge/refmap mapping needed; a minimal empty refmap is emitted so the loader does not warn. — `neoforge/build.gradle:7-34`
- **All 18 network packets rewritten to `CustomPacketPayload`.** Every packet in `common/.../network/` implements `CustomPacketPayload` with a `StreamCodec` and a `TYPE`; no `SimpleChannel` remains. Registered through `PayloadRegistrar` in the loader. — `neoforge/.../OuatForge.java:223-247` (`onRegisterPayloadHandlers`)
- **Server→client dispatch moved to `PacketDistributor`.** `NetworkHelper.send*` delegates are wired in `onCommonSetup` to `PacketDistributor.sendToPlayer(...)`. — `neoforge/.../OuatForge.java:183-197`
- **Registries moved to `DeferredRegister`.** Blocks, items, block-entities, entity types, menu types and attachment types are all deferred and registered on the mod event bus. — `neoforge/.../OuatForge.java:94-155`
- **Citizen rework landed with the port.** A citizen is no longer a bespoke entity subclass; membership is a NeoForge `AttachmentType<CitizenData>` on `minecraft:villager`. The `Citizen` entity type still exists for the NPC builder, but the townsfolk are vanilla villagers with one bit of identity synced via the new `S2CVillagerIdentityPacket`. — commit `51d1f11`, `neoforge/.../OuatForge.java:108-110,131-137,193-197`
- **Structure directory renamed.** `data/onceuponatown/structures/` → `data/onceuponatown/structure/` (1.21 pack path), 124 NBTs moved byte-identical. — commit `b8d94cb`
- **`SavedData` recompiled against the 1.21 `HolderLookup.Provider` signature.** `INBTSerializable` now takes a `HolderLookup.Provider` on both halves; called out in the port commit because both halves compile while doing the wrong thing. — commit `51d1f11`
- **`RenderLayer.renderColoredCutoutModel` tail-int signature fixed.** The tail `int` is an ARGB colour, not three float channels; the old call passed an overlay coord whose alpha byte was zero, drawing every garment fully transparent. — commit `51d1f11`
- **Datapack reload wired to `ServerStartingEvent`.** All eight handlers (`BuilderConfig`, `Building`, `BuildingList`, `EraTransition`, `FoodList`, `Quest`, `SettlerJobs`, `TradePrice`) reload on server start from the NeoForge event bus. — `neoforge/.../OuatForge.java:259-268`
- **Town integrity healing on chunk load.** `TownIntegrity.healAnchors(level, chunk)` restores a missing anchor when its chunk loads, replacing a startup sweep. — `neoforge/.../OuatForge.java:292-295`
- **`/ouat town spawn` does site prep.** Surveys the footprint, takes the median ground level, runs `TerrainCarver`, and seeds `initial_stock` — previously the starter floated one course up and the builder could never start. — commit `51d1f11`
- **i18n passes landed (en/ru).** Town hub widgets, commands, network chat, trade messages, era labels and the anchor block all go through translatable keys with autodetect. — commits `bf2a1e4`…`b9402bd`
- **Worldgen replacement in plains and meadow.** Vanilla villages in those biomes are replaced by ours. — commit `a76bbea`

---

## What is pending

Items the port itself did not finish, or that [ARCHITECTURE](ARCHITECTURE.md) calls out as separate work.

- **Rename `onceuponatown` → `burg`** (issue [#11](https://github.com/lowern1ght/burg/issues/1)). Mod id, gradle `rootProject.name`, the `org.dawnoftime.onceuponatown` package, the `OuatForge` / `OuatForgeClient` entry classes, the `ouat` command prefix and every `onceuponatown:` resource location still carry the old name. This is a separate, deliberate post-port task.
- **`ARCHITECTURE.md` refresh.** That doc still describes the 1.20.1-reborn tree (it says so itself, line 5) and has not been updated for: the `people/` package, `entity/citizen/`, `gametest/`, `SettlerJobsDataHandler`, `TownIntegrity`, the 18th packet, the attachment-based citizen model, or the rename. PORT-STATUS is the companion until ARCHITECTURE is rewritten.
- **Player reputation system** (issue [#3](https://github.com/lowern1ght/burg/issues/3)). Not started on this branch; called out in the README roadmap.
- **Town defense / raids** (issue [#4](https://github.com/lowern1ght/burg/issues/4)). Not started on this branch; called out in the README roadmap.

---

## Unverified

Things this doc cannot confirm without running the game or a full build; recorded honestly rather than guessed.

- **No clean build has been run from this checkout.** `neoforge/build/libs/` is empty in the working tree; the port's compilability is inferred from the clean dev log and the game-test run, not from a freshly produced jar. A `gradle build` is the real test.
- **Game-test structure `onceuponatown:empty5x5` is reported missing** by the most recent crash report (`neoforge/run/crash-reports/crash-2026-07-27_01.51.25-server.txt`). The `gameTestServer` run configuration exists and the mod loads, but at least one registered game test cannot resolve its structure. Severity unverified — could be a missing fixture or a game-test infra gap, not a port regression.
- **World save compatibility.** `Town.fromNbt()` carries backward-compat shims per [ARCHITECTURE](ARCHITECTURE.md), but whether a 1.20.1 save loads cleanly under 1.21.1 has not been verified end-to-end. The `INBTSerializable` signature change (above) is a known risk surface.
- **Multiplayer behaviour.** All 18 packets are wired, but the S2C broadcast paths (`push*ToWatchers`) have only been exercised in single-player dev. Edge cases around player tracking range, chunk unload, and the new `StartTracking`-driven identity packet are unverified.
- **Datapack reload on `/reload`.** Handlers reload on `ServerStartingEvent`; whether they also respond correctly to a runtime `/reload` (which 1.21 wires through `ResourceManager` reload listeners) is not confirmed by reading `OuatForge` alone.

---

## How to update this doc

When a port or post-port task lands, move its line from **What is pending** to **What has landed** with the commit SHA or PR number as the evidence pointer. When something in **Unverified** is confirmed or refuted by running the game, move it to the relevant section with the verification note. Keep entries to one line plus a pointer — long form belongs in commit messages or [ARCHITECTURE](ARCHITECTURE.md).

---

## Related

- [ARCHITECTURE](ARCHITECTURE.md) — the pre-port system map (describes 1.20.1-reborn)
- [`README.md`](../../README.md) — public status line and roadmap links
- Issue [#1](https://github.com/lowern1ght/burg/issues/1) — NeoForge 1.21.1 port
- Issue [#11](https://github.com/lowern1ght/burg/issues/11) — code rename `onceuponatown` → `burg`
- [`CLAUDE.md`](../../CLAUDE.md) — repo-wide facts and the build/verification toolchain
