# Why

**The mod's entrance does not exist.** A new player can spend hours in a
world and never learn Burg is installed. (ROADMAP.md §Act 0 — Arrival.)
The strongest lever available is *converting an ordinary vanilla village*
rather than placing a rival structure beside it (Ruling 1). The hard part,
named by ROADMAP.md: a converted vanilla village has no `ConnectionPoint`
carried by its vanilla pieces — the growth system uses jigsaw on Burg NBTs
only — so the NPC builder has nowhere to attach and the converted town
cannot grow at all.

This change lands the cheapest honest resolution: **a bridgehead piece**.
The player places the Town Anchor at the meeting point and one Burg
street piece is laid at the village edge (or replacing one vanilla piece).
That piece arrives with connection points and *the rest of the growth
system works unchanged*. Three alternatives were considered (ROADMAP.md
§Act 0 enumerates them); the bridgehead is the preferred one, and this
change ships it.

Without the bridgehead, the mod has no Act 0 — which means it has no
player, which means every other spec in `openspec/specs/` is a design
without an audience. This is the single highest-leverage Act 0 task.

# What Changes

- **CAP-NEW** `vanilla-village-anchor`: when the Town Anchor is placed at
  the meeting point of a vanilla village that is not yet registered, the
  anchor binds, runs `Citizens.enlistAllNear`, marks existing houses as
  occupied footprints, and lays one bridgehead street piece. The
  bridgehead carries connection points so existing growth from now on
  works unchanged.
- **CAP-NEW** `bridgehead-street-piece`: a single Burg NBT (jigsaw piece,
  vanilla format) that ships in `common/src/main/resources/data/burg/structure/plains/`
  carrying two outward-facing `ConnectionPoint`s. Sized to fit at a vanilla
  village edge without modifying terrain beyond what's required for its
  own footprint.

# Capabilities

## New Capabilities

- `vanilla-village-anchor`: covers the placement flow, the conversion
  criteria ("is this an unregistered vanilla village?"), and the
  post-bind guarantees (anchor unbreakable, enlisted villagers present,
  existing houses reserved).
- `bridgehead-street-piece`: covers the NBT itself (its connection-point
  contract, the placement rules, and the "measure against the author"
  test from the burg-buildings skill).

# Impact

Affected code:
- `common/src/main/java/org/dawnoftime/onceuponatown/block/TownAnchorBlock.java`
  — `use()` becomes a binding entrypoint (in addition to today's hub
  opening)
- `common/src/main/java/org/dawnoftime/onceuponatown/town/Town.java` —
  new constructor path for "town sourced from vanilla village"
- `common/src/main/java/org/dawnoftime/onceuponatown/worldgen/`
  — bridgehead piece registration; replaces 1 jigsaw template entry

Affected data:
- `common/src/main/resources/data/burg/structure/plains/bridgehead.nbt`
  — NEW file. It is read-only corpus territory after creation (FORK_NOTICE
  + CLAUDE.md) — once measured and `selfgate.py`-clean, it becomes a
  calibration artefact. Measure with `tools/check_integrity.py` and
  `tools/selfgate.py` before commit.

Affected docs:
- `docs/02-roadmap/ROADMAP.md` §Act 0 — already written; this change
  makes it shippable.
- `docs/07-state/STATUS.md` — `worldgen` row's note updates; the row
  remains `build-green` until an in-world walk-through is recorded.

Verification:
- `tools/check_integrity.py bridgehead.nbt` exits 0.
- `tools/selfgate.py` exits 0 against `bridgehead.nbt`.
- A recorded walk-through: player spawns, walks to a plains village,
  places the anchor, sees one bridgehead piece appear at the edge with
  connection points; the NPC builder attaches the first growth piece on
  the next tick. STATUS.md `worldgen` row moves to `verified-in-game`
  only when this is recorded in `docs/07-state/`.

