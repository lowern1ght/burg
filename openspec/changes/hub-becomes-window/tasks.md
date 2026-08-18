# tasks — `hub-becomes-window`

## 1. Model + state

- [ ] 1.1 Add `HubMode { CONSTRUCTION, SUPPLY }` to `town/Town.java` and a
  `hubMode` accessor derived from standing + structural predicate.
- [ ] 1.2 Add the standing-threshold and structural-flag defaults to
  `BuilderConfigDataHandler` (read at town registration; not per-tick).
- [ ] 1.3 Add a unit test for the transition predicate (no GUI required
  yet — pure logic over a fixture `Town`).

## 2. GUI

- [ ] 2.1 Fork `TownHubScreen` into two widget sets, gated on `Town#hubMode`.
  `CONSTRUCTION` widgets = today's screen, untouched. `SUPPLY` widgets =
  read-only intent list + a "supply" input field (per-item accept).
- [ ] 2.2 Update the hub title and tooltip text per act (i18n keys
  `town.hub.title.construction` / `town.hub.title.supply`).
- [ ] 2.3 Add the `_supply_` packet to the network layer (17 packets
  today; this lands packet #18). Mirror `CustomPacketPayload` style.

## 3. Anchor block

- [ ] 3.1 Branch `TownAnchorBlock.use()` on `Town#hubMode`. Add a
  capability-test scenario in `tests/player-role/` that asserts:
  `open(anchor, stranger)` ⇒ "you are not of this village",
  `open(anchor, guest)` ⇒ `CONSTRUCTION`,
  `open(anchor, chief)` ⇒ `SUPPLY` (if structural predicate met).

## 4. Datapack keys

- [ ] 4.1 Add `hub.transition_standing_threshold` and
  `hub.transition_structure_required` to the shipped
  `data/onceuponatown/builder/*.json` file (defaults: 50 /
  `core_populated | industry_zoned | road_laid`).
- [ ] 4.2 Add the schema entries to `BuilderConfigDataHandler` so unknown
  keys do not break servers running older datapacks (per
  `specs/datapack-content` §"schema stability").

## 5. STATUS.md

- [ ] 5.1 Update `client` row in `STATUS.md`: tick "transition lands in
  act 4 — verified only after recorded in-world walk-through".
- [ ] 5.2 Update `tick` row note: `hubMode` is *derived* from standing +
  state predicates, not stored as a separate boolean, so a server with
  this change and an old world does not need a migration.

## 6. Verification

- [ ] 6.1 `openspec validate hub-becomes-window --type change` exits 0.
- [ ] 6.2 `tools/describe.py town_hub <world-id>` reads back the
  current `HubMode` per town.
- [ ] 6.3 A recorded in-world walk-through (acts 0–3 — both modes side
  by side — act 4 — `SUPPLY` mode engages after the transition).
  STATUS.md `client` row moves to `verified-in-game` only when that
  walk-through is recorded in `docs/07-state/`.
