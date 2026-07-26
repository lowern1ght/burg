# Burg

> **Villages with their own life.**

[![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen?style=flat-square)](https://minecraft.net)
[![Mod Loader: NeoForge](https://img.shields.io/badge/loader-NeoForge-1976d2?style=flat-square)](https://neoforged.net)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue?style=flat-square)](LICENSE)

**Burg** is a Minecraft mod that transforms vanilla villages into autonomous, thriving towns. NPC builders grow settlements through eras while you develop your own path. Earn trust through helping — defend against raids, complete quests, contribute resources — and villages may eventually recognize you as chief.

Villages are **not** your project. They grow themselves. You're a neighbor who can help.

---

## Features

- 🏘️ **Autonomous NPC builders** — villages grow themselves; you can help, not command
- 🌾 **Era progression** — Settlement → Village → 5 specialized branches (Urban, Rural, Ranching, Cooking, Forge)
- 🛠️ **Upgrades & production** — buildings transform resources, level up to unlock new recipes
- 🗺️ **Town hub GUI** — draggable widgets: town map, summary, era progress, quest tracker
- 💼 **Trading** — buy/sell at the town hall with datapack-driven prices
- 📜 **Quest system** — delivery quests and lore notes, data-driven prerequisites
- 📦 **Datapack-first** — add buildings, eras, quests, prices via JSON, no code required

---

## Installation

1. Install [NeoForge for Minecraft 1.21.1](https://neoforged.net/).
2. Download the latest release `.jar` from [Releases](https://github.com/lowern1ght/burg/releases).
3. Drop the file into your `mods/` folder.
4. Launch Minecraft and find a village — a **Town Anchor** block has been placed at its center.

> Burg is currently in development for NeoForge 1.21.1. See issue [#1](https://github.com/lowern1ght/burg/issues/1) for the port progress. The legacy version runs on Forge 1.20.1 under the `1.20.1-reborn` branch.

---

## Quick start

1. Find a plains village in your world — a **Town Anchor** block sits at the center.
2. Right-click it to open the **Town Hub**.
3. Three tabs are available:
   - **Stock** — see what the village has produced; buy/sell from your inventory.
   - **Construction** — queue new buildings or view the catalog; your builder NPC will autonomously construct them.
   - **Upgrade** — level up placed buildings to unlock new recipes and bonuses.
4. Drop resources into the trade zone to feed the village.
5. Watch the NPC builder autonomously grow your village while you develop your own path elsewhere.

---

## Datapack development

Burg is fully datapack-driven. Add new buildings, eras, quests, prices, and builder behavior by dropping JSON files into your datapack. See [`docs/PHILOSOPHY.md`](docs/PHILOSOPHY.md) for design intent and the [issues list](https://github.com/lowern1ght/burg/issues) for upcoming documentation.

Minimal building example:

```json
{
  "id": "my_building",
  "nbt": "mymod:plains/jobs/my_building",
  "category": "jobs",
  "weight": 2,
  "construction_cost": [{ "item": "minecraft:oak_log", "amount": 24 }],
  "production": [
    { "item": "minecraft:oak_planks", "amount": 4, "every_ticks": 600, "capacity_stacks": 4 }
  ]
}
```

---

## Philosophy

Burg is built on a clear design philosophy — the player is a helper, not a leader.

- **Villages are autonomous.** They grow even if you never engage.
- **Player helps, not commands.** No "issue order" UI. The NPC builder is the actor.
- **Datapack-driven.** New content = JSON, not code.
- **Vanilla feel.** The existing hub stays minimal; subtle additions only.

The full philosophy, including what is explicitly out of scope, lives in [`docs/PHILOSOPHY.md`](docs/PHILOSOPHY.md).

---

## Project status

Burg is in early development. Current focus:

1. **Port to NeoForge 1.21.1** (issue [#1](https://github.com/lowern1ght/burg/issues/1)) — the immediate blocker for new releases.
2. **Code rename** `onceuponatown` → `burg` (issue [#11](https://github.com/lowern1ght/burg/issues/11)) — clean slate after the port lands.
3. **Player reputation system** (issue [#3](https://github.com/lowern1ght/burg/issues/3)) — the foundation for "village as character".
4. **Town defense / raids** (issue [#4](https://github.com/lowern1ght/burg/issues/4)) — natural way for the player to help and earn trust.

See [all open issues](https://github.com/lowern1ght/burg/issues) for the full roadmap.

---

## License

GPL-3.0 — see [`LICENSE`](LICENSE).

This is a fork of [TheGoldenWorld/OnceUponATown](https://github.com/DawnOfTimeMC/onceuponatown). Original authorship, license inheritance, and the line of descent are documented in [`FORK_NOTICE.md`](FORK_NOTICE.md).

---

## Links

- [GitHub repository](https://github.com/lowern1ght/burg)
- [Issue tracker](https://github.com/lowern1ght/burg/issues)
- [Original mod](https://github.com/DawnOfTimeMC/onceuponatown) — TheGoldenWorld/OnceUponATown