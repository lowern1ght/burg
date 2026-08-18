# specs/domain-settlement/spec.md

## Purpose

Capsulate the **Settlement bounded context's domain carve** (ADR-0008) —
the strangler migration that moves standing, stock, construction queue
and quest state behind Minecraft-free domain types while `town/Town.java`
remains the aggregate-root facade and the `ouat_towns` NBT contract stays
byte-for-byte stable.

This spec is the consolidated main spec for the capability. It was
hand-merged (2026-08-19) from the change deltas that carved the context —
`ddd-foundation`, `settlement-standing-acquisition`,
`settlement-stock-ledger` (all still in flight and carrying their own
deltas against these requirement names) — plus the archived
`settlement-construction-queue` and `settlement-quest-log` views
(ADR-0011, ADR-0012) and the `settlement-stock-promote` dual-write
(ADR-0013). Requirement headers below match the delta headers exactly so
future `openspec archive` runs merge into this file.

Source: P1 (villages are autonomous — the town owns its state, the player
is one input), P4 (NPC builder is the actor — the domain models the town,
not the player's screen).

---

## Requirements

### Requirement: settlement domain is Minecraft-free

The Settlement bounded context's domain layer (`domain/settlement/` and
`domain/shared/`) MUST contain no `net.minecraft` or NeoForge types.
Minecraft-native concepts cross the boundary only as value-object
wrappers (`TownId`, `BlockCoord`, `CitizenId`, `ItemId`), converted at
the infrastructure edge — today the `Town` facade edge.

Rationale: the domain becomes testable on a bare JVM — the first fast
feedback loop in a project whose verification bar is in-game
(ADR-0008 §"Layers inside each context"). `DomainPurityTest` turns this
into a build failure, not a review finding.

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

#### Scenario: ResourceLocation stays at the edge
- **WHEN** infrastructure code needs an item identity for a domain call
- **THEN** it converts the Minecraft `ResourceLocation` (or the `Item`
  it points at) to `ItemId` before the call; the domain signature
  accepts only `ItemId`.

#### Scenario: UUID stays at the edge
- **WHEN** a domain call needs a citizen identity
- **THEN** it accepts a `CitizenId` (the domain value object); the
  Minecraft `UUID` is wrapped at the `Town` facade edge and never
  appears in domain signatures.

### Requirement: Town stays the aggregate root of Settlement

All mutations of settlement state MUST enter through the Town aggregate
root (`domain/settlement/` via the `town/Town.java` facade). Carved-out
members (Standing, Acquisition, StockLedger, ConstructionQueue, QuestLog)
are entities or value objects owned by the root; no external caller
mutates them directly.

#### Scenario: carve keeps one entry point
- **WHEN** a carve lands and a caller wants to mutate or read a town's
  standing, stock, queue or quest state
- **THEN** the call goes through the `Town` facade (e.g.
  `town.adjustStanding(...)`, `town.addStock(...)`,
  `town.stockLedger().get(...)`, `town.constructionQueueView()`,
  `town.questLog()`); the carved domain object is never handed out for
  write and never mutated outside `Town`.

### Requirement: save format survives the strangler

For as long as the strangler migration runs, the world-save contract
MUST NOT change: the `ouat_towns` SavedData keeps the NBT shape
`Town.toNbt()`/`fromNbt()` produce today — `Standings`, `Acquisition`,
`ReserveStock` (per-item keys `BuiltInRegistries.ITEM.getKey(item)
.toString()`), `ConstructionQueue` + `QueueReservedStock`, `ActiveQuests`
+ `QuestDefLastCompleted`. A world saved by a build without this
architecture MUST load — behaviorally unchanged — in a build with it,
and vice versa.

#### Scenario: old world loads after a carve
- **WHEN** a world last saved before a strangler carve (standing,
  stock-ledger, queue view, quest view, dual-write) is opened by a
  post-carve build
- **THEN** the town loads with all buildings, queue entries, stock, era,
  standing and acquisition intact — missing additive keys read as their
  defaults (`FREE` acquisition, empty `StandingBook`, `StockLedger.EMPTY`
  reserve), verified in a running game, not only in a unit round-trip.

### Requirement: item identity wrapper

The domain layer MUST identify an item by `ItemId` — a record wrapping
the canonical `namespace:path` string the Minecraft registry already
uses. Two callers using different cases (e.g. `Minecraft:Stone` vs
`minecraft:stone`) produce the same `ItemId`, so the hash-map key is
stable across call sites.

