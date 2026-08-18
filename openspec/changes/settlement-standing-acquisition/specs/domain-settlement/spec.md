# Delta spec — `settlement-standing-acquisition`

## MODIFIED Requirements

### Requirement: settlement domain is Minecraft-free

The Settlement bounded context's domain layer (`domain/settlement/` and
`domain/shared/`) MUST contain no `net.minecraft` or NeoForge types. The
domain types added by this change (`CitizenId`, `Acquisition`,
`Standing`, `StandingBook`) use `java.util.UUID` and `java.lang.String`
only; conversion to and from Minecraft `UUID` happens at the `Town`
facade edge.

Rationale: the domain becomes testable on a bare JVM — the first fast
feedback loop in a project whose verification bar is in-game
(ADR-0008 §"Layers inside each context").

#### Scenario: bare-JVM domain test
- **WHEN** a JUnit test instantiates any `domain/settlement/` or
  `domain/shared/` class and runs without Minecraft classes on the
  classpath
- **THEN** the test compiles and passes — a `net.minecraft` import inside
  `domain/settlement/` or `domain/shared/` is a build failure, not a
  review finding.

#### Scenario: BlockPos stays at the edge
- **WHEN** infrastructure code needs a coordinate for a domain call
- **THEN** it converts `BlockPos` to `BlockCoord` before the call; the
  domain signature accepts only `BlockCoord`.

#### Scenario: UUID stays at the edge
- **WHEN** a domain call needs a citizen identity
- **THEN** it accepts a `CitizenId` (the domain value object); the
  Minecraft `UUID` is wrapped at the `Town` facade edge and never
  appears in domain signatures.

### Requirement: Town stays the aggregate root of Settlement

All mutations of settlement state MUST enter through the Town aggregate
root (`domain/settlement/` via the `town/Town.java` facade).
Carved-out members (Production, ConstructionQueue, Standing, QuestLog —
future changes) are entities or value objects owned by the root; no
external caller mutates them directly. Standing and Acquisition land
here as the first carve — both enter through `Town` only.

#### Scenario: carve keeps one entry point
- **WHEN** the Standing carve lands and a caller wants to adjust a
  citizen's score in a town
- **THEN** the call goes through `Town.adjustStanding(UUID, int)` (or
  its domain-friendly sibling that accepts `CitizenId`); the
  `StandingBook` is never handed out by reference and never mutated
  outside `Town`.

### Requirement: save format survives the strangler

For as long as the strangler migration runs, the world-save contract
MUST NOT change: the `ouat_towns` SavedData keeps the NBT shape
`Town.toNbt()`/`fromNbt()` produce today. A world saved by a build
without this architecture MUST load — behaviorally unchanged — in a
build with it, and vice versa. The Standing and Acquisition carve
demonstrates this: missing `Acquisition` / `Standings` NBT keys read as
`FREE` / empty book on load.

#### Scenario: old world loads after the standing carve
- **WHEN** a world last saved before this commit is opened by a
  post-carve build
- **THEN** the town loads with `getAcquisition() == FREE` and
  `getStandingBook().isEmpty() == true`, and every pre-existing field
  (buildings, queue, stock, era, chatSubscribers) is intact — verified
  by unit round-trip in `StandingBookTest` (the additive-default path)
  and by visual review of `Town.fromNbt` (no `chatSubscribers` branch
  touched).

## ADDED Requirements

### Requirement: standing value object

Every town carries a `StandingBook` — an immutable roll of per-citizen
scores — accessible via `Town.getStandingBook()` and mutated only
through `Town.adjustStanding(UUID, int)` or `Town.setStanding(UUID, int)`
when the latter is added in a later carve. A citizen not on the roll
reads as zero. Citizens whose score is set to zero are dropped from the
roll so the persisted NBT stays sparse.

#### Scenario: a fresh town has no standing roll
- **WHEN** a brand-new `Town()` is constructed
- **THEN** `getStandingBook().isEmpty() == true` and
  `standingFor(<any citizen>).value() == 0`.

#### Scenario: adjust accumulates
- **WHEN** `adjustStanding(alice, +5)` is called twice on a town with no
  prior standing for Alice
- **THEN** `standingFor(alice).value() == 10`.

#### Scenario: zero drops from the roll
- **WHEN** a citizen's score is set to 0 (either by `setStanding` or by
  `adjustStanding` with a negative delta that lands on zero)
- **THEN** the citizen is removed from `getStandingBook().entries()`
  (size drops by one) and `standingFor(citizen).value() == 0`.

### Requirement: acquisition lifecycle

Every town carries an `Acquisition` — one of `FREE`, `ELEVATED`,
`FOUNDED`, `CAPTURED`. The acquisition is a four-step ladder; ordinal
order is the progression. The additive NBT default is `FREE`, which is
what any pre-carve world reads as on load.

#### Scenario: a fresh town is FREE
- **WHEN** a brand-new `Town()` is constructed
- **THEN** `getAcquisition() == FREE`.

#### Scenario: an old world loads FREE
- **WHEN** a world last saved before this commit is opened by a
  post-carve build
- **THEN** `getAcquisition() == FREE` — no migration needed.

#### Scenario: unknown NBT defaults to FREE
- **WHEN** the `Acquisition` NBT key holds a string that is not one of
  the four named values (e.g. a value introduced by a future build)
- **THEN** `getAcquisition()` returns `FREE` rather than throwing; the
  forward-compat sentinel keeps worlds loadable across version skew.