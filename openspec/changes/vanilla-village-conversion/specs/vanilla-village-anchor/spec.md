# Delta spec — `vanilla-village-conversion`

## ADDED Requirements

### Requirement: anchor binds a vanilla village on placement

The Town Anchor MUST, when placed at the meeting point of an unregistered
vanilla village (vanilla `Village` POI detection), enter a binding state
and complete the conversion flow described below. When placed elsewhere,
the anchor MUST fall through to today's hub-open behavior. The two paths
are disjoint: binding is a one-shot, the hub is repeatable.

#### Scenario: anchor on a vanilla village meeting point
- **WHEN** a player in survival places a Town Anchor at the meeting point
  of a plains vanilla village that has not yet been registered
- **THEN** the anchor binds within the same tick; a chat message confirms
  the binding; `Citizens.enlistAllNear` enlists the existing villagers;
  existing houses are reserved as occupied footprints; the bridgehead
  street piece is placed at the nearest village edge with flat ground; the
  anchor becomes unbreakable (per `specs/npc-builder-actor`).

#### Scenario: anchor on a non-village location
- **WHEN** a player places a Town Anchor on a plains biome but not within
  a vanilla village's POI radius
- **THEN** the anchor behaves as today (opens the hub if the player has
  standing; tells the stranger he is not of it otherwise). No bridgehead
  is placed.

### Requirement: villagers are enlisted, not re-spawned

`Citizens.enlistAllNear` MUST be called exactly once during binding, on
the existing vanilla villagers. Spawning new burg_NPC entities into the
village to "fill" it MUST NOT happen; the conversion preserves vanilla
villagers and re-skins them via `Role`.

#### Scenario: a 6-villager plains village is bound
- **WHEN** a 6-villager plains village is bound via anchor placement
- **THEN** the resulting town has 6 enlisted villagers, no new entity
  classes were created, and no villager profession was changed by Burg
  (vanilla profession is preserved; burg adds a `Role` layer on top via
  the existing `Npc` class extension).

### Requirement: existing houses are reserved, not overwritten

During binding, every house footprint inside the village's vanilla POI
MUST be registered as a reserved footprint. The growth system MUST NOT
place a new Burg building in the same footprint as a reserved house, ever.

#### Scenario: a Burg `house_lvl1` would land on a vanilla house
- **WHEN** the NPC builder selects `house_lvl1` and the only legal
  placement footprint is over an existing vanilla house
- **THEN** the placement is skipped, the builder logs the skip, and on
  the next tick the builder picks an alternative footprint.

### Requirement: bridgehead is one NBT with two connection points

The bridgehead street piece MUST be a single vanilla-format NBT
(`plains/bridgehead.nbt`) carrying exactly two outward-facing
`ConnectionPoint`s on opposite faces. Pieces must pass
`tools/check_integrity.py` and `tools/selfgate.py` (5 primitives +
drawn section). Once green, the file is read-only calibration territory
(FORK_NOTICE + CLAUDE.md).

#### Scenario: bridgehead passes integrity
- **WHEN** `python tools/check_integrity.py plains/bridgehead.nbt` is run
- **THEN** every primitive (walls / roof / door / float / room) is green.

#### Scenario: bridgehead passes selfgate
- **WHEN** `python tools/selfgate.py` is run on `plains/bridgehead.nbt`
- **THEN** exit 0; the drawn-section output is referenced by SHA in the
  commit message.

### Requirement: the player never fights a war with his own sword

(Reinforces Pillar 2 / R3 — re-listed here so a future contributor
reading only this change sees the rule.)

#### Scenario: stranger sees an empty hub after binding
- **WHEN** a player with standing = 0 binds a vanilla village and opens
  the hub
- **THEN** the hub either refuses (stranger-blocked) or opens in read-only
  intent mode; it NEVER opens a "go build X" command console for a
  stranger.
