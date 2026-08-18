# Contributing to Burg

Thank you for your interest in Burg. This document covers how to file issues, contribute code, and contribute datapack content.

## Code of conduct

Be respectful. Focus on the work. The fork is a hobby project maintained in spare time — patience and clear bug reports help a lot.

## Filing issues

Use [GitHub issues](https://github.com/lowern1ght/burg/issues) for:

- **Bug reports** — describe what you did, what you expected, what happened. Include the Minecraft version, NeoForge version, and Burg version.
- **Feature requests** — first read [`docs/PHILOSOPHY.md`](docs/PHILOSOPHY.md) and confirm the feature aligns with the design pillars. If in doubt, open an issue tagged "discussion" before writing code.
- **Datapack questions** — open an issue with a `[datapack]` prefix.

For crashes, attach the full `latest.log` and any debug output. For datapack bugs, attach the offending JSON.

## Code contributions

### Before you write code

1. **Open an issue first.** Describe what you want to change and why. Small changes can be a single paragraph; large changes need a short design doc.
2. **Check the philosophy.** New features must serve one of the five pillars in [`docs/PHILOSOPHY.md`](docs/PHILOSOPHY.md). Features that don't will be declined even if well-implemented.
3. **Check existing issues.** Use the search before opening a duplicate.

### Architecture overview

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the system map. The mod is split into:

- **State** (`town/`) — `Town`, `LevelTowns`, building/quest/inventory data.
- **Tick** (`tick/`) — `TickScheduler`, `ProductionManager`, `FoodManager`, `EraManager`.
- **AI** (`entity/`) — `Npc`, `BuildGoal`, `BuildAction` interface.
- **Datapack** (`datapack/`) — loaders for buildings/eras/quests/prices/builder-config.
- **Worldgen** — vanilla jigsaw structures + `ChunkGeneratorMixin`.
- **Client** (`client/`) — `TownHubScreen`, draggable widgets, NPC renderer.
- **Network** (`network/`) — 17 server↔client packets.

### Branching model

- **`main`** — releases only. Force-push discouraged.
- **`docs/*`** — documentation changes.
- **`feat/*`** — feature branches (one per issue).
- **`fix/*`** — bug fix branches.
- The current development line lives on `1.20.1-reborn` until the NeoForge 1.21.1 port lands (see issue [#1](https://github.com/lowern1ght/burg/issues/1)).

### Commit format

Every commit follows `[.stbl](feat/<area>): <imperative subject>` — see the project's commit conventions. Examples:

```
[.stbl](feat/port): update gradle plugin to neoforged.moddev
[.stbl](feat/village): add reputation tier confidant
[.stbl](feat/docs): add philosophy doc
[.stbl](feat/meta): rename mod_id onceuponatown to burg
```

### Pull request process

1. Branch off the current development line (not `main`).
2. Keep PRs focused — one logical change per PR.
3. Update or add tests where applicable.
4. Make sure `gradlew build` passes locally.
5. Reference the issue number in the PR body (e.g. "Closes #3" or "Refs #1").
6. Wait for review. Be prepared to revise.

## Datapack contributions

Datapack contributions are usually **easier to merge** than code contributions because they don't touch Java. To add a new building:

1. Create the structure NBT file under `common/src/main/resources/data/<namespace>/structures/<biome>/<category>/<name>.nbt`.
2. Create the definition JSON under `common/src/main/resources/data/<namespace>/buildings/<name>.json` — see existing files for the schema.
3. If the building has upgrades, add the per-level NBT files and reference them in `nbt_levels`.
4. Open a PR with both files.

For new eras, quests, or trade prices, the JSON schema lives in the existing files; copy an existing one and modify.

## Translations

Burg uses Minecraft's built-in translation system (`.lang` files under `assets/<modid>/lang/`). To add a translation:

1. Copy `common/src/main/resources/assets/burg/lang/en_us.json` to `xx_xx.json` for your locale.
2. Translate only the values, never the keys.
3. Open a PR.

## Don'ts

- ❌ Don't add code without an issue and philosophy alignment.
- ❌ Don't break the autonomous-NPC principle by adding player-controlled NPCs.
- ❌ Don't add new GUI tabs to the Town Hub — keep it vanilla-feel.
- ❌ Don't commit generated files (`build/`, `*.class`, `*.jar`).
- ❌ Don't commit the `mods.toml` placeholder tokens without replacing them.
- ❌ Don't force-push to `main`.

## License

By contributing, you agree that your contributions are licensed under GPL-3.0, the same license as the project.