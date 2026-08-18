<p align="center">
  <strong>Burg</strong><br/>
  <em>Villages with their own life.</em>
</p>

<p align="center">
  <a href="https://minecraft.net"><img alt="Minecraft 1.21.1" src="https://img.shields.io/badge/Minecraft-1.21.1-brightgreen?style=flat-square" /></a>
  <a href="https://neoforged.net"><img alt="NeoForge" src="https://img.shields.io/badge/loader-NeoForge-1976d2?style=flat-square" /></a>
  <a href="LICENSE.md"><img alt="License: GPL-3.0" src="https://img.shields.io/badge/license-GPL--3.0-blue?style=flat-square" /></a>
  <a href="https://github.com/lowern1ght/burg/issues"><img alt="Issues" src="https://img.shields.io/github/issues/lowern1ght/burg?style=flat-square" /></a>
</p>

Burg turns vanilla Minecraft villages into places that keep living when you walk away. NPC builders grow the settlement through eras; you earn trust by trading and supplying — and a stranger can, over time, become chief, then king.

Villages are **not** your colony. They grow themselves. You are a neighbor who can help.

Our philosophy:

```text
→ villages autonomous, not player-driven
→ help and supply, not command consoles
→ datapack content, not hardcoded lists
→ NPC builder places every block
→ vanilla feel, not a UI overhaul
```

> [!NOTE]
> NeoForge **1.21.1** is the current line (`master`). The legacy Forge 1.20.1 tree lives on `1.20.1-reborn`. Early development — nothing below is verified as a finished player walk in a published release.

## See it in action

```text
# schematic — intended loop on master (NeoForge 1.21.1)

You:  find a plains village
World: Town Anchor sits at the meeting point

You:  right-click a citizen
Burg:  name · trade · what they are doing right now

You:  supply oak logs and cobblestone at the hub
Burg:  stock rises · builder wakes earlier · walls climb

You:  keep helping through eras
Burg:  settlement → village → branch (urban / rural / ranching / cooking / forge)
       standing rises · the village may name you chief
```

<details>
<summary><strong>What a datapack building looks like</strong></summary>

Drop JSON under your datapack — no Java required:

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

Structures are vanilla `.nbt` (Blockbench works). The five loaders — buildings, eras, quests, builder config, trade prices — are the content contract.

</details>

## Why Burg

**Autonomous towns** — production, food, quests, and builders tick whether you are online or not.

**Earned crown** — stranger → guest → trusted → chief → king. Power is graduated, not given at spawn.

**Datapack-first** — new buildings, eras, quests, and prices are JSON. Code changes are for systems, not content packs.

**Not MineColonies** — no order queue for NPCs, no player-placed town blocks, no combat overhaul. Raid-scale fights stay vanilla; war-scale is NPC-vs-NPC (design on the roadmap).

## Quick start

**Requires:** Minecraft **1.21.1** · [NeoForge](https://neoforged.net/) matching that version · Java **21** (for building from source)

### Play

1. Install NeoForge for 1.21.1.
2. Drop the latest `.jar` from [Releases](https://github.com/lowern1ght/burg/releases) into `mods/`.
3. Create or open a world, find a plains village, look for the **Town Anchor**.

> Releases may lag the `master` branch while the NeoForge port and content gates settle. Prefer building from source if you need tip-of-tree.

### Build from source

```bash
git clone https://github.com/lowern1ght/burg.git
cd burg
./gradlew :neoforge:build
```

Jar output lands under `neoforge/build/libs/`.

## Docs

| Doc | What it answers |
|---|---|
| [`docs/01-vision/VISION.md`](docs/01-vision/VISION.md) | What kind of game this is (earned crown) |
| [`docs/01-vision/PHILOSOPHY.md`](docs/01-vision/PHILOSOPHY.md) | Five pillars and hard bans |
| [`docs/01-vision/FOCUS.md`](docs/01-vision/FOCUS.md) | How this fork differs from upstream |
| [`docs/02-roadmap/ROADMAP.md`](docs/02-roadmap/ROADMAP.md) | Acts 0–5 in order |
| [`docs/04-engineering/ARCHITECTURE.md`](docs/04-engineering/ARCHITECTURE.md) | Subsystem map |
| [`docs/07-state/STATUS.md`](docs/07-state/STATUS.md) | What is build-green vs verified-in-game |
| [`FORK_NOTICE.md`](FORK_NOTICE.md) | Lineage, license, credits |

## Compared with

**vs. MineColonies** — Full colony management: you hire, order, and place. Burg refuses that surface. Influence is standing, supply, and earned command later — never a god console over every villager.

**vs. Guardians / town mods that drop a rival structure** — Burg prefers converting or growing from vanilla villages (ruling: the player *finds* the village). Ordinary Minecraft places stay the spine.

**vs. Once Upon a Town (upstream)** — Same lineage, different aim. Upstream grew feature breadth; Burg freezes a vision (earned crown, datapack-first, NPC builder as sole block placer) and ports to NeoForge 1.21.1. See [`FOCUS.md`](docs/01-vision/FOCUS.md).

## Status

Early development on `master` (NeoForge 1.21.1). Open work is tracked in [issues](https://github.com/lowern1ght/burg/issues). Subsystem honesty lives in [`STATUS.md`](docs/07-state/STATUS.md) — a green build is not the same as verified-in-game.

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md). Design PRs should cite which pillar they serve ([`PHILOSOPHY.md`](docs/01-vision/PHILOSOPHY.md)). Content that can be JSON should stay JSON.

```bash
./gradlew build
```

## License

**GPL-3.0** — see [`LICENSE.md`](LICENSE.md).

Fork of [DawnOfTimeMC/onceuponatown](https://github.com/DawnOfTimeMC/onceuponatown) by **TheGoldenWorld**. Original authorship, GPL inheritance, and what diverged are in [`FORK_NOTICE.md`](FORK_NOTICE.md). Upstream credits stay; this fork does not republish under the upstream CurseForge project id.
