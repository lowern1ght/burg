# Data formats

Burg is datapack-first — pillar 3 of the [design philosophy](../01-vision/PHILOSOPHY.md). Adding a building, an era branch, a quest, a trade price, or a settler job is a JSON edit; no Java change is required and no recompile is needed. This doc is the extension contract: the shape of every shipped JSON file, grounded in the actual data under `common/src/main/resources/data/burg/`, plus the handler class that loads each one.

> The five handlers documented in [ARCHITECTURE](ARCHITECTURE.md) have grown to **eight** on the port branch: `SettlerJobsDataHandler` was added when settlers became working villagers, and `BuilderConfig` / `BuildingList` / `FoodList` / `TradePrice` were already split out. All eight reload on server start from `ServerStartingEvent` (`neoforge/.../OuatForge.java:259-268`); the loader namespace is gated to `burg` (the mod id; renamed from `onceuponatown` by [ADR-0007](../06-decisions/ADR-0007-mod-id-rename.md)) so third-party datapacks must use their own namespace.

---

## buildings/*.json

The reference building def. ~59 files ship, covering jobs, houses, military, fields, streets, decor and the three starters. Loaded by `BuildingDataHandler` (`common/.../datapack/BuildingDataHandler.java:30`), which scans `data/<ns>/buildings/*.json`, parses each into a `BuildingDef`, and indexes by `id`. Reload on server start; defs are also pushed to the client on login via `S2CBuildingDefsPacket` (`neoforge/.../OuatForge.java:301-308`).

Minimal example (a job building with one transformation and one upgrade):

```json
{
  "id": "carpenter",
  "nbt": "burg:plains/jobs/carpenter",
  "entry_pool": "burg:jobs",
  "icon_item": "minecraft:chiseled_bookshelf",
  "category": "jobs",
  "zone": "outer",
  "weight": 3,
  "construction_cost": [{ "item": "minecraft:oak_log", "amount": 35 }],
  "transform_input_ratio": 0.1,
  "transform_every_ticks": 1600,
  "transformations": [
    {
      "inputs": [{ "item": "minecraft:oak_planks", "amount": 3 }],
      "output": "minecraft:white_bed",
      "output_amount": 1,
      "output_capacity_stacks": 1
    }
  ],
  "upgrades": [
    { "capacity_stacks_add": 1, "upgrade_cost": [{ "item": "minecraft:oak_log", "amount": 20 }] }
  ],
  "nbt_levels": [
    "burg:plains/jobs/carpenter_lvl1",
    "burg:plains/jobs/carpenter_lvl2"
  ]
}
```

| Field | Required | Meaning |
|---|---|---|
| `id` | yes | Internal id; referenced by `required_buildings`, era `unlocked_building_ids`, `building_list.json` order. |
| `nbt` | yes | Resource location of the level-0 structure file under `data/<ns>/structure/`. |
| `entry_pool` | yes | Jigsaw template pool the building is injected through (e.g. `burg:jobs`, `burg:houses`, `burg:military`). |
| `icon_item` | yes | Item id rendered in the construction catalog. |
| `category` | yes | Catalog grouping: `buildings`, `jobs`, `military`, `naturals`, etc. Drives weight contribution. |
| `zone` | no | `core` / `outer` — placement preference relative to the town centre. See `house.json` (`core`) vs `carpenter.json` (`outer`). |
| `weight` | yes | Construction-weight cost; counts against the era's `currentMaxWeight`. |
| `required_buildings` | no | List of `{ defId, count }` prerequisites that must already be placed. |
| `construction_cost` | yes | List of `{ item, amount }` consumed when the build is queued. |
| `production` | no | Passive output: `{ item, amount, every_ticks, capacity_stacks }`. Job buildings usually transform instead; `house` has neither. |
| `transform_input_ratio` / `transform_every_ticks` | no | Building-level knobs for the transformation cycle. |
| `transformations` | no | List of recipes: `{ inputs: [{item, amount}], output, output_amount, output_capacity_stacks, unlock_at_level? }`. `unlock_at_level` gates a recipe behind an upgrade rung. |
| `upgrades` | no | Ordered list (rung 0 = level-1 → level-2). Each entry is a delta: any of `capacity_stacks_add`, `amount_add`, `cadence_multiplier`, `consumption_per_resident_add`, `residents_add`, `unlocks_display`, plus an `upgrade_cost` list. The next entry in `nbt_levels` is placed on upgrade. |
| `nbt_levels` | yes for upgradable buildings | Resource locations for each successive level. **Length varies per building type** — `house` has 6, `carpenter` has 7, `pig_farm` has 9. Read the array, do not assume a fixed count. |
| `residents` | no | Housing capacity. Houses and military buildings set this; job buildings do not. |
| `consumption_per_resident` | no | Food units eaten per resident per feeding. Defaults via `food_list.json`. |

