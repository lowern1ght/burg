# tasks — `hub-becomes-window`

## 1. Model + state

- [x] 1.1 Add `HubMode { CONSTRUCTION, SUPPLY }` and `HubView` record to
  `domain/settlement/`. Town exposes `hubView()` (and `hubMode()` as a
  convenience) — derived from the construction queue + acquisition.
  HubView is `EMPTY` when the construction queue is empty OR
  acquisition is outside the act-4 set `{ELEVATED, FOUNDED}`. Carved
  this PR (ADR-0019). (`common/src/main/java/org/lowern1ght/burg/
  domain/settlement/HubMode.java`,
  `HubView.java`; `Town#hubView()` at the constructionQueueView seam.)
- [ ] 1.2 Add the standing-threshold and structural-flag defaults to
  `BuilderConfigDataHandler` (read at town registration; not per-tick).
  **Out of scope for the first carve** — the predicate uses the
  queue-emptiness + acquisition gate this PR; the structural-flag
  defaults land with the SUPPLY-mode widget carve so the GUI reads
  thresholds the same way the domain predicate does.
- [x] 1.3 Unit test for the HubView + HubMode shape on a bare JVM
  (`HubViewTest`). The full predicate test (Town fixture + standing
  book + structural flags) lands with the SUPPLY-mode widget carve
  — the additive carve this PR proves out the type shape, not the
  end-to-end tick.

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

- [x] 6.1 `openspec validate hub-becomes-window --type change` exits 0
  (strict mode also clean after SHALL/MUST strengthening this PR).
- [ ] 6.2 `tools/describe.py town_hub <world-id>` reads back the
  current `HubMode` per town.
- [ ] 6.3 A recorded in-world walk-through (acts 0–3 — both modes side
  by side — act 4 — `SUPPLY` mode engages after the transition).
  STATUS.md `client` row moves to `verified-in-game` only when that
  walk-through is recorded in `docs/07-state/`.

## 7. Application wiring (ADR-0018 follow-up)

- [x] 7.1 `Town.tryAddToConstructionQueue` builds the construction
  intent as a `ConstructionIntent.NewBuild` first and validates against
  the immutable `ConstructionQueue` view's capacity before mutating the
  legacy list (ADR-0019 §"Hub setter forward to ConstructionQueue.enqueue
  through a domain method"). The legacy list stays the NBT owner and the
  source of truth on disk. Carved this PR.
- [ ] 7.2 `C2SDepositPacket` migrates to `new SupplyStock.Handler(adapter).
  handle(...)` for the act-4 player path. ** Out of scope for the first
  carve** — ADR-0018 §"diffuse rewiring to hub-becomes-window" defers
  the migration until the SUPPLY-mode widget lands; a TODO comment on
  the packet records the follow-up. (`network/C2SDepositPacket.java`.)
- [ ] 7.3 `C2SQueueBuildingPacket` migrates to a
  `QueueConstruction.Handler(adapter).handle(...)` for the act-4 player
  path. Same shape as 7.2; lands with the widget carve.