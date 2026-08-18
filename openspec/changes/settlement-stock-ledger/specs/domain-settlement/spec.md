# Delta spec — `settlement-stock-ledger`

## MODIFIED Requirements

### Requirement: settlement domain is Minecraft-free

The Settlement bounded context's domain layer (`domain/settlement/` and
`domain/shared/`) MUST contain no `net.minecraft` or NeoForge types. The
domain types added by this change (`ItemId`, `StockLedger`) use
`java.lang.String` only; conversion to and from Minecraft
`ResourceLocation` and `Item` happens at the `Town` facade edge.

Rationale: the domain becomes testable on a bare JVM — the first fast
feedback loop in a project whose verification bar is in-game
(ADR-0008 §"Layers inside each context").

#### Scenario: bare-JVM domain test

- **WHEN** a JUnit test instantiates any `domain/settlement/` or
  `domain/shared/` class and runs without Minecraft classes on the
  classpath
- **THEN** the test compiles and passes — a `net.minecraft` import
  inside `domain/settlement/` or `domain/shared/` is a build failure,
  not a review finding.

#### Scenario: ResourceLocation stays at the edge

- **WHEN** infrastructure code needs an item identity for a domain call
- **THEN** it converts the Minecraft `ResourceLocation` (or the `Item`
  it points at) to `ItemId` before the call; the domain signature
  accepts only `ItemId`.

### Requirement: Town stays the aggregate root of Settlement

All mutations of settlement state MUST enter through the Town aggregate
root (`domain/settlement/` via the `town/Town.java` facade). Carved-out
members (StockLedger, Standing, QuestLog — future changes) are entities
or value objects owned by the root; no external caller mutates them
directly. The StockLedger carve lands here as the second carve — its
domain object is read-only today, and every write still goes through
the legacy `reserveStock` field on the root.

#### Scenario: carve keeps one entry point

- **WHEN** the StockLedger carve lands and a caller wants to ask "does
  the town hold N oak logs?"
- **THEN** the call goes through `town.stockLedger().get(ItemId.of(...))`;
  the `StockLedger` is never handed out for write and never mutated
  outside `Town`.

### Requirement: save format survives the strangler

For as long as the strangler migration runs, the world-save contract
MUST NOT change: the `ouat_towns` SavedData keeps the NBT shape
`Town.toNbt()`/`fromNbt()` produce today. A world saved by a build
without this architecture MUST load — behaviorally unchanged — in a
build with it, and vice versa. The StockLedger carve demonstrates this
a second time: the `ReserveStock` compound tag and its per-item keys
(`BuiltInRegistries.ITEM.getKey(item).toString()`) are unchanged, and a
pre-carve world with an empty reserve reads as `StockLedger.EMPTY`.

#### Scenario: old world loads after the stock-ledger carve

- **WHEN** a world last saved before this commit is opened by a
  post-carve build
- **THEN** the town loads with `stockLedger().isEmpty() == true` (the
  additive-default path), and every pre-existing field (buildings,
  queue, era, chatSubscribers, Standings, Acquisition) is intact —
  verified by `StockLedgerTest` (`emptyIsTheDefault`,
  `mergeCancelDrops`) and by visual review of `Town.fromNbt` (no
  `ReserveStock` branch touched).

## ADDED Requirements

### Requirement: item identity wrapper

The domain layer identifies an item by `ItemId` — a record wrapping
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

Every town carries a `StockLedger` — an immutable roll of per-item
quantities — accessible via `Town.stockLedger()` (the strangler
accessor) and rebuilt from the legacy `reserveStock` map on every
call. A future carve promotes the ledger to source of truth and
demotes `reserveStock` to a persistence-only adapter; today the
ledger is a read view.

#### Scenario: a fresh town has an empty ledger

- **WHEN** a brand-new `Town()` is constructed
- **THEN** `stockLedger().isEmpty() == true` and
  `stockLedger().get(<any ItemId>) == 0`.

#### Scenario: an old world loads with an empty ledger

- **WHEN** a world last saved before this commit is opened by a
  post-carve build
- **THEN** `stockLedger().isEmpty() == true` — no migration needed.

#### Scenario: the ledger reflects reserveStock on read

- **WHEN** `Town.addStock(<some Item>, 10)` is called and then
  `town.stockLedger()` is called
- **THEN** the ledger's `get(ItemId.of("namespace:path"))` returns
  `10` for the corresponding item, and `EMPTY`-sentinel for any
  other item.

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