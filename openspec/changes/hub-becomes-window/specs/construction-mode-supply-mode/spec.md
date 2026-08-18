# Delta spec — `hub-becomes-window`

## ADDED Requirements

### Requirement: hub has two modes

Every town has a `HubMode { CONSTRUCTION, SUPPLY }` derived from
`(standing, structural_predicate)` against the per-town transition thresholds
shipped in the `BuilderConfigDataHandler` JSON. The mode MUST be derived, not
stored separately — a server with this change and an old world has no
migration: the predicate recomputes on next read.

#### Scenario: act-3 town stays in CONSTRUCTION mode
- **WHEN** a town has standing < 50 (or whatever
  `hub.transition_standing_threshold` is set to) and the structural predicate
  is not yet met
- **THEN** `Town#hubMode` returns `CONSTRUCTION`; the right-click on the Town
  Anchor opens the command-console hub (current behavior).

#### Scenario: act-4 town in SUPPLY mode
- **WHEN** a town crosses both thresholds and the player reopens the hub
- **THEN** `Town#hubMode` returns `SUPPLY`; the right-click opens a
  read-only intent list and the player's input is supply, not orders.

### Requirement: structural predicate is three conditions AND-ed

`hub.transition_structure_required` MUST be a non-empty subset of
`{core_populated, industry_zoned, road_laid}`. For a town to be eligible for
SUPPLY mode, every condition in the subset MUST be true at the moment of
query. The conditions are derived from existing state and do not require new
state:

- `core_populated` — every footprint inside the core radius is occupied.
- `industry_zoned` — the zoning layer has at least one industry cell outside
  the core radius.
- `road_laid` — the road planner has at least one path from the core to the
  industry zone.

#### Scenario: only one condition met
- **WHEN** a town has `core_populated = true` but `industry_zoned = false`
- **THEN** the structural predicate returns false; `hubMode` is
  `CONSTRUCTION`.

### Requirement: hub UI respects mode

`TownHubScreen` MUST read `Town#hubMode` once at open and present the
matching widget set. The CONSTRUCTION set is today's hub, untouched. The
SUPPLY set MUST show:

- a read-only list of "what the town intends to build next",
- a list of items the town is short of (a derived stock-gap view), and
- a single "supply" input field that accepts any item a player holds.

It MUST NOT show a construction queue widget, a "demolish" widget, or an NPC
assignment widget — those are CONSTRUCTION-mode actions.

#### Scenario: SUPPLY-mode hub shows the intent list only
- **WHEN** a player opens the hub in SUPPLY mode
- **THEN** the screen shows exactly the three widgets above; no other
  interactive widgets are present; pressing `E` exits the screen.

#### Scenario: a SUPPLY-mode widget is replayed
- **WHEN** a player in SUPPLY mode supplies oak logs while the town's next
  intent is `house_lvl1` (cost includes oak logs)
- **THEN** the NPC builder's queue advances by one `house_lvl1` step on the
  next builder tick (no separate command was required).

