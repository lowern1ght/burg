# Delta spec — `settlement-production-domain`

## MODIFIED Requirements

### Requirement: settlement domain is Minecraft-free

The Settlement bounded context's domain layer (`domain/settlement/`
and `domain/shared/`) MUST contain no `net.minecraft` or NeoForge
types. The domain types added by this change (`ProductionRule`,
`ProductionPlan`, `TransformationRule`, plus the nested
`StockCost`) use `ItemId` (ADR-0010) and primitive ints / doubles
only; conversion to and from Minecraft `Item` happens at the
`ProductionManager.buildProductionPlan` helper, at the
infrastructure edge.

Rationale: the domain becomes testable on a bare JVM — the first
fast feedback loop in a project whose verification bar is in-game
(ADR-0008 §"Layers inside each context", ADR-0015).

#### Scenario: production arithmetic on a bare JVM

- **WHEN** a JUnit test calls `ProductionPlan.computeDueOutputs` (or
  any method on `ProductionRule` / `TransformationRule`) and runs
  without Minecraft classes on the classpath
- **THEN** the test compiles and passes — a `net.minecraft` import
  inside `domain/settlement/` or `domain/shared/` is a build
  failure, not a review finding. Pinned by `DomainPurityTest`
  (fence #1) and by the new `ProductionPlanTest` /
  `TransformationRuleTest` running on the `:common:test` JUnit BOM.

#### Scenario: Item stays at the edge

- **WHEN** the production tick needs an item identity for a domain
  call
- **THEN** it converts the Minecraft `Item` (or the `ResourceLocation`
  it points at) to `ItemId` inside
  `ProductionManager.buildProductionPlan`; the domain signature
  accepts only `ItemId`.

### Requirement: Town stays the aggregate root of Settlement

All mutations of settlement state MUST enter through the Town
aggregate root (`domain/settlement/` via the `town/Town.java`
facade). Carved-out members (Production, ConstructionQueue,
Standing, QuestLog, StockLedger — all previous carves) are entities
or value objects owned by the root; no external caller mutates them
directly. The Production carve lands here as the production-rules
projection: the domain types are read-side projections, and the
write side (`PlacedBuilding.stock`, `TownInventory.removeStock`,
the per-building `forceAdd`) is still in the tick adapter. A
future carve promotes the production roll to a domain field; today
the projection is the contract.

#### Scenario: production tick re-routes through the domain plan

- **WHEN** `ProductionManager.tick` runs a per-entry amount
  calculation
- **THEN** it builds a one-rule `ProductionPlan` (via the package-
  private `buildProductionPlan` helper) and asks
  `computeDueOutputs` for the scaled amount; the legacy inline
  `Math.round(entry.amount() * totalMultiplier)` is gone. The
  `forceAdd` write still lands on `PlacedBuilding.stock`, the
  per-building capacity cap is still enforced in the adapter, the
  cadence gate (`gameTime % effectiveTicks != 0 → continue`) is
  still in `tick`, and the dirty mark / UI push / herd-fed gate /
  reservation refund are byte-for-byte identical.

### Requirement: save format survives the strangler

For as long as the strangler migration runs, the world-save
contract MUST NOT change: the `ouat_towns` SavedData keeps the
NBT shape `Town.toNbt()`/`fromNbt()` produce today. A world saved
by a build without this architecture MUST load — behaviorally
unchanged — in a build with it, and vice versa. The Production
carve demonstrates this: the `ReserveStock` compound tag, every
`PlacedBuilding` NBT key, every `BuildingDataHandler` JSON key
(`production`, `transformations`, `transformEveryTicks`,
`transformInputRatio`, etc.), the
`data/burg/jobs/settler.json` shape — all unchanged.

#### Scenario: old world loads after the production-rules carve

- **WHEN** a world last saved before this commit is opened by a
  post-carve build
- **THEN** the town loads with all buildings, queue entries,
  reserve stock, era, standing, quest log intact — verified by
  visual review of `Town.fromNbt` (no `Production` / `transform`
  branch touched) and by the new `ProductionPlanTest` /
  `TransformationRuleTest` exercising the domain arithmetic that
  the tick now re-routes through.

## ADDED Requirements

### Requirement: production rule value object

A Settlement building's production line is described by a
`ProductionRule` — a record `(ItemId output, int amount, long
everyTicks, int capacityItems)` with the following contract:

- `amount` MUST be positive; `everyTicks` MUST be positive;
  `capacityItems` MUST be non-negative. The constructor enforces
  the shape; an invalid rule fails fast.
- `isDue(long gameTime)` returns `true` iff
  `gameTime % everyTicks == 0` — the same check
  `ProductionManager.tick` has always run, lifted into a helper.
- `isActiveCadence(int effectiveTicks)` mirrors the legacy
  `effectiveTicks > 0` short-circuit; the static helper is the
  seam the tick uses to skip a rule whose cadence multiplier
  collapsed to zero.

#### Scenario: rule validates at construction

- **WHEN** `new ProductionRule(item, 0, 100, 64)` (or any non-
  positive amount / cadence, or a negative capacity) is called
- **THEN** it throws `IllegalArgumentException` at construction;
  the tick can rely on the rule's shape without defensive
  re-checks. Pinned by `ProductionRuleValidates`.

#### Scenario: rule fires on its cadence boundary from tick 0

- **WHEN** `ProductionRule(OAK_LOG, 1, 100, 64).isDue(t)` is
  called for `t ∈ {0, 50, 99, 100, 101, 200}`
- **THEN** it returns `true` for `t ∈ {0, 100, 200}` and
  `false` for `t ∈ {50, 99, 101}`. Pinned by
  `productionRuleIsDue`.

### Requirement: production plan with pure `computeDueOutputs`

A `ProductionPlan` is an immutable bundle of
`(List<ProductionRule> rules, double bonusMultiplier)` with a
pure `computeDueOutputs(long gameTime, long lastTick)` that
returns the per-tick `Map<ItemId, Integer>` of due outputs,
scaled by the bonus multiplier, rounded with `Math.round`, and
merged into a sparse `LinkedHashMap` keyed by `ItemId`.

- `bonusMultiplier` MUST be non-negative (the constructor
  enforces it; `computeDueOutputs` is also defensive).
- An empty plan produces nothing at any tick.
- A `bonusMultiplier` of `0.0` (or any non-positive value)
  produces nothing.
- A scaled amount that rounds to zero is dropped at the edge.
- Overlapping rules on the same `ItemId` sum into one map entry.
- `rules()` and `bonusMultiplier()` are read-only views.
- A tick that produces nothing returns `Map.of()` (not `null`).

#### Scenario: single rule emits its scaled amount on its due tick

- **WHEN** `ProductionPlan(rules=[rule(OAK_LOG, 5, 100, 320)],
  bonusMultiplier=1.0).computeDueOutputs(0, 0)` is called
- **THEN** it returns `{OAK_LOG → 5}`. Pinned by
  `singleRuleDue`.

#### Scenario: bonus multiplier scales before rounding

- **WHEN** `ProductionPlan(rules=[rule(STONE, 4, 50, 200)],
  bonusMultiplier=1.5).computeDueOutputs(0, 0)` is called
- **THEN** it returns `{STONE → 6}` (4 × 1.5 = 6.0, rounded
  to 6). Pinned by `bonusMultiplierScales`.

#### Scenario: multiple rules emit independently

- **WHEN** a plan with `[rule(OAK_LOG, 2, 100, 320),
  rule(STONE, 5, 50, 200)]` and `bonusMultiplier=1.0` is
  evaluated at `gameTime ∈ {0, 50, 100}`
- **THEN** `0` and `100` return both outputs;
  `50` returns only the stone. Pinned by `multipleRules`.

#### Scenario: zero-rounding drops the entry

- **WHEN** a plan with `rule(OAK_LOG, 1, 100, 320)` and
  `bonusMultiplier=0.4` is evaluated on a due tick
- **THEN** the result is an empty map — the scaled amount
  `Math.round(0.4) = 0` is dropped, the persisted form stays
  sparse. Pinned by `roundedZeroIsDropped`.

### Requirement: transformation rule value object

A `TransformationRule` is the Minecraft-free shape of a
transformation recipe: a list of `StockCost` inputs, the
`ItemId` output, the per-application output amount, and the
per-building capacity. It exposes:

- `apply(StockLedger stock)` — pure single-shot. Drains every
  input (failing fast with `IllegalStateException` if any is
  short) and adds the output in one ledger transaction. The
  source ledger is unchanged on the failure path (immutability
  via `StockLedger`).
- `canApply(StockLedger stock)` — non-mutating pre-check. Returns
  the same boolean the `apply` failure path would produce; a
  caller can skip recipes that obviously fail without paying for
  a `take` throw.
- `inputTotals()` — read-only per-item sum of the input amounts.
  A future multi-pass budget loop in the tick adapter needs the
  per-input total to know how much of the stock is fair game
  across the loop; the helper pins the contract.
- `inputs()` — read-only view of the inputs in declaration order;
  the constructor's defensive copy guarantees the rule's
  behaviour is independent of the source list.

#### Scenario: apply drains all inputs and adds the output

- **WHEN** `rule(2 wheat → 1 flour).apply(stock)` is called on a
  ledger with 5 wheat
- **THEN** the new ledger has 3 wheat and 1 flour; the source
  ledger is unchanged. Pinned by `applyDrainsAndAdds`.

#### Scenario: apply throws on insufficient input

- **WHEN** `rule(2 wheat → 1 flour).apply(stock)` is called on a
  ledger with 1 wheat
- **THEN** it throws `IllegalStateException` (the same
  deterministic failure `StockLedger.take` raises); the source
  ledger is unchanged. Pinned by `applyFailsOnInsufficientInput`.

#### Scenario: duplicate inputs sum

- **WHEN** a rule with `[StockCost(WHEAT, 2), StockCost(WHEAT, 2)]`
  is applied to a ledger with 4 wheat
- **THEN** the new ledger has 0 wheat and 1 flour — duplicate
  inputs sum, the rule is processed as a single transaction.
  Pinned by `applySumsDuplicateInputs`.

#### Scenario: canApply is a non-mutating pre-check

- **WHEN** `rule(2 wheat → 1 flour).canApply(stock)` is called on
  a ledger with 1 wheat
- **THEN** it returns `false`; calling it does not mutate the
  ledger, and the source ledger's `get(WHEAT)` is still 1.
  Pinned by `canApply`.