`carpenter.json`, `house.json` and `barracks.json` together cover every field above and are the canonical references.

---

## eras/*.json

The era tree. 16 files ship, named `<era#>_<orientation>.json` (e.g. `0_pastoral.json`, `2_urban.json`). Loaded by `EraTransitionDataHandler` (`common/.../datapack/EraTransitionDataHandler.java:30`), which keeps three indexes: by transition id, by starter building, and by orientation.

Era 0 files (starters) and era 1+ files (transitions) have **different shapes**.

Era 0 — picks the starter and seeds the orientation:

```json
{
  "era": 0,
  "orientation": "pastoral",
  "orientation_label": "burg.orientation.pastoral",
  "structure_label": "burg.structure.settlement",
  "icon_item": "minecraft:porkchop",
  "starter_building_id": "settlement_2",
  "boosted_buildings": ["pig_field", "cow_field", "sheep_field"],
  "boost_multiplier": 1.15,
  "initial_max_weight": 20
}
```

Era 1+ — a transition from a previous era/orientation:

```json
{
  "from_era": 1,
  "from_orientation": "agricultural",
  "orientation_label": "burg.orientation.urban",
  "structure_label": "burg.structure.village",
  "icon_item": "minecraft:emerald",
  "min_weight_percent": 90,
  "weight_cap_increase": 10,
  "resource_cost": [{ "item": "minecraft:oak_log", "amount": 42 }],
  "required_residents": 7,
  "required_buildings": [{ "defId": "wild_spot", "count": 1 }],
  "unlocked_building_ids": ["merchant_shop", "lone_place", "carpenter"],
  "unlock_new_builder": true,
  "next_orientation": "urban"
}
```

| Field | Era 0 | Era 1+ | Meaning |
|---|---|---|---|
| `era` / `from_era` | `era` | `from_era` | Rung this def occupies / rung it transitions from. |
| `orientation` / `from_orientation` | both | `from_orientation` | Orientation key (`pastoral`, `agricultural`, `industrial`, then `urban`/`rural`/`ranching`/`cooking`/`forge`). |
| `orientation_label`, `structure_label` | both | both | Translation keys (i18n is wired on the port branch). |
| `icon_item` | both | both | Catalog / era-screen icon. |
| `starter_building_id` | era 0 only | — | Building id placed at worldgen. |
| `boosted_buildings`, `boost_multiplier` | era 0 only | — | Orientation production bonus. |
| `initial_max_weight` | era 0 only | — | Starting construction-weight cap. |
| `min_weight_percent` | — | era 1+ | Percentage of the current cap that must be filled before the transition is offered. |
| `weight_cap_increase` | — | era 1+ | Added to the cap on transition. |
| `resource_cost` | — | era 1+ | List of `{ item, amount }` consumed on advance. |
| `required_residents`, `required_buildings` | — | era 1+ | Prerequisites. `required_buildings` is the same `{ defId, count }` shape as buildings. |
| `unlocked_building_ids` | — | era 1+ | Building ids added to the catalog on transition. |
| `unlock_new_builder` | — | era 1+ | If true, spawn an additional NPC builder. |
| `next_orientation` | — | era 1+ | The orientation the town holds after this transition. |

