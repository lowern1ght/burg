# specs/village-autonomy/spec.md

## Purpose

Capsulate **Pillar 1** — *"Villages are autonomous"* — into a testable capability. A village grows whether or not the player engages; the player is one input among several, not the central actor. The pillar is **eternal**: no progression act lifts it.

Source: P1 (PHILOSOPHY.md §"The five pillars" → Pillar 1).

---

## Requirements

### Requirement: autonomous growth

The town state machine (`Town`, `TickScheduler`, `ProductionManager`, `FoodManager`, `EraManager`) MUST advance its world on server ticks **without any player input**. Nothing in the mod's growth loop is gated on the player's presence, inventory, or position.

#### Scenario: idle player for seven in-game days
- **WHEN** a player joins a world, finds a village, then leaves the session and the world runs for seven in-game days unobserved
- **THEN** at least one of the following has changed without player input: production output increased, era advanced, a new NPC arrived, a quest appeared, the town stock changed, food was consumed and replenished, an industry zone was extended.

#### Scenario: village on a server with no online players
- **WHEN** a dedicated server runs the mod with zero players online for 24 real-time hours
- **THEN** on next login, the town is still alive: stock exists, NPCs are still scheduled, no entity is despawned, no production state is reset.

### Requirement: player is one input, not the center

No system path treats the player as a necessary participant. The player MAY accelerate, supply, or choose between options the town has already decided on; the player MAY NOT be the sole trigger for a town's growth event.

#### Scenario: construction without a player
- **WHEN** the stock contains the materials listed in a building's `construction_cost` and the building is in the construction queue
- **THEN** the NPC builder constructs it on its own schedule — the player's "click build" button MAY exist for convenience but is not the only path to construction.

#### Scenario: era advancement without a player
- **WHEN** all prerequisites of a downstream era are satisfied (per `EraTransitionDataHandler`) and the town's current era is below the prerequisite era in the era tree
- **THEN** the era advances on its own; `EraManager.tick` is allowed to be a no-op only if the underlying deterministic check is gated on something other than player presence.

### Requirement: anti-coupling — no "player required" features

A feature whose design requires the player to be present to function is **wrong** by this spec. Proposed changes that fail this test MUST be redesigned or rejected at proposal-time, not after implementation.

#### Scenario: rejected feature shape
- **WHEN** a PR introduces a new progression item whose only path to acquisition is a player-driven interaction
- **THEN** the PR is rejected; the proposer is asked to provide either (a) a non-player path or (b) a reclassification that drops the item from "progression" to "player convenience".

---

## Cross-references

- PHILOSOPHY.md §"Villages are autonomous"
- ROADMAP.md §"Cross-cutting, and where it sits" — every row is read through this spec
- STATUS.md — rows are `build-green` until a recorded in-world walk demonstrates autonomy
