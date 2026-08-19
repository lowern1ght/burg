# tasks — `hub-becomes-window`

## 1. Model + state

- [x] 1.1 Add `HubMode { CONSTRUCTION, SUPPLY }` to `town/Town.java` and a
  `hubMode` accessor derived from standing + structural predicate.
- [ ] 1.2 Add the standing-threshold and structural-flag defaults to
  `BuilderConfigDataHandler` (read at town registration; not per-tick).
- [ ] 1.3 Add a unit test for the transition predicate (no GUI required
  yet — pure logic over a fixture `Town`).
  - **Gap (ADR-0022 wiring PR, 2026-08-19):** the `:common` test
    target does not reach `Town` — `Town` imports `net.minecraft.*`
    on its god-object fields (the multiloader-common contract
    deliberately keeps the test classpath Minecraft-free; only
    `org.lowern1ght.burg.people` is reachable). The wiring PR
    attempted to land `TownHubModeTest` in
    `common/src/test/.../town/` and failed with
    `NoClassDefFoundError: net/minecraft/nbt/Tag` on `new Town()`.
    The bare JVM test for `hubMode()` is **deferred** until one of:
    (a) a `:neoforge` test target exists, or (b) `Town` is
    refactored to be Minecraft-free (unlikely — god-object).
    Until then, the only coverage of the predicate is the
    `tools/describe.py town_hub` walk-through and the
    `HubModeTest` enum-level contract.

## 2. GUI

- [ ] 2.1 Fork `TownHubScreen` into two widget sets, gated on `Town#hubMode`.
  `CONSTRUCTION` widgets = today's screen, untouched. `SUPPLY` widgets =
  read-only intent list + a "supply" input field (per-item accept).
- [ ] 2.2 Update the hub title and tooltip text per act (i18n keys
  `town.hub.title.construction` / `town.hub.title.supply`).
- [x] 2.3 Add the `_supply_` packet to the network layer (17 packets
  today; this lands packet #18). Mirror `CustomPacketPayload` style.
  - Landed in the ADR-0022 wiring PR (`feature/screen-wire`,
    2026-08-19): `S2COpenTownHubV2Packet` carries the anchor pos
    only; the wire-format intent list is the act-4 follow-up PR.

## 3. Anchor block

- [x] 3.1 Branch `TownAnchorBlock.use()` on `Town#hubMode`. Add a
  capability-test scenario in `tests/player-role/` that asserts:
  `open(anchor, stranger)` ⇒ "you are not of this village",
  `open(anchor, guest)` ⇒ `CONSTRUCTION`,
  `open(anchor, chief)` ⇒ `SUPPLY` (if structural predicate met).
  - Landed in the ADR-0022 wiring PR (`feature/screen-wire`,
    2026-08-19): the `useWithoutItem` branch dispatches on
    `town.hubMode()`. CONSTRUCTION-mode keeps the legacy
    `sendTownHubPacket + openMenu(be)` path untouched. SUPPLY-mode
    sends `S2COpenTownHubV2Packet` and the client opens
    `TownHubScreenV2.withEmptyIntent()` via a game-bus
    `ClientTickEvent.Pre` poll. The V2 screen renders the
    `NO_INTENT_KEY` placeholder until the act-4 follow-up PR
    ships the wire-format intent list.

## 4. Datapack keys

- [ ] 4.1 Add `hub.transition_standing_threshold` and
  `hub.transition_structure_required` to the shipped
  `data/burg/builder/*.json` file (defaults: 50 /
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
