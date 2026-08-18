# Focus — Burg (you) vs Once Upon a Town (the author)

> This document is the answer to *"what does this fork actually want that
> the original didn't?"*. It is the funnel that every change proposal must
> stand inside, written so a contributor reading only this file can decide
> whether their idea belongs here.

The data sources for "what the author wanted" come from three places,
only the first of which is in-tree:

- **`docs/PHILOSOPHY.md` §"Why this document exists"** — explicit on the
  upstream drift. The author went from "Town Map" to "100 buildings and
  5 era systems and trading and quests and side activities" within a year;
  each feature was reasonable in isolation; together they became a
  kitchen-sink colony sim. **The author did not write a vision doc.**
  The fork's `VISION.md` exists precisely *because* upstream never had one.
- **`FORK_NOTICE.md` §"What has diverged from upstream"** — the authored,
  auditable divergence list. Three of the five rows there are about
  packaging (loader, branding, build setup). Two are about substance:
  "Mod feature breadth" (upstream) vs "Player as helper, not leader"
  (fork), and the philosophy emphasis. **This file expands on those two.**
- The upstream repository at `github.com/DawnOfTimeMC/onceuponatown` —
  referenced for any deeper question. This file does not link-by-commit
  because nothing in this fork is a one-file backport; the divergence is
  architectural, not patch-level.

## Where the two diverge at a glance

| Where | The author's Once Upon a Town | Your Burg fork |
|---|---|---|
| **Vision document** | None. Drift was contained only by what features shipped. | `docs/01-vision/VISION.md` is the *authoritative* arbiter. Disagreements with PHILOSOPHY / ROADMAP are flagged in those files. |
| **Player's role, canon** | "Player as colony leader". The mod grew a hub where you queue buildings, you manage the town. | Acts 0–3: player is a stranger who can only trade/supply. Act 4: a window, not a console. Act 5: a king over a *realm*, not just one town. |
| **Endgame** | Effectively none — there is no "you've won" state. New content kept being added. | The earned-crown trajectory with six named acts. Act 5 has *three* endgame paths (elevated / founded / captured) and an autonomy–control slider. |
| **Town acquisition** | Implicit: you just have the town you found/placed. | Three named paths, each with its own obedience profile: **elevated** (loyal), **founded** (loyal by birth), **captured** (resists). |
| **"Player controls NPCs" / colony-management UI** | Allowed, encouraged. The hub became the surface. | **Banned in acts 0–3**; lifts gradually, only for villages the player earned (founded or captured) or that chose him (elevated). |
| **Combat** | Vanilla only, total. The player fights everything himself. | **Two-tier:** raid-scale vanilla combat — the player fights personally; war-scale NPC-vs-NPC, the player commands but does not swing the sword. |
| **Player places blocks in town** | Allowed (the anchor IS a player placement, but the surrounding growth was unfettered). | **Eternal ban.** The NPC builder is the only actor in a town. Even a king commands; he does not lay the stone. |
| **Authority flow** | Top-down. Player ⇒ town. | Bottom-up + earned. Town ⇒ player (named chief, network of chiefs raises to king). |
| **World structure** | One village per player, more or less. | One **metropolis** + **colonies founded by expedition** + **foreign villages** acquired by one of three paths. Three-tier shape; not a flat list. |
| **City-forming factor** | Not present. | **Water**. A village without a river or lake tops out as a village; worldgen must supply this. |
| **Mod loader** | Forge 1.20.1 + Fabric 1.20.1 (multi-loader). | NeoForge 1.21.1 (single target). |
| **Branding & distribution** | "Once Upon a Town" / DawnOfTimeMC, on CurseForge project ID 1545001. | "Burg" / lowern1ght, rebranded 2026-07-26. Original credits retained. |
| **Voice of the mod** | Designer-driven. Tags, labels, tooltip text. | **Author already wrote it for us, and we keep it.** Per `ROADMAP.md §"The author already wrote this"`, two of the four shipped quests are `NOTE` type — villagers writing *about the player*, calling him **the stranger**. The fork inherits this voice and treats it as canon. |

## What that means in practice — for you

The most important sentence, the one that decides every new feature, is:

> **Burg is a medieval life-sim where villages live on their own, and a
> stranger can — through help, or force, or time — become a king over
> them.**

If a feature does not serve that sentence, it does not belong in Burg. The
five pillars (PHILOSOPHY.md) are the operational reading; the
earned-crown trajectory (VISION.md) is the long arc. The two together
form the funnel. **If a feature is in one and not the other, the second
is wrong;** if it's in neither, it's a kitchen-sink feature and gets
rejected.

## What the upstream did *better* — and we keep

The fork is not a "the author was wrong about everything" document. There
are things upstream did that the fork adopts wholesale and treats as
canon. Three:

1. **The author already wrote the stranger-voice in the datapack.**
   `new_visitor` and `trusting_someone_else` are quests the author
   shipped — villagers writing about you behind your back, calling you
   "the stranger". We keep them and treat the voice as load-bearing for
   the mod's tone. (See ROADMAP.md §"The author already wrote this".)
2. **Five datapack handlers.** BuildingDataHandler,
   EraTransitionDataHandler, QuestDataHandler, BuilderConfigDataHandler,
   TradePriceDataHandler. The fork's pillar 3 *is* this contract — the
   author had it, the fork codifies it.
3. **The structure corpus.** `plains/` is the calibration corpus — 125
   NBTs measuring every wall, roof, slab, stair, fence connection. The
   author built it; the fork measures against it (CLAUDE.md §"tools").
   Read it; harvest from it; never write to it.

## What this document is *not*

- **Not a replacement for PHILOSOPHY.md or VISION.md.** It is the
  delta-summary, scoped to the question "what is different from
  upstream". For the deep read, those two docs are authoritative.
- **Not a list of things to revert.** Not a single upstream feature has
  been removed purely for being upstream. Every divergence has a
  measured reason (the fork's own docs cite it).
- **Not a license or contribution change.** GPL-3 stays; attribution
  stays; the fork does not republish upstream's CurseForge ID.

---

## Related

- `docs/01-vision/VISION.md` — earned-crown trajectory (authoritative)
- `docs/01-vision/PHILOSOPHY.md` — five pillars (eternal canon)
- `docs/01-vision/RULINGS.md` — constitutional rulings (R1, R2, R3)
- `docs/02-roadmap/ROADMAP.md` — six acts in order
- `FORK_NOTICE.md` — packaging-level divergence (loader, branding, build)
- `CLAUDE.md` §"Constitutional rulings" — the four-gate model and the
  burg-buildings / burg-skins skill conventions