The tree itself (Settlement → Village → five branches → terminal) is documented in [ARCHITECTURE](ARCHITECTURE.md#era-tree).

---

## quests/*.json

Four files ship: `new_visitor`, `stone_supplies`, `trusting_some_else`, `wood_supplies`. Loaded by `QuestDataHandler` (`common/.../datapack/QuestDataHandler.java:23`) into `QuestDef` records.

Two quest **types** ship (`type` field): `NOTE` (display-only lore, can be dismissed) and `TASK` (interactive, expires). A TASK quest carries a `conditions` array whose entries have their own `type` — `DELIVERY` is the only condition type that ships, but the schema is open. Be careful not to confuse quest `type` with condition `type`: a TASK quest has `type: "TASK"` and inside it `conditions: [{ type: "DELIVERY", ... }]`.

NOTE quest (no conditions, no reward):

```json
{
  "id": "burg:new_visitor",
  "type": "NOTE",
  "title": "quest.burg.new_visitor.title",
  "description": "quest.burg.new_visitor.desc",
  "prerequisites": {
    "min_era": 0,
    "stock_conditions": [{ "item": "minecraft:oak_log", "min": 35 }]
  }
}
```

TASK quest (with a DELIVERY condition and a reward):

```json
{
  "id": "burg:wood_supplies",
  "type": "TASK",
  "title": "quest.burg.wood_supplies.title",
  "description": "quest.burg.wood_supplies.desc",
  "refresh_interval_ticks": 7000,
  "prerequisites": {
    "min_era": 0,
    "max_era": 1,
    "required_buildings": ["grove"],
    "stock_conditions": [{ "item": "minecraft:oak_log", "min": 0, "max": 20 }]
  },
  "conditions": [
    { "type": "DELIVERY", "item": "minecraft:oak_log", "required": 12, "send_to_stock": true }
  ],
  "reward": { "type": "PLAYER", "item": "minecraft:emerald", "amount": 2 }
}
```

| Field | Required | Meaning |
|---|---|---|
| `id` | yes | Namespaced id (`burg:wood_supplies`). |
| `type` | no (defaults to `TASK`) | `NOTE` or `TASK`. |
| `title`, `description` | yes | Translation keys. |
| `refresh_interval_ticks` | TASK only; default 4500 | How long the quest stays offerable before re-rolling. Ignored for NOTE. |
| `prerequisites` | no | Object; all sub-fields optional. |
| `prerequisites.min_era`, `max_era` | no | Inclusive bounds; `max_era` defaults to `Integer.MAX_VALUE`. |
| `prerequisites.required_buildings` | no | List of building ids (strings, not `{defId,count}`). |
| `prerequisites.min_residents`, `max_residents` | no | Inclusive bounds. |
| `prerequisites.required_orientations` | no | List of orientation keys. |
| `prerequisites.stock_conditions` | no | List of `{ item, min?, max? }` — current village stock must fall in range. |
| `conditions` | TASK only | List of condition objects. `{ type: "DELIVERY", item, required, send_to_stock }`. `send_to_stock: true` routes the delivered items into village stock instead of consuming them. |
| `reward` | TASK only | `{ type: "PLAYER", item, amount }`. `PLAYER` is the only reward type that ships. |

---

## config/*.json

Three flat config files, each loaded by its own handler. None of them drives a registry; they tune behaviour.

### config/trade_prices.json

```json
{
  "prices": [
    { "item": "minecraft:oak_log", "buy": 2, "sell": 1, "quantity": 30 }
  ]
}
```

Loaded by `TradePriceDataHandler`. `buy`/`sell` are emerald prices; `quantity` is how many of the item the trade slot holds. Every item tradeable at the town hall must appear here.

### config/food_list.json

```json
{
  "feeding_schedule": [5000, 15000],
  "resident_food": [{ "item": "minecraft:apple", "fuv": 1 }],
  "herd_food":     [{ "item": "minecraft:wheat", "fuv": 1 }]
}
```

Loaded by `FoodListDataHandler`. `feeding_schedule` is the tick offsets within a day when residents and herds eat. `fuv` is "food unit value" — how many consumption units one item satisfies. `resident_food` and `herd_food` are separate lists because animals and residents eat different things.

### config/building_list.json

```json
{
  "order": ["settlement", "settlement_2", "settlement_3", "lake", "wild_crops", "wheat_field"]
}
```

Loaded by `BuildingListDataHandler`. A single array of building ids in display order — the sort order of the construction catalog. Buildings not listed here are not filtered out, but appear after the ordered set in undefined order.

---

## jobs/*.json

Two files. Both drive NPC behaviour; both are flat JSON, not registries.

### jobs/builder.json

The NPC builder's movement, build rhythm and secondary activities. Loaded by `BuilderConfigDataHandler` (`common/.../datapack/BuilderConfigDataHandler.java`). Secondary activities trigger when the construction queue is empty and the town contains the required building.

```json
{
  "walk_speed": 0.6,
  "block_reach_distance": 7.0,
  "block_delay_ticks": 4,
  "burst_pause_min_ticks": 10,
  "burst_pause_max_ticks": 18,
  "max_burst_extra_blocks": 2,
  "stuck_fallback_ticks": 100,
  "moving_timeout_ticks": 3600,
  "plan_read_chance": 0.03,
  "plan_read_min_ticks": 15,
  "plan_read_max_ticks": 35,
  "core_radius": 32,
  "secondary_activities": [
    {
      "requiredBuilding": "wild_stone",
      "heldItem": "minecraft:wooden_pickaxe",
      "animationType": "MINE",
      "target_block": "minecraft:stone"
    }
  ]
}
```

The `secondary_activities` entry shape (`requiredBuilding`, `heldItem`, `animationType`, `target_block`) is shared with `settler.json`. `animationType` is one of `MINE`, `CRAFT`, `CHOP`.

### jobs/settler.json

What a settler (a working villager) does for a living. Loaded by `SettlerJobsDataHandler`, which is newer than [ARCHITECTURE](ARCHITECTURE.md) and ships only on the port branch. Same activity-entry shape as the builder's secondary activities, plus the work-cycle knobs.

```json
{
  "work_ticks": 2400,
  "skill_per_shift": 1,
  "max_skill": 5,
  "manned_window_ticks": 6000,
  "unmanned_output": 0.5,
  "skill_bonus_per_level": 0.15,
  "jobs": [
    {
      "requiredBuilding": "lumberjack",
      "heldItem": "minecraft:stone_axe",
      "animationType": "CHOP",
      "target_block": "minecraft:oak_log"
    }
  ]
}
```

| Field | Meaning |
|---|---|
| `work_ticks` | Length of one shift. |
| `skill_per_shift`, `max_skill` | Skill progression — a settler gains one level per shift up to the cap. |
| `manned_window_ticks` | How long a workplace counts as "manned" after a finished shift. Longer than `work_ticks` on purpose so a steady settler does not flicker. |
| `unmanned_output` | Production multiplier when a workplace has a job defined but nobody worked it this cycle. `0.5` by default so existing towns do not stall the moment this ships; `0.0` is a hard gate, `1.0` makes work decorative. |
| `skill_bonus_per_level` | Per-level output bonus. At `max_skill` 5 with `0.15`, a master produces 1.75×. |
| `jobs` | Activity list. `target_block` is searched **only inside the building it belongs to**, so a common block like `oak_log` is safe and does not turn every wall in town into a workplace. |

---

## Related

- [ARCHITECTURE](ARCHITECTURE.md) — the system map; the datapack layer is described there at a higher level
- [PORT-STATUS](PORT-STATUS.md) — port branch state; notes which handlers are new since [ARCHITECTURE](ARCHITECTURE.md) was written
- Handler sources — `common/src/main/java/org/lowern1ght/burg/datapack/` (eight files: `BuildingDataHandler`, `EraTransitionDataHandler`, `QuestDataHandler`, `BuilderConfigDataHandler`, `SettlerJobsDataHandler`, `BuildingListDataHandler`, `FoodListDataHandler`, `TradePriceDataHandler`)
- Reload wiring — `neoforge/src/main/java/org/lowern1ght/burg/OuatForge.java:259-268`
- Datapack content — `common/src/main/resources/data/burg/{buildings,eras,quests,config,jobs}/`
