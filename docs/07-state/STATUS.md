# Burg — subsystem status

Per-subsystem tracker. Three states: `build-green` (compiles, contact sheets render, no
in-game verification), `verified-in-game` (a real player has walked through it in a running
world), `broken` / `partial` (known not to work), `not-started` (design only or absent).

This doc exists because of [`ROADMAP.md`](../02-roadmap/ROADMAP.md)'s closing line:
**"Nothing on this road has been verified in a running game."** Every commit behind the mod
went in on a green build and a contact sheet. [`VISION.md`](../01-vision/VISION.md) repeats
the same rule for the chief→king→realm arc. The gap between "builds" and "works in a world"
is the whole point of this table — rows default to `build-green`, not to `verified-in-game`.

## Subsystems

Rows grouped by layer. "Notes" cite the evidence (commit, checker, doc line) and the known
gap. Absence of a `verified-in-game` row is intentional.

### Engine layers

| subsystem | state | notes |
|---|---|---|
| state (`town/Town`, `LevelTowns`, SavedData) | build-green | God-object ~1000 LOC (ARCHITECTURE.md §state), but nine DDD carves wrap Minecraft-free value objects around every piece of state the tick reads or writes (ADR-0009/0010/0011/0012/0013/0015/0016/0017; see "DDD carves (2026-08-19)"). `Town.stockLedger()` / `constructionQueueView()` / `questLog()` are the read views; `Town.constructionQueue` (ADR-0027, PR #48) and `Town.questLog` (ADR-0028, PR #49) are now SoT, `PlacedBuilding.outputLedger` is SoT (#50). `Town.addZoning(Zone, int)` + `Town.addRoadSegment(RoadSegment)` are the structural-flags mutators (PR #54); `Town.hubMode()` is the full three-leg predicate (acquisition + structural + act threshold, PR #51). Per-player standing is now a `StandingBook` roll on `Town`. `Realm`/`Kingdom` layer above `Town` does not exist (VISION.md). |
| tick (`TickScheduler`, `ProductionManager`, `FoodManager`, quests, era) | build-green | Production tick arithmetic re-routed through `ProductionPlan.computeDueOutputs` + `TransformationRule.apply` (ADR-0015, PR #45); `tickTransformer` output side migrated to per-instance `StockLedger` (`8034522`). The real-planner seam is `Town.addZoning` + `addRoadSegment` wired from `TickScheduler.tick` (PR #56, `4fe8e1b`); `tickZoning` + `tickRoadPlans` retired to no-op stubs after the PR-#56 mistake (PR #71, `df976a2`) and seam-pinned (`f51a978`, `9a41e42`) — the real zoning layer / road planner are still future carves, the seam has teeth today. Quest engine tick collapses onto `Town.findQuestDef(defId)` port (PR #57, `122bb51`). `EraManager.tick` is a no-op; era advance is player-triggered. |
| ai (`Npc`, `BuildGoal`, state machine, `OuatWalkNodeEvaluator`) | build-green | Builder places blocks block-by-block with burst rhythm; opens gates/doors (ARCHITECTURE.md §ai). `DemolitionAction`/`RepairAction` are future stubs. |
| datapack (buildings / eras / quests / jobs / config loaders) | build-green | Author `plains` corpus reads 125/125 after byte-exact CRLF repair (CLAUDE.md). Five JSON loaders reload on server start. Game loop over them unverified. |
| worldgen (`plains_town` jigsaw, terrain matcher) | build-green | Plains/meadow village replacement landed (`a76bbea`). Vanilla-village **conversion** not wired — Act 0's bridgehead/connector problem is open. Player spawn does not lead to a village. |
| client (`TownHubScreen`, `NpcRenderer`, draggable widgets, layers) | build-green | Black-screen regression fixed (`a6ada7f`, a layer baked but never registered). `TownHubScreenV2` (act-4 SUPPLY-mode) lands on the new bare-JVM UI engine under `common/.../common/ui/` (ADR-0022, `8d08f62`); SUPPLY-mode intent list + `InputField` + status-bar widget wired (`b881758`, `2c19a44`). Legacy `TownHubScreen` stays untouched and still owns the construction-mode hub. Widget positions persist per-session only — known tech debt. |
| network (17 packets, `CustomPacketPayload`) | build-green | Ported to NeoForge 1.21.1 `CustomPacketPayload` (`51d1f11`). All hub/stock/quest/era/citizen/log packets wired. `_supply_` packet (client→server stock supply) added (PR #42, `dc31057`); `C2SContributeQuestPacket` rewired to `questId` → `defId` port (PR #69, `58bd6eb`). |

### Content layers

| subsystem | state | notes |
|---|---|---|
| military-content (fortifications: wall, gate, `wall_tower`, watchtower, barracks, armory, training_yard) | build-green | Style-gated + usability-gated; `selfgate.py` green. Done since the open list: ramp dither in `wall.py`, `wall_tower` ladder, watchtower enterable+climbable all 7 levels (was NO-STAIR on 6), `armory_lvl2` ground floor 30/45→46/46. Open: watchtower crown reads heavy (looks), `watchtower_lvl3`/`lvl4` emit `EMPTY_TOP=1`, fortification style not yet applied to barracks/armory/training_yard. See OPEN-WORK.md. |
| livestock-content (cattle / sheep / pig farmsteads) | build-green | Gates green; `build_livestock.py` fails a rung on any fault (fourth gate). Open: three yards share devices (similarity 0.86–0.87 vs author 0.79), `pasture.py` palette decoupled from `wall.py`. See OPEN-WORK.md. |
| mod-config (Cloth Config API, player-facing knobs) | build-green | Cloth Config API 15.0.140 for 1.21.1 wired as bundled runtime dep (ADR-0021, PR #39, `ac78681`). Five knobs land through `BurgConfig` (`ModConfigSpec`): `villagerGrowthMultiplier` → `people.GrowthMultiplier` → `DaySim.tickDay` (PR #39); `buildCadenceMultiplier` → `BuildCadenceMultiplier` → `ProductionManager.tick` cadence (`c657f42`); `buildingOutputCapPerInstance` → `BuildingOutputCap` (clamp 16..4096, default 256) → `PlacedBuilding` FIFO drain (PR #62, `9cfc438`); `raidCooldownSeconds` → `RaidConfig` → `RaidManager.tick` (PR #51, `2dd8b33`); `actThreshold` → `StandingBook.highestStanding()` + `meetsActThreshold()` → `Town.hubMode()` third leg (PR #51). Cloth GUI save consumer re-fires `BurgConfig.refresh*()` without a world reload. `:common:test` pins the clamp/floor/range/default for every knob. |
| npc-skins (hand-drawn bodies, hair-as-paint, wealth-as-dye) | build-green | 14 bodies shipped at reference density (`d8cfdfe`, `7c85b85`). Two past decisions shipped wrong and were fixed — "cringe" + a black screen (burg-skins SKILL). Poses added: sit, doze, talk (`a956fec`). Crown-groove leak fixed (`3729f34`). |
| npc-schedule (sleep at dusk, work by day) | build-green | "The day decides what a person does, and they sleep in a real bed" (`1d8b63b`). ⚠ ROADMAP cross-cutting table still marks schedule `not started` — that line is stale relative to the commit. |
| npc-interaction (right-click to speak, trade in gold) | build-green | "A person can be spoken to, and traded with, in gold" (`adc4a26`). ⚠ ROADMAP Act 1 still says `mobInteract` is unimplemented on every entity — that line is stale relative to the commit; in-game behaviour unverified either way. |
| npc-idlers (wander / talk / swim / hunt / die of it) | partial | Poses (sit/doze/talk) landed (`a956fec`); full idler set per ROADMAP cross-cutting still `not started`. |
| npc-skill (per-worker skill raising output) | build-green | ROADMAP cross-cutting marks `done` — meaning built; in-game verification still pending under the closing-line rule. |
| settlers (arrive rather than spawn) | build-green | ROADMAP cross-cutting marks `done` (built). `Citizens.enlistAllNear` already worked pre-roadmap. |
| births (man + woman + spare bed) | build-green | ROADMAP cross-cutting marks `done` (built). Cadence is the Cloth `villagerGrowthMultiplier` knob; the bare-JVM `DaySim` is the reference loop. |
| gold (two denominations: nugget + ingot) | build-green | Trade-in-gold landed (`adc4a26`); `MerchantOffer` takes arbitrary `ItemCost`. ROADMAP cross-cutting line stale. |
| the pub | not-started | ROADMAP cross-cutting `not started`, act 2. |
| zoning (people in / industry out) | build-green | ROADMAP cross-cutting marks `done` (built). Industry pushed outside core radius; road laid out to it. Tick-side mutators `Town.addZoning` + `addRoadSegment` wired from `TickScheduler.tick`; real planner on the production path still pending (PR #73 pin). |
| era branch surfacing | build-green | 16 era defs in 4 tiers shipped (ARCHITECTURE.md §datapack). ROADMAP Act 3: the branch is "the mod's strongest asset and is currently invisible" — the choice is never surfaced to the player. |
| i18n (en_us, ru_ru) | build-green | ru_ru added (`b9402bd`); era labels, hub tooltips, quest widgets, chat, commands, anchor all through translatable keys. UI engine carries lang keys as opaque strings (ADR-0022); adapter resolves at draw time. |

## DDD carves (2026-08-19)

The repo moved from a ~1000-line `Town` god-object with no domain types to a
strangler pattern that wraps Minecraft-free value objects around every piece of
village state the production tick reads or writes. Fourteen ADRs and twenty-seven
PRs landed the foundation. None of it is `verified-in-game` — a contact sheet
reads the same; the gates are faster and the seams are visible.

### ADRs accepted 2026-08-19 (in dep order)

| ADR | carve |
|---|---|
| 0008 | DDD foundation: 5 contexts (Settlement, Realm, Diplomacy, War, Content), 3 layers each (domain / application / infrastructure), `Town` as the aggregate root under a strangler facade |
| 0009 | `CitizenId` + `Acquisition` (FREE/ELEVATED/FOUNDED/CAPTURED) + `Standing` + `StandingBook` — first carve; additive NBT, missing-key defaults |
| 0010 | `ItemId` + `StockLedger` — second carve; `Town.stockLedger()` rebuilds the read view from `reserveStock` |
| 0011 | `ConstructionIntent` (sealed `NewBuild`/`Upgrade`) + `ConstructionQueue` — MC queue read view |
| 0012 | `QuestRef` + `QuestLog` — active quests + completion map read view |
| 0013 | Dual-write `StockLedger` field + `applyStockLedger()` domain→MC write path |
| 0014 | Application layer: `TownStandingPort`/`AdjustStanding`, `TownStockPort`/`SupplyStock`; `DomainPurityTest` fences `domain/` + `application/` against `net.minecraft.*` |
| 0015 | `ProductionRule` + `ProductionPlan` + `TransformationRule` — `ProductionManager.tick` arithmetic re-routed through domain helpers |
| 0016 | Dual-write construction queue + quest log; `Town.stampQuestCompletion(defId, gameTime)` mutator |
| 0017 | Realm/Diplomacy/War seed: `RealmId`, `HoldingKind`, `AutonomyBand`, `RelationStance`, `BattleOutcome` — names the 2026-07-31 grilling decisions in code |
| 0021 | Cloth Config API 15.0.140 (bundled runtime dep); `villagerGrowthMultiplier` knob + `people.GrowthMultiplier` value object + `DaySim.tickDay` wire site |
| 0022 | Bare-JVM UI engine (`Rect`/`Point`/`Color`/`TextStyle`/`UiEvent`/`Widget`/`DrawContext`/`Root`/`Container`/`Label`/`Panel`) + act-4 SUPPLY-mode widget; 81 new bare-JVM tests |
| 0027 | `Town.constructionQueue` SoT flip — MC `List<QueueEntry>` rebuilds on demand (was dual-write) |
| 0028 | `Town.questLog` SoT flip — legacy MC fields retire, `activeQuestMap` (LinkedHashMap) carries the rich per-quest data |

### PRs that consumed or followed the ADRs

| PR | what |
|---|---|
| #46 multi | Three Cloth knobs (`BUILD_CADENCE_MULTIPLIER`, `RAID_COOLDOWN_SECONDS`, `ACT_THRESHOLD`) + `Town.applyStockToReserve` static helper + `:neoforge:test` target scaffold |
| #48 act5-sot | `ConstructionQueue` SoT (ADR-0027) |
| #49 questlog-sot | `QuestLog` SoT (ADR-0028) |
| #50 output-side-sot | `PlacedBuilding.outputLedger` SoT; per-instance cap retired, output accumulates until `TownInventory` drains |
| #51 config-and-structural | Readers for the three knobs + `StandingBook.highestStanding()` + `meetsActThreshold()`; `Town.hubMode()` is the full three-leg predicate |
| #52 neoforge-test-target | `:neoforge:test` Gradle target with MC-aware carve |
| #53 tick-raid-wire | `RaidManager.tick` wired into `TickScheduler` + `:neoforge:test` classpath fix |
| #54 structural-fields | `Town.addZoning(Zone, int)` + `Town.addRoadSegment(RoadSegment)` mutators |
| #55 town-redirect-test | `applyStockLedger` redirect test on `:neoforge:test` |
| #56 tick-population | First wire of `addZoning` + `addRoadSegment` from `TickScheduler.tick` |
| #57 quest-tick-port | Engine tick collapses onto `Town.findQuestDef(defId)` port |
| #58 javadoc-fix | 12 broken `@link` refs across `common` + `neoforge` |
| #59 neoforge-test-fixture | Legacy classpath JARs at execution time — fixes 31 `NoClassDefFoundError` failures |
| #60 gitignore | Ignore Minecraft runtime artefacts under `neoforge/` |
| #61 queue-consumer-migration | Migrate `SimpleStateMachine` + `TownHubDataBuilder` off `Town.getConstructionQueue()` onto `constructionQueueView()` |
| #62 building-cap | `BUILDING_OUTPUT_CAP_PER_INSTANCE` Cloth knob + `PlacedBuilding` FIFO drain |
| #63 javadoc-cleanup | Close out remaining Javadoc warnings across `common`+`neoforge` |
| #64 gametest | `gametest` source-set scaffold with `@GameTestHolder BurgGameTests` |
| #65 empty-fixture | `empty5x5.snbt` fixture + `tools/generate_empty5x5.py` generator + 2 pin tests |
| #66 gametest-run | End-to-end `:neoforge:runGameTestServer` pass + CI workflow + mod-bus fix |
| #67 placedbuilding-live | Live `PlacedBuilding` `forceAdd` + inverted Town Anchor assertion |
| #68 core-walk | `structural-flags` `core_populated` walks buildings, not core cells |
| #69 questid-cleanup | `C2SContributeQuestPacket` wire + engine port pinned against re-introducing `questId` |
| #70 quest-tick-live | Live `@GameTest` pins `Town.findQuestDef` on a real MC server |
| #71 planner-population | `tickZoning` + `tickRoadPlans` retired to no-op stubs after the PR-#56 mistake |
| #72 real-planner | Seam pin for bare-JVM classloader reachability of the planner classes |
| #73 planner-pin-update | Final pin of the `TickScheduler` no-op stubs + `addZoning` seam signature |

### Gametest infrastructure

A third rung between "builds + contact sheet" and "verified in a world":

- `:common:test` (≥666) — bare-JVM tests, the cheap fast loop. Touches
  `domain/`, `application/`, `people/`, `common/ui/`, `settlement/ui/`,
  and the Town facade through reflection only (ADR-0026 carve-out for
  `Town.class` metadata).
- `:neoforge:test` (≥56) — MC-aware tests on the merged JAR (PR #52+).
  Real `Town()`, real `TickScheduler`, real `QuestManager`, real
  `RaidManager`, real packets (`C2SContributeQuestPacket`,
  `C2SDepositPacket`); town facade redirects; structural-flags real
  derivations.
- `:neoforge:runGameTestServer` (≥90) — live `@GameTest` runs on a real
  ModLauncher boot (PR #64+). Empty `5×5` stone platform fixture, live
  `PlacedBuilding.forceAdd`, live `Town.findQuestDef`, live worldgen-
  only contract for the Town Anchor.

### Gates the carve is held to

- `:common:test` ≥ **666** — bare-JVM tests, the cheap fast loop.
- `:neoforge:test` ≥ **56** — MC-aware tests on the merged JAR.
- `:neoforge:runGameTestServer` ≥ **90** — live `@GameTest` runs.
- `openspec validate --all` **10/10** — seven capability specs
  (`datapack-content`, `domain-settlement`, `earned-crown-trajectory`,
  `npc-builder-actor`, `player-role`, `vanilla-feel`,
  `village-autonomy`) + three active changes (`hub-becomes-window`,
  `realm-diplomacy-war-seed`, `vanilla-village-conversion`).

## What "verified-in-game" would take

Per ROADMAP, the first act to be *finished* rather than written must be finished in the
world. Concretely: a player spawns, travels, and **finds** a village (Act 0); right-clicks a
citizen and **gets a response** — name, trade, what they are doing (Act 1); the anchor does
**not** open the hub for a stranger, and the notes about him begin. That single walk is the
bar that moves the first rows out of `build-green`. Nothing less counts.

The three test gates above sit *below* this bar — they prove the seams hold, the SoT flips
do not regress, and the live `@GameTest` paths boot on a real server. They are not the bar;
they are the rungs the next walk has to climb.

## How to update

Move a row's state forward only after evidence, and link the evidence (commit SHA,
screenshot description, or session note) in the row's notes. A green build is not evidence;
a contact sheet is not evidence; a `:neoforge:runGameTestServer` pass is not evidence
(it's a rung, not the bar). `build-green → verified-in-game` requires a recorded in-world
walk.

When a new carve or seam pin lands, append a row to the **PRs that consumed or followed the
ADRs** table above with the PR number + one-line summary. New ADRs land in the **ADRs
accepted** table in dep order; supersessions mark the predecessor row with "superseded by
ADR-NNNN" and link the new one. The `DDD carves (2026-08-19)` section is the canonical
landing for this work; do not duplicate its content in row notes — cross-link with
`(see "DDD carves (2026-08-19)")`.

## Related

- [`ROADMAP.md`](../02-roadmap/ROADMAP.md) — the act order and the closing-line rule this doc enforces.
- [`VISION.md`](../01-vision/VISION.md) — the earned-crown arc; same "not verified in a running game" status line.
- [`ARCHITECTURE.md`](../04-engineering/ARCHITECTURE.md) — the subsystem map these rows are derived from.
- [`docs/06-decisions/ADR-0008-ddd-foundation.md`](../06-decisions/ADR-0008-ddd-foundation.md) — the foundation this section summarizes.
- [`OPEN-WORK.md`](OPEN-WORK.md) — the backlog this status feeds.
- [`OPENSPEC-INIT.md`](OPENSPEC-INIT.md), [`OPENSPEC-ARCHIVE-LOG.md`](OPENSPEC-ARCHIVE-LOG.md) — the OpenSpec state this section references for the active changes.
