# Philosophy

Burg is **not** MineColonies. The player is not the colony leader; villages are not the player's project. They're semi-autonomous places the player can *help*. This document is the design canon — every feature PR must cite which pillar it serves, and what is explicitly out of scope is non-negotiable until this document changes.

---

## Elevator pitch

> *Minecraft villages with their own life. NPC builders autonomously grow settlements through eras while you develop your own path. Earn trust through helping — defend against raids, complete quests, contribute resources — and villages may eventually recognize you as chief.*

---

## The five pillars

These are non-negotiable. Any new feature must clearly serve at least one.

### 1. Villages are autonomous

Villages grow whether or not the player engages. The NPC builder works the construction queue on its own. Era progression happens on its own timeline. Quests appear on their own. Resources are produced on their own. **The player is one input among several, not the central actor.**

This is the foundation. If a feature requires the player to be present to function, it's wrong.

### 2. Player helps, not commands

There is no UI for "give this order to the NPC." The player influences the village by:

- Depositing resources into the stock.
- Adding buildings to the construction queue.
- Upgrading placed buildings (within the limits the NPC executes).
- Completing quests that the village emits.
- Defending the village against raids.

That's it. No direct NPC control, no colony-management panels, no "go build X" buttons.

### 3. Datapack-first content

New buildings, eras, quests, prices, and builder behavior are added by **dropping JSON files into a datapack**. Code changes are not required for content additions.

The five `datapack/*.java` handlers (`BuildingDataHandler`, `EraTransitionDataHandler`, `QuestDataHandler`, `BuilderConfigDataHandler`, `TradePriceDataHandler`) are the contract. Anything that bypasses them — a hardcoded building list, a code-only quest trigger, a non-JSON era — is a regression.

### 4. NPC builder is the actor

The NPC builder is the *only* entity that places blocks in villages. Player placement is forbidden. This keeps the autonomous-NPC principle intact and makes the village feel alive.

Future NPCs (issue [#5](https://github.com/lowern1ght/burg/issues/5) — farmer, miner, forager) extend the existing `Npc` class with new `Role` enum values and new goals, not by adding new entity classes.

### 5. Vanilla feel

Burg's GUI stays minimal. The existing Town Hub (Stock / Construction / Upgrade tabs + draggable widgets) is the surface. Subtle additions only — colored pips, small calendar indicators, new widgets that respect the existing visual language.

The mod should feel like **vanilla Minecraft with extra life**, not a UI overhaul.

---

## Out of scope

The following are explicitly NOT goals. Adding them will be declined even if well-implemented. If you think one of these should be in scope, open a discussion issue and propose changing this document.

### Hard bans

- ❌ **MineColonies-style colony management.** Player is not the boss. No "issue order" mechanics.
- ❌ **Player-controlled NPCs.** No "hire this builder" or "direct this villager" mechanics.
- ❌ **Multi-block structures requiring player placement.** Player does not place blocks. The NPC places blocks. Always.
- ❌ **Combat overhaul.** Use vanilla combat. Burg does not change how mobs or weapons work.
- ❌ **Magic / tech systems.** No mana, no power, no automation logic beyond what's already in vanilla.
- ❌ **Heavy GUI panels.** No new tabs in the Town Hub. No floating menus. No JEI-style sidebar.
- ❌ **Cross-feature coupling with other mods.** Compatibility is allowed (e.g. JEI/REI overlays), but Burg's features must not depend on other mods being present.
- ❌ **Player-placed decorations** (custom furniture, signs with custom text, etc.). Vanilla decoration stays.
- ❌ **Custom villagers with custom trades.** Use vanilla villagers.

### Soft concerns (handle carefully)

- ⚠️ **Per-village reputation decay.** Decay creates grind pressure; acceptable only if subtle and explained.
- ⚠️ **Long progression chains.** Era trees should branch, not be a single linear line.
- ⚠️ **Costly buildings without gameplay payoff.** Every building must contribute to a recognizable mechanic.
- ⚠️ **Cross-mod integration beyond display overlays.** Burg is a content mod, not a library.

---

## Discipline rules

When designing or reviewing a feature, answer these questions:

### Design questions

1. **Which pillar does this serve?** Every feature serves at least one of the five pillars. If none, the feature is wrong.
2. **Is this vanilla-feel?** Could a Minecraft player pick this up without reading a tutorial? If no, simplify.
3. **Does this require player presence?** If yes, redesign so the village works regardless.

### Implementation questions

4. **Can this be done in a datapack?** If yes, prefer the JSON handler over Java code.
5. **Does this add a new entity class?** If yes, reconsider — extending the existing `Npc` class is almost always better.
6. **Does this add a new GUI tab?** If yes, reconsider — a new widget in the existing hub is almost always better.
7. **Does this break save format compatibility?** If yes, design a migration path or accept the break and document it.

### Scope questions

8. **Does this feature open the door to five more features?** If yes, defer the feature until the design space is constrained.
9. **Is the feature one we can remove?** A feature that can't be cleanly removed (because other features depend on it) is a red flag.
10. **Is the feature reversible for the player?** If the player commits to it and regrets, can they undo?

---

## Open design questions (in flight)

These are unresolved and currently blocking related work. See [issues](https://github.com/lowern1ght/burg/issues) for context.

- **Reputation: per-village or global?** If the player helps village A, does village B know?
- **Reputation decay?** If the player is absent for 7 days, does the village forget?
- **"Chief" unlocks?** What does becoming chief actually let the player do?
- **Vanilla-feel boundary.** Where exactly does the line fall for "still Minecraft"?

These will be answered by issue [#2](https://github.com/lowern1ght/burg/issues/2) and the answers will be folded into this document.

---

## Why this document exists

The original upstream mod drifted significantly across its 0.0.x releases — going from "Town Map" to "100 buildings and 5 era systems and trading and quests and side activities" within a year. Each feature was reasonable in isolation; together they became a kitchen-sink colony sim.

Burg explicitly does not want to be a kitchen sink. This document is the discipline mechanism. If a contributor reads only the codebase and not this document, they'll likely build something the project doesn't want.

If you want to propose a change to this document, open an issue titled "Philosophy: propose change to ..." and explain the new direction. Changes require explicit approval — the five pillars and the hard bans are stable.

---

## Related

- [`README.md`](../README.md) — public-facing summary
- [`CONTRIBUTING.md`](../CONTRIBUTING.md) — how to file issues and PRs
- [`docs/ARCHITECTURE.md`](ARCHITECTURE.md) — system map
- Issue [#2](https://github.com/lowern1ght/burg/issues/2) — design philosophy tracking