#### Scenario: the canonical form is lowercase

- **WHEN** `ItemId.of("Minecraft:Stone")` is called
- **THEN** `value()` returns `"minecraft:stone"` and
  `equals(ItemId.of("minecraft:stone"))` holds.

#### Scenario: of() rejects malformed input

- **WHEN** `ItemId.of("not a valid:one")` is called
- **THEN** it throws `IllegalArgumentException`; the boundary caller
  is responsible for catching its own garbage.

#### Scenario: parseOrEmpty is lenient

- **WHEN** `ItemId.parseOrEmpty(null)` (or `""`, or `"stone"` without
  a colon, or `"minecraft:"` with an empty path) is called
- **THEN** it returns `ItemId.EMPTY` rather than throwing — the
  additive NBT load path must be able to wrap whatever string was
  persisted.

### Requirement: stock ledger value object

Every town MUST carry a `StockLedger` — an immutable roll of per-item
quantities over `ItemId`. Since the stock-promote carve (ADR-0013) the
ledger is a **cached view kept in dual-write sync**: `Town` holds a
`StockLedger` field rebuilt at every known `reserveStock` mutation site
(`addStock`, the queue's refund cycle, the additive NBT load, every
`TownInventory` mutation via its callback), `stockLedger()` serves the
cache on the fast path and falls back to a full rebuild when the cache
and `reserveStock` disagree, and `applyStockLedger(StockLedger)` writes
a domain-built ledger back into the reserve (skipping unknown item ids).
`reserveStock` remains the NBT source of truth; the ledger is not yet
the sole SoT.

#### Scenario: a fresh town has an empty ledger

- **WHEN** a brand-new `Town()` is constructed
- **THEN** `stockLedger().isEmpty() == true` and
  `stockLedger().get(<any ItemId>) == 0`.

#### Scenario: an old world loads with an empty ledger

- **WHEN** a world last saved before the stock carve is opened by a
  post-carve build
- **THEN** `stockLedger().isEmpty() == true` — no migration needed.

#### Scenario: the ledger reflects reserveStock on read

- **WHEN** `Town.addStock(<some Item>, 10)` is called and then
  `town.stockLedger()` is called
- **THEN** the ledger's `get(ItemId.of("namespace:path"))` returns
  `10` for the corresponding item, and the `EMPTY`-sentinel for any
  other item — served from the dual-write cache, not a fresh rebuild.

#### Scenario: take fails fast on insufficient stock

- **WHEN** `StockLedger.take(ItemId.of("minecraft:stone"), 5)` is
  called on a ledger whose running quantity for stone is `3`
- **THEN** it throws `IllegalStateException` rather than silently
  underflowing; the original ledger is unchanged.

#### Scenario: zero-quantity entries drop at the edge

- **WHEN** `StockLedger.add(item, qty).take(item, qty)` drains a
  quantity back to zero
- **THEN** the resulting ledger is the `StockLedger.EMPTY` sentinel
  (referentially stable) and `entries()` is empty — the persisted
  form stays sparse.

### Requirement: standing value object

Every town MUST carry a `StandingBook` — an immutable roll of per-citizen
scores — accessible via `Town.getStandingBook()` and mutated only
through `Town.adjustStanding(UUID, int)` (a `setStanding` sibling may
arrive in a later carve). A citizen not on the roll reads as zero.
Citizens whose score lands back on zero are dropped from the roll so
the persisted NBT stays sparse.

#### Scenario: a fresh town has no standing roll
- **WHEN** a brand-new `Town()` is constructed
- **THEN** `getStandingBook().isEmpty() == true` and
  `standingFor(<any citizen>).value() == 0`.

#### Scenario: adjust accumulates
- **WHEN** `adjustStanding(alice, +5)` is called twice on a town with no
  prior standing for Alice
- **THEN** `standingFor(alice).value() == 10`.

#### Scenario: zero drops from the roll
- **WHEN** a citizen's score lands on 0 (by `adjustStanding` with a
  negative delta that lands on zero, or by a future `setStanding`)
- **THEN** the citizen is removed from `getStandingBook().entries()`
  (size drops by one) and `standingFor(citizen).value() == 0`.

### Requirement: acquisition lifecycle

