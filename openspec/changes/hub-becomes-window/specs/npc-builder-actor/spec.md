# Delta spec — `hub-becomes-window`

## MODIFIED Requirements

### Requirement: builder stays alive on its own — builder consumes player-supplied items in SUPPLY mode

**MODIFIED** (adds a SUPPLY-mode scenario to `specs/npc-builder-actor/spec.md`
requirement §"builder stays alive on its own")

**Previous text:** the NPC builder's pacing (sleep, work, morale) is
player-independent. This requirement is preserved as-is. What the hub-mode
change adds is a single new scenario that exercises the builder's reaction
to a SUPPLY-mode supply tick.

**New scenario addition:**

#### Scenario: builder consumes a player-supplied item in SUPPLY mode
- **WHEN** a town is in SUPPLY mode and a player supplies an item the town's
  intent list flagged as needed
- **THEN** the item is consumed by the town (added to stock via the existing
  stock-deposit path) and the NPC builder continues on its own schedule —
  no player "go build" command was required, and the builder still pauses
  for dusk / sleep / morale. Pillar 4 (NPC builder is the actor) is
  preserved.
