# specs/npc-builder-actor/spec.md

## Purpose

Encode **Pillar 4** — *"NPC builder is the actor"* — into a testable capability. The NPC builder is the only entity that places blocks in villages. Player placement is forbidden, always, in every act. The pillar is **eternal**.

Source: P4 (PHILOSOPHY.md §"Pillar 4").

---

## Requirements

### Requirement: NPC builder, not player, places every block

Within a registered Town's plot (the area the anchor owns, including its growth radius), the ONLY actor that places or removes blocks is the NPC builder. The player MAY break blocks (a vanilla verb, unchanged); the player MAY NOT place blocks within a town's plot.

#### Scenario: player attempts to place a block inside the town plot
- **WHEN** a player in survival places a block at coordinates inside the registered town plot
- **THEN** the placement is rejected at the event level; no block is added to the world; a chat or hub message explains the rule.

#### Scenario: NPC builder opens gates and doors it built
- **WHEN** an NPC builder constructs a building that includes a fence gate or door
- **THEN** during construction the NPC opens the gate / door to step through, then closes it behind; the placement is logged in `tools/check_pens.py` as a normal open/close cycle.

### Requirement: the only sanctioned player-placed block

The Town Anchor itself is the ONE sanctioned exception: the player places the anchor at the village's meeting point to register the town. This is the founding act. After registration, the anchor's possession is the Town's, and the player MAY NOT move or break it without explicit debug-mode or staff privilege (to prevent stealth town-deletion).

#### Scenario: founding a vanilla village
- **WHEN** the player places a Town Anchor at the meeting point of a vanilla village that has not yet been registered
- **THEN** the founding flow runs once: the anchor binds, `Citizens.enlistAllNear` enlists the existing villagers, and from this point the player cannot place blocks within the plot.

#### Scenario: trying to break the anchor in survival
- **WHEN** a player attempts to break a registered Town Anchor in survival mode
- **THEN** the anchor is unbreakable without operator privilege; this prevents the player from undoing his own registration to grief the town.

### Requirement: NPC builder roles are extensions, not new classes

Future roles (farmer, miner, forester, …, planned under issue #5) extend the existing `Npc` class with new `Role` enum values and new goals. They never become new entity classes.

#### Scenario: adding a new farmer role
- **WHEN** a new role `FARMER` is added to the `Role` enum with new goal behavior
- **THEN** the existing `Npc` renderer, network packet set (17 packets, `CustomPacketPayload`), town-state queries, and datapack content loaders are all reusable; only goal-selection logic and role-specific inventory are added.

### Requirement: builder stays alive on its own

The builder is itself subject to Pillar 1 — it has a schedule, sleeps, eats, gets tired, and has morale. The builder does not pause and wait for player input; it paces itself.

#### Scenario: builder schedule
- **WHEN** server time reaches `dusk` for the town
- **THEN** the builder suspends construction, walks to its bed, and sleeps; when `dawn` arrives it resumes; an unscheduled player interaction does not change this schedule.

---

## Cross-references

- PHILOSOPHY.md §"Pillar 4"
- ROADMAP.md §"Three rulings" — R3 *"player only trades and supplies"*
- VISION.md §"pillar 4 is eternal" — the act-4/5 lifting does NOT extend to block placement
- ARCHITECTURE.md §"ai"