Every town MUST carry an `Acquisition` — one of `FREE`, `ELEVATED`,
`FOUNDED`, `CAPTURED`. The acquisition is a four-step ladder; ordinal
order is the progression. The additive NBT default is `FREE`, which is
what any pre-carve world reads as on load.

#### Scenario: a fresh town is FREE
- **WHEN** a brand-new `Town()` is constructed
- **THEN** `getAcquisition() == FREE`.

#### Scenario: an old world loads FREE
- **WHEN** a world last saved before the standing carve is opened by a
  post-carve build
- **THEN** `getAcquisition() == FREE` — no migration needed.

#### Scenario: unknown NBT defaults to FREE
- **WHEN** the `Acquisition` NBT key holds a string that is not one of
  the four named values (e.g. a value introduced by a future build)
- **THEN** `getAcquisition()` returns `FREE` rather than throwing; the
  forward-compat sentinel keeps worlds loadable across version skew.

### Requirement: construction queue domain view

The player's construction queue MUST be exposed as Minecraft-free domain
types — `ConstructionIntent` (sealed `NewBuild` / `Upgrade`, world
position as the `Long.toString(BlockPos.asLong())` string) and
`ConstructionQueue` (immutable ordered list with the `EMPTY` sentinel,
capacity, enqueue/dequeue). The legacy `Town.constructionQueue` field
and the `ConstructionQueue` + `QueueReservedStock` NBT keys remain the
source of truth; `Town.constructionQueueView()` rebuilds the domain view
on read. No change to `BuildExecutor`, queue claims, or NBT shape
(ADR-0011).

#### Scenario: the view mirrors the legacy queue
- **WHEN** `Town.tryAddToConstructionQueue(defId)` accepts an entry and
  `town.constructionQueueView()` is called
- **THEN** the immutable view reports the same ordered entries and
  remaining capacity as the legacy queue — reading the view never
  mutates the queue.

#### Scenario: the view is a rebuild, not a second SoT
- **WHEN** the queue drains (a build completes) and
  `constructionQueueView()` is called again
- **THEN** the new view reflects the drained state — there is no
  queue copy to fall out of sync, and the NBT keys are unchanged.

### Requirement: quest log domain view

Active quests and the last-completed map MUST be exposed as Minecraft-free
domain types — `QuestRef` (defId, type `NOTE|TASK`, optional status) and
`QuestLog` (immutable refs plus last-completed ticks). The legacy
`Town.activeQuests` / `questDefLastCompleted` fields and the
`ActiveQuests` / `QuestDefLastCompleted` NBT keys stay the source of
truth; `Town.questLog()` is a read-only rebuild. No `QuestManager`
rewrite, no datapack schema change (ADR-0012).

#### Scenario: the view mirrors active quests
- **WHEN** a quest spawns (or completes) and `town.questLog()` is
  called
- **THEN** the immutable view reports the same active refs and
  last-completed ticks as the legacy fields — reading the view never
  mutates quest state.

#### Scenario: quest state survives the view carve
- **WHEN** a world last saved before the quest-log carve is opened by a
  post-carve build
- **THEN** active quests and completion history load intact — the view
  added no persistence of its own.

---

## Change deltas still in flight

These changes carry `specs/domain-settlement/` deltas against the
requirement names above and will merge here via `openspec archive`
when their tasks complete:

- `ddd-foundation` (9/10) — the three base requirements.
- `settlement-standing-acquisition` (13/17) — standing + acquisition.
- `settlement-stock-ledger` (9/14) — `ItemId` + `StockLedger`.

Archived into this file's history (2026-08-19, skip_specs — no delta of
their own): `settlement-application-services` (ADR-0014, ports + use
cases documented in ADR-0014/ADR-0018), `settlement-construction-queue`
(ADR-0011), `settlement-quest-log` (ADR-0012), `settlement-stock-promote`
(ADR-0013).

## Cross-references

- ADR-0008 (ddd-foundation), ADR-0009 (standing-acquisition),
  ADR-0010 (stock-ledger), ADR-0011 (construction queue),
  ADR-0012 (quest log), ADR-0013 (stock promote),
  ADR-0014 (settlement application), ADR-0018 (application wiring note).
- [`OPENSPEC-ARCHIVE-LOG.md`](../../docs/07-state/OPENSPEC-ARCHIVE-LOG.md)
  — what was archived vs deferred and why.
- STATUS.md — the carve rows stay `build-green` until a recorded
  in-world walk; a green bare-JVM test suite is not evidence.
