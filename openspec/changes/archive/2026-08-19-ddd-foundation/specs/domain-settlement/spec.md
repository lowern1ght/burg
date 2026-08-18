# Delta spec — `ddd-foundation`

## ADDED Requirements

### Requirement: settlement domain is Minecraft-free

The Settlement bounded context's domain layer (`domain/settlement/`)
MUST contain no `net.minecraft` or NeoForge types. Minecraft-native
concepts cross the boundary only as value-object wrappers
(`TownId`, `BlockCoord`, `CitizenId`, `ItemId`), converted at the
infrastructure edge.

Rationale: the domain becomes testable on a bare JVM — the first fast
feedback loop in a project whose verification bar is in-game
(ADR-0008 §"Layers inside each context").

#### Scenario: bare-JVM domain test
- **WHEN** a JUnit test instantiates any `domain/settlement/` class and
  runs without Minecraft classes on the classpath
- **THEN** the test compiles and passes — a `net.minecraft` import inside
  `domain/settlement/` is a build failure, not a review finding.

#### Scenario: BlockPos stays at the edge
- **WHEN** infrastructure code needs a coordinate for a domain call
- **THEN** it converts `BlockPos` to `BlockCoord` before the call; the
  domain signature accepts only `BlockCoord`.

### Requirement: Town stays the aggregate root of Settlement

All mutations of settlement state MUST enter through the Town aggregate
root (`domain/settlement/`). Carved-out members (Production,
ConstructionQueue, Standing, QuestLog — future changes) are entities or
value objects owned by the root; no external caller mutates them
directly.

#### Scenario: carve keeps one entry point
- **WHEN** the Production carve lands and a caller wants to add output
  to a town's stock
- **THEN** the call goes through the Town root (e.g. a
  `town.recordProduction(...)` intent method), not through a
  separately-held `Production` reference.

### Requirement: save format survives the strangler

For as long as the strangler migration runs, the world-save contract
MUST NOT change: the `ouat_towns` SavedData keeps the NBT shape
`Town.toNbt()`/`fromNbt()` produce today. A world saved by a build
without this architecture MUST load — behaviorally unchanged — in a
build with it, and vice versa.

#### Scenario: old world loads after a carve
- **WHEN** a world last saved before a strangler carve (e.g. Production
  moving out of `Town`) is opened by a post-carve build
- **THEN** the town loads with all buildings, queue entries, stock, era
  and standing intact — verified in a running game, not only in a unit
  round-trip.
