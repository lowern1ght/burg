# Burg docs — index

A map of every document, who it's for, and what order to read it in. New here?
Read the **First time** column top to bottom. Looking for something specific?
Use the **Find** column.

Tags: 👤 = human-facing (narrative, design), 🤖 = agent-facing (rules,
loadable), 🔧 = reference (look up, don't read end-to-end).

## First time (in order)

| # | doc | who | what it gives you |
|---|---|---|---|
| 1 | [01-vision/VISION.md](01-vision/VISION.md) | 👤 | What kind of game Burg is — the earned-crown trajectory, stranger to king |
| 2 | [01-vision/PHILOSOPHY.md](01-vision/PHILOSOPHY.md) | 👤🤖 | The five eternal pillars + what's a starting role vs eternal |
| 3 | [02-roadmap/ROADMAP.md](02-roadmap/ROADMAP.md) | 👤 | What's built, what's next, what doesn't exist yet — by act |
| 4 | [07-state/STATUS.md](07-state/STATUS.md) | 👤🔧 | What actually works vs what's `build-green` vs `verified-in-game` |

After those four, branch by what you're doing:

## Find

### Why — design intent
| doc | who | for |
|---|---|---|
| [01-vision/VISION.md](01-vision/VISION.md) | 👤 | the earned crown, three acquisition paths, autonomy slider, Realm/Kingdom consequence, the unsolved war-scale problem |
| [01-vision/PHILOSOPHY.md](01-vision/PHILOSOPHY.md) | 👤🤖 | pillars 1/3/4/5 (eternal), pillar 2 + hard bans (reclassified to starting role) |
| [01-vision/RULINGS.md](01-vision/RULINGS.md) | 👤🤖 | flat index of every constitutional owner ruling + the load-bearing measurement-derived rules |

### What — the roadmap & state
| doc | who | for |
|---|---|---|
| [02-roadmap/ROADMAP.md](02-roadmap/ROADMAP.md) | 👤 | the act order; what each act's player verb is; ruling 3 tension |
| [07-state/STATUS.md](07-state/STATUS.md) | 👤🔧 | per-subsystem state (`build-green` / `verified-in-game` / `broken` / `not-started`) |
| [07-state/OPEN-WORK.md](07-state/OPEN-WORK.md) | 👤🔧 | the backlog; moved out of CLAUDE.md |
| [07-state/AUDIT-*.md](07-state/) | 👤🔧 | deep audits (military, livestock, skins, framework) — what's beta and why |

### How — system design (what each game system does, not the code)
| doc | who | for |
|---|---|---|
| [03-design/npc/README.md](03-design/npc/README.md) | 👤 | NPC life: needs, morale, chief emergence, capture reaction, soldier role |
| [03-design/realm/README.md](03-design/realm/README.md) | 👤 | Realm/Kingdom layer above Town, acquisition field, autonomy-control slider |
| [03-design/war/README.md](03-design/war/README.md) | 👤 | the scale problem (army vs 1v1) — explicitly unsolved |
| [03-design/diplomacy/README.md](03-design/diplomacy/README.md) | 👤 | relations between realms, AI chiefs, tribute, alliances |
| [03-design/economy/README.md](03-design/economy/README.md) | 👤 | production, gold two denominations, supply-steers-building, realm economy |

### How — engineering (how the code is actually built)
| doc | who | for |
|---|---|---|
| [04-engineering/ARCHITECTURE.md](04-engineering/ARCHITECTURE.md) | 👤🤖 | subsystem map: state / tick / ai / datapack / worldgen / client / network, with file refs |
| [04-engineering/PORT-STATUS.md](04-engineering/PORT-STATUS.md) | 👤🔧 | what survived the NeoForge 1.21.1 port, what's pending, what's unverified |
| [04-engineering/DATA-FORMATS.md](04-engineering/DATA-FORMATS.md) | 🤖🔧 | the datapack JSON schemas (buildings / eras / quests / config) — the extension contract |
| [04-engineering/modding/README.md](04-engineering/modding/README.md) | 🤖🔧 | NeoForge 1.21.1 agent modding pack: official-docs index, hard API rules, curated excerpts |

### How — craft (how we author buildings & skins)
| doc | who | for |
|---|---|---|
| [05-craft/HOW_WE_WORK.md](05-craft/HOW_WE_WORK.md) | 👤🤖 | the five-layer protocol; what is proven not to work; the bench; the ledger |
| [05-craft/STYLE.md](05-craft/STYLE.md) | 👤🤖 | the fortification style: gradient ramps, three stones, worked-stone-as-top-unlock |
| [05-craft/DEVICES.md](05-craft/DEVICES.md) | 👤🔧 | every device in the author's corpus, with counts — the grammar |
| [05-craft/BUILD_LANGUAGE.md](05-craft/BUILD_LANGUAGE.md) | 👤🔧 | device recipes per building family + the livestock/military implementation record |
| [.agents/skills/burg-buildings/SKILL.md](../.agents/skills/burg-buildings/SKILL.md) | 🤖 | the 5 building laws, measured — load when building anything |
| [.agents/skills/burg-skins/SKILL.md](../.agents/skills/burg-skins/SKILL.md) | 🤖 | the 9 skin laws, measured — load when touching citizen textures |
| [.agents/skills/stylekit-from-nbt/SKILL.md](../.agents/skills/stylekit-from-nbt/SKILL.md) | 🤖 | reading an NBT's anatomy for style |

### Why that way — decisions log
| doc | who | for |
|---|---|---|
| [06-decisions/ADR-0001-earned-crown-trajectory.md](06-decisions/ADR-0001-earned-crown-trajectory.md) | 👤 | stranger→king vision formalised; pillar 2 reclassified |
| [06-decisions/ADR-0002-plains-readonly.md](06-decisions/ADR-0002-plains-readonly.md) | 👤🤖 | author's corpus is read-only (CRLF repair the one exception) |
| [06-decisions/ADR-0003-farm-fence-stays-timber.md](06-decisions/ADR-0003-farm-fence-stays-timber.md) | 👤🤖 | a farm fence never turns to stone |
| [06-decisions/](06-decisions/) | 👤 | append a new ADR per significant decision |

## Conventions

- **Numbers in folder names** = reading order for a newcomer (00–07). Within
  a folder, files are not ordered.
- **One ADR per decision**, `ADR-NNNN-kebab-slug.md`, append-only. Never edit
  a shipped ADR — supersede it with a new one and link back.
- **RULINGS.md is a flat index**, one line per ruling. A formalised ruling
  links to its ADR; an informal one says so.
- **STATUS.md / OPEN-WORK.md / AUDIT-*.md are living state** — update in
  place; do not preserve history inside them (git does that).
- **Craft canon** lives in `05-craft/` (human) and `.agents/skills/*/`
  (agent-loadable). They reference each other; neither copies the other.

## Also see

- [`CLAUDE.md`](../CLAUDE.md) — the agent's always-loaded entry point. Thin by
  design; everything heavy has a home in this docs tree.
- [`README.md`](../README.md) — public-facing summary for players / GitHub.
- [`CONTRIBUTING.md`](../CONTRIBUTING.md) — how to file issues and PRs.
