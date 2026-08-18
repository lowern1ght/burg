# Architecture

A tour of Burg's subsystems, with file:line references. Use this as a map when reading the code or planning a change.

> This document describes the **1.20.1-reborn branch** as of mid-2026. After the NeoForge 1.21.1 port (issue [#1](https://github.com/lowern1ght/burg/issues/1)) lands, some file paths and APIs will change. The structural relationships (state vs. tick vs. AI vs. datapack) will not.

---

## High-level shape

Burg is layered:

```
+------------------ client --------------------+
|  TownHubScreen, draggable widgets, packets  |
+------------------ client --------------------+

------------------ common -----------------------
|  state, tick, ai, datapack, network, worldgen |
------------------ common -----------------------

------------------ loader -----------------------
|  neoforge: mod entrypoint, registries        |
|  (was: forge + fabric, now neoforge only)   |
------------------ loader -----------------------
```

The `common/` module is loader-agnostic; `neoforge/` (planned, was `forge/`) wires it into NeoForge.

---

## State layer (`town/`)

The single source of truth for "what does this village look like right now."

```
World save (SavedData: ouat_towns)
  └── LevelTowns                (town/LevelTowns.java:18)
       └── Map<Long, Town>      (key = BlockPos.asLong() of anchor)
            └── Town            (town/Town.java:33)  ← god-object, 967 lines
                 ├── buildings:           List<PlacedBuilding>
                 ├── freeConnections:     List<ConnectionPoint>
                 ├── constructionQueue:   List<QueueEntry.{NewBuild, Upgrade}>
                 ├── activeBuilds:        Map<slot, ActiveBuildState>
                 ├── activeQuests:        List<Quest>
                 ├── unlockedBuildingIds: Set<String>
                 ├── activityLog:         Deque<TownLogEntry>
                 ├── chatSubscribers:     Set<UUID>
                 ├── builders:            List<UUID>     (NPC UUIDs)
                 └── currentEra, currentMaxWeight, ...
```

`Town` is a god-object. Everything that mutates village state goes through it. The `toNbt()` / `fromNbt()` pair at the bottom of the file handles save/load with backward-compat shims.

**Known tech debt:**
- `Town.java` is ~1000 LOC. Many responsibilities. Could be split (post-port).
- `fromNbt()` has commented "BootstrapQueue: silently ignored" — vestigial code from an earlier feature.

---

## Tick layer (`tick/`)

Server-side, runs once per `ServerTickEvent.END`. Orchestrates all per-tick work.

```
TickScheduler.tick(server)                          (tick/TickScheduler.java:28)
  ├── for each (town, level):
  │    ├── ProductionManager.tick                    (tick/ProductionManager.java:27)
  │    │     ├── for each placed building:
  │    │     │    ├── produce resources (every N ticks, scaled by cadence multiplier)
  │    │     │    └── run transformations (consume inputs, produce outputs)
  │    │     └── push stock update to watchers (throttled to 60 ticks)
  │    ├── FoodManager.tick                          (read separately)
  │    │     └── residents/herd eat (consume food, may starve)
  │    ├── tickQuests (in TickScheduler)             (tick/TickScheduler.java:106)
  │    │     └── for each quest def:
  │    │          ├── check prerequisites (era, residents, orientation, stock)
  │    │          └── if met, spawn quest in town
  │    └── EraManager.tick                           (tick/EraManager.java:13)
  │          └── currently no-op (era advance is player-triggered via packet)
  │
  └── every 20 ticks, for each town:
       └── respawn missing NPC builders (if player nearby, chunks loaded)
```

`EraManager.advance()` is the only non-tick entry point — it's called from `C2SAdvanceEraPacket` when the player requests an era transition.

---

## AI layer (`entity/`)

The NPC builder is a `PathfinderMob` with a custom state machine.

```
Npc (entity/Npc.java:39) — extends PathfinderMob
  └── state machine (entity/ai/SimpleStateMachine.java)
       └── active goal: BuildGoal (entity/ai/BuildGoal.java:38)
            ├── phase = MOVING
            │    └── GoToPosition walks to action.getTargetPos()
            ├── (arrived)
            ├── if action.isInstant() → executeInstant() → DONE
            │
            └── phase = BUILDING
                 ├── prepareBlocks() → ordered List<SchematicBlock>
                 ├── for each block:
                 │    ├── pathfind to block
                 │    ├── place block (sound + animation)
                 │    └── burst rhythm: 4 tick delay, then 10–18 tick pause
                 ├── maybe: read map animation (5% chance at burst boundary)
                 └── onComplete() → phase = DONE

BuildAction (entity/ai/BuildAction.java) — pluggable interface
  ├── NewBuildAction  — places a full NBT template block-by-block
  ├── UpgradeAction   — applies a visual diff between two NBT levels
  └── future: DemolitionAction, RepairAction, etc. (just implement the interface)
```

`OuatWalkNodeEvaluator` (entity/ai/) overrides vanilla pathfinding so the NPC can open fence gates and doors.

Secondary activities (mining, crafting) are configured via `data/onceuponatown/jobs/builder.json` and triggered when the construction queue is empty.

---

## Datapack layer (`datapack/`)

Five JSON loaders, one per content type. All reload on server start.

```
data/onceuponatown/
├── buildings/*.json        → BuildingDataHandler      (datapack/BuildingDataHandler.java:30)
├── eras/*.json             → EraTransitionDataHandler (datapack/EraTransitionDataHandler.java:29)
├── quests/*.json           → QuestDataHandler         (datapack/QuestDataHandler.java:23)
├── jobs/builder.json       → BuilderConfigDataHandler (datapack/BuilderConfigDataHandler.java:21)
└── config/
    ├── trade_prices.json   → TradePriceDataHandler
    ├── food_list.json       → FoodListDataHandler
    └── building_list.json   → BuildingListDataHandler   (catalog sort order)
```

Each handler exposes:
- `reload(MinecraftServer)` — reparse all JSON, populate the registry map.
- `get(String id)` / `getAll()` — typed accessors.

Datapack content is the project's primary extension surface. Adding a new building or era requires no Java changes.

### Building JSON shape

```json
{
  "id": "carpenter",
  "nbt": "onceuponatown:plains/jobs/carpenter",
  "entry_pool": "onceuponatown:jobs",
  "icon_item": "minecraft:chiseled_bookshelf",
  "category": "jobs",
  "weight": 3,
  "required_buildings": [{ "defId": "grove", "count": 2 }],
  "construction_cost":  [{ "item": "minecraft:oak_log", "amount": 35 }],
  "production":         [{ "item": "minecraft:oak_planks", "amount": 4,
                          "every_ticks": 600, "capacity_stacks": 4 }],
  "transformations":    [{ "inputs": [...], "output": "...", "output_amount": 1,
                          "output_capacity_stacks": 1 }],
  "upgrades":           [{ "capacity_stacks_add": 1, "upgrade_cost": [...] }],
  "nbt_levels":         ["onceuponatown:plains/jobs/carpenter_lvl1", ...],
  "residents": 2,
  "consumption_per_resident": 1.0
}
```

Full schema is captured in `carpenter.json` (datapack/buildings/) — that's the reference implementation.

### Era tree

```
Era 0  (Settlement)
├── starter: settlement (Pastoral)
├── starter: settlement_2 (Agricultural)
└── starter: settlement_3 (Industrial)

Era 1  (Village) — same three orientations
├── 1_pastoral      →  Pastoral branch
├── 1_agricultural  →  Agricultural branch
└── 1_industrial    →  Industrial branch

Era 2  — branches diverge
├── 2_urban         (from Industrial)
├── 2_rural         (from Agricultural)
├── 2_ranching      (from Pastoral)
├── 2_cooking       (from Pastoral)
└── 2_forge         (from Industrial)

Era 3  — terminal state
├── 3_urban
├── 3_rural
├── 3_ranching
├── 3_cooking
└── 3_forge
```

Each era transition file (`data/onceuponatown/eras/N_<name>.json`) specifies: cost, prerequisites (residents, buildings), unlocked buildings, weight cap increase, and optional `unlock_new_builder`.

---

## Worldgen layer

Uses **vanilla jigsaw** for structure placement; custom logic for terrain matching and post-placement wiring.

```
worldgen/structure/plains_town.json
  └── type: minecraft:jigsaw
       ├── start_pool: onceuponatown:plains/starters
       └── size: 2 (template pool depth)

worldgen/template_pool/plains/
  ├── starters.json  — picks the settlement starter (settlement / _2 / _3)
  ├── streets.json   — roads connecting buildings
  ├── houses.json    — house templates
  └── jobs.json      — job building templates

data/onceuponatown/structures/plains/
  ├── starters/*.nbt
  ├── houses/*.nbt     (per level: house_lvl1.nbt, house_lvl2.nbt, ...)
  ├── jobs/*.nbt
  └── streets/*.nbt

ChunkGeneratorMixin              (mixin/ChunkGeneratorMixin.java)
  └── custom terrain carving for roads (handled during chunk gen, not after)

BuildSchematic                   (building/schematic/BuildSchematic.java)
  └── reads .nbt structure files at runtime
       ├── decodes block states and NBT
       └── places block-by-block via BuildGoal
```

---

## Client layer (`client/`)

The Town Hub is a `Screen` with three tabs and four draggable widgets.

```
TownHubScreen                    (client/screen/TownHubScreen.java:42)
  ├── 3 tabs (left side buttons):
  │    ├── Tab 0: Stock       (textures/gui/town_hub.png)
  │    │    ├── inventory slots
  │    │    ├── trade zone (sell to / buy from village)
  │    │    └── activity log (last 20 events)
  │    ├── Tab 1: Construction (textures/gui/town_construction.png)
  │    │    ├── queue row (54 slots = 6 rows × 9 cols)
  │    │    ├── building catalog (3 rows × 9 cols, scrollable)
  │    │    └── weight bar (committed weight / cap)
  │    └── Tab 2: Upgrade     (textures/gui/town_upgrade.png)
  │         └── placed buildings list + level info
  │
  └── 4 draggable widgets (left side of screen):
       ├── MapDraggableWidget       — 2D top-down town map
       ├── TownSummaryWidget        — production / transformation rollup
       ├── EraProgressDraggableWidget — current era + available transitions
       └── QuestHubWidget           — active quests + progress

NpcRenderer                      (client/renderer/NpcRenderer.java)
  └── custom entity model
       ├── NpcModel                — geometry
       ├── NpcClothesLayer         — clothing overlay
       └── animations: idle, walk, read-map, swing-tool, cross-arms
```

Widget positions persist per-client-session (in static fields, reset on MC restart). Should be per-player config — known tech debt.

---

## Network layer (`network/`)

17 server↔client packets, all using `SimpleChannel` (1.20.1) — to be rewritten to `CustomPacketPayload` for 1.21.1 (issue [#1](https://github.com/lowern1ght/burg/issues/1)).

```
Server → Client (S2C, 8 packets)
  ├── S2CTownHubPacket          (full hub snapshot on open)
  ├── S2CBuildingDefsPacket     (building defs, sent once per session)
  ├── S2CBuildingListPacket     (placed buildings + queue, on changes)
  ├── S2CStockUpdatePacket      (stock snapshot, throttled to 60 ticks)
  ├── S2CQuestUpdatePacket      (quest list, on changes)
  ├── S2CEraUpdatePacket        (current era + transitions, on advance)
  ├── S2CCitizenUpdatePacket    (residents / herd counts, daily)
  └── S2CLogEntryPacket         (single log entry, on event)

Client → Server (C2S, 9 packets)
  ├── C2SQueueBuildingPacket    (queue a new build)
  ├── C2SRemoveQueuedBuildingPacket
  ├── C2SUpgradeBuildingPacket  (queue an upgrade)
  ├── C2SDepositPacket          (player → village stock)
  ├── C2SBuyPacket              (village → player)
  ├── C2SRequestStockPacket     (request full stock snapshot)
  ├── C2SContributeQuestPacket  (submit quest items)
  ├── C2SAdvanceEraPacket       (trigger era transition)
  └── C2SToggleChatBroadcastPacket (opt in/out of log→chat)

NetworkHelper                    (network/NetworkHelper.java)
  └── central dispatcher
       ├── `send*` methods  — send from client to server
       └── `push*ToWatchers` — broadcast to players who have the hub open for a town
```

---

## Registries (`registry/`)

Six static fields, populated by the loader module at startup. The common module holds the references; the loader wires them.

```
BlockRegistry.TOWN_ANCHOR                  — TownAnchorBlock
BlockEntityRegistry.TOWN_ANCHOR            — BlockEntityType<TownAnchorBlockEntity>
ItemRegistry.TOWN_ANCHOR                   — block item for the anchor
EntityRegistry.NPC                         — EntityType<Npc>
MenuRegistry.TOWN_HUB                      — MenuType<TownHubMenu>
```

The single block + single entity is intentional — see [`docs/PHILOSOPHY.md` pillar #4](../01-vision/PHILOSOPHY.md#4-npc-builder-is-the-actor).

---

## Commands (`command/`)

Single command class: `TownCommand`. Mostly admin/debug — not a player-facing interface. Player interaction is through the GUI + packets.

---

## Where to start reading

If you want to understand the project, read these files in order:

1. `town/Town.java` — the state model. Long but the source of truth.
2. `tick/TickScheduler.java` — the orchestration. Short and clear.
3. `datapack/BuildingDataHandler.java` — the datapack contract.
4. `entity/ai/BuildGoal.java` — the NPC's main activity.
5. `client/screen/TownHubScreen.java` — the player-facing UI.
6. `common/src/main/resources/data/onceuponatown/buildings/carpenter.json` — the JSON schema in practice.

If you want to make a change, identify which subsystem it touches, then look at the corresponding section above.

---

## Target DDD shape

Everything above describes the code as it is. The code as it is heading
is recorded in [ADR-0008](../06-decisions/ADR-0008-ddd-foundation.md):
five bounded contexts (**Settlement**, **Realm**, **Diplomacy**,
**War**, **Content-as-shared-kernel**), each layered
`domain / application / infrastructure`, with Minecraft types kept out
of the domain behind value-object wrappers (`TownId`, `BlockCoord`,
`CitizenId`, `ItemId`).

The landing zone is an empty package skeleton under
`common/src/main/java/org/lowern1ght/burg/{domain,application,infrastructure}/`.
`Town` remains the aggregate root of Settlement and is **not** rewritten —
responsibilities move out one carve per change (strangler), behind a
facade that keeps the `ouat_towns` NBT shape so old worlds keep loading.
New act-4/act-5 systems (realm, diplomacy, war) go into the new packages
from day one; nothing gameplay-facing changes until a carve is verified
in a running game.

---

## Related

- [`docs/PHILOSOPHY.md`](../01-vision/PHILOSOPHY.md) — design pillars and out-of-scope rules
- [ADR-0008](../06-decisions/ADR-0008-ddd-foundation.md) — DDD foundation decision this section summarizes
- [`CONTRIBUTING.md`](../../CONTRIBUTING.md) — PR process
- Issue [#1](https://github.com/lowern1ght/burg/issues/1) — NeoForge 1.21.1 port (will refactor some file paths)
- Issue [#11](https://github.com/lowern1ght/burg/issues/11) — code rename `onceuponatown` → `burg`