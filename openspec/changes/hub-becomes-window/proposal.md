# Why

Today the town hub is a command console: the player right-clicks the Town
Anchor in any act, picks "Construction", and queues a building. This
contradicts the strict reading of Ruling 3 (ROADMAP.md §"Three rulings"
+ VISION.md §"the hub is a window"). The grilling of 2026-07-31 settled
that **the hub becomes a window, not a command console** — a transition
that lands in act 4 and turns the player's lever from "orders" into
"supply".

This change moves the existing command-console shape into a transition
state: through act 3 it stays a console (the player queues); at act 4 it
becomes a read-only view onto town intent, with supply as the player's
input. The transition is *gated on the act-4 standing threshold* and
*tied to the structural act-4 trigger* (core radius populated, industry
zoning pushed out, road laid). It is the single biggest behavior change
in the mod and the first change that the act-5 vision rests on.

Without this transition, the act-5 endgame (`specs/earned-crown-trajectory`
§"realm grows from inside") has no way to talk to towns the player holds
that are not directly his own colonies — only console verbs, and the
console is what VISION §"the hub is a window" already bans.

# What Changes

- **CAP-NEW** `construction-mode-supply-mode`: a single capability that
  models the hub's two modes (`CONSTRUCTION` for acts 0–3; `SUPPLY` for
  act 4+) and the act-4 transition trigger.
- **CAP-MOD** `town-anchor-block`: the anchor right-click semantics change
  with the act. In `CONSTRUCTION` mode it opens the hub (current behavior,
  unchanged). In `SUPPLY` mode it opens a "what the town wants" view and
  the player's interaction is supplying items that match the town's
  current intent.
- **CAP-MOD** `builder-config-datapack`: a new optional datapack key
  `hub.transition_standing_threshold` (default 50) and
  `hub.transition_structure_required` (default `core_populated | industry_zoned | road_laid`)
  governs the transition. Tunable per datapack.

# Capabilities

## New Capabilities

- `construction-mode-supply-mode`: Defines the two hub modes, the act-4
  transition trigger (standing + structural), and what each mode shows
  / accepts. Covers `player-role` spec §"hub becomes a window (act 4)"
  scenarios.

## Modified Capabilities

- `player-role`: Adds a scenario for the *act-4 transition* itself, and
  tightens the act-3 → act-4 boundary so the hub-mode toggle is enforced
  at event level, not just at proposal level.
- `npc-builder-actor`: Adds a scenario for how the NPC builder reacts to
  a `SUPPLY`-mode change in its surrounding town (it reads the same
  intent list the player does; this is not new behavior but is now
  spec-tested).

# Impact

Affected code:
- `common/src/main/java/org/dawnoftime/onceuponatown/block/TownAnchorBlock.java`
  — branching on town mode + standing
- `common/src/main/java/org/dawnoftime/onceuponatown/client/gui/TownHubScreen.java`
  — two-mode widget set
- `common/src/main/java/org/dawnoftime/onceuponatown/town/Town.java` —
  `HubMode` enum + transition predicate
- `common/src/main/java/org/dawnoftime/onceuponatown/datapack/BuilderConfigDataHandler.java`
  — new optional keys

Affected datapacks:
- `data/onceuponatown/builder/*.json` — new `hub` key (default shipped value)

Affected docs:
- `docs/02-roadmap/ROADMAP.md` §"Act 4" — already written; this change
  makes it shippable.
- `docs/07-state/STATUS.md` — moves `client` row state from
  `build-green` toward `verified-in-game` only after a recorded act-4
  walk-through.
- `openspec/specs/player-role/spec.md` — modified Capability body (in
  this proposal's "Modified Capabilities" list); required to land
  before archive.

Verification:
- New test scenario in `tools/describe.py town_hub` reads back the
  current mode (CONSTRUCTION | SUPPLY) from the running world's
  `LevelTowns` saved data.
- A focused gameplay walk-through lands `verified-in-game` on the
  `client` STATUS row.
