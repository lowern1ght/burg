# ADR-0015: Production rules domain carve

- **Status**: Accepted
- **Date**: 2026-08-19
- **Builds on**: ADR-0008, ADR-0010, ADR-0013

## Context

The carves before this one (ADR-0010 stock ledger, ADR-0011
construction queue, ADR-0012 quest log) extracted *read* views of
`Town` into the domain — pure value objects the production tick
could ignore. The production tick itself, in
`tick.ProductionManager`, is still pure Minecraft: it iterates
`BuildingDef.ResolvedBuildingStats.production()` (a list of
`ProductionEntry(Item, amount, everyTicks, capacityStacks)`), folds
the village bonus / per-instance bonus / worker skill into a
multiplier, and writes the result straight into `PlacedBuilding.stock`
via `forceAdd`. The transformer pass (`tickTransformer`) does the
same dance for `TransformationRecipe` records: multi-pass budget
allocation over `Item`-keyed maps, with capacity caps and reserve-stock
drain.

The carve ADR-0008 was set up to deliver needs to land this layer of
the production code in the domain too. The roadmap calls for
**production rules as testable arithmetic** so the "what did this
building produce this tick" question is answerable on a bare JVM, not
on a running Minecraft instance. Today it is not — the
`ProductionManager` tick is exercised only in-game, and a regression
in the cadence math ships to a save before anyone notices.

The carve also has to fit the pattern ADR-0010 / ADR-0011 / ADR-0012
established: **additive, behaviour-preserving, NBT untouched**. The
production tick currently writes `PlacedBuilding.stock` and
`TownInventory.removeStock`; this carve must keep both writers in
place and only re-route the arithmetic through a domain helper.

## Decision

Land three value objects in the domain layer — `ProductionRule`,
`ProductionPlan`, `TransformationRule` — and re-route the per-entry
amount calculation in `ProductionManager.tick` through the new
`ProductionPlan.computeDueOutputs` helper. The legacy
`ProductionEntry` / `TransformationRecipe` records stay the source of
truth; the domain objects are the projection the tick arithmetic now
runs on.

### Domain types (JDK-only)

| Type | Kind | Where |
|---|---|---|
| `ProductionRule` | record `(ItemId output, int amount, long everyTicks, int capacityItems)` | `domain/settlement/` |
| `ProductionPlan` | immutable `(List<ProductionRule> rules, double bonusMultiplier)`, with a pure `computeDueOutputs(gameTime, lastTick)` | `domain/settlement/` |
| `TransformationRule` | record `(List<StockCost> inputs, ItemId output, int outputAmount, int outputCapacityItems)` with a pure `apply(StockLedger)` and a non-mutating `canApply(StockLedger)` | `domain/settlement/` |

All three live in `domain/` and import nothing from `net.minecraft` or
NeoForge. `ItemId` is the canonical identity for an item, established
in ADR-0010. The capacity conversion is `capacityStacks * 64` → kept
inside the `Town` facade edge when the rule is constructed from a
legacy `ProductionEntry`; the domain itself speaks in items.

### ProductionRule shape

- One output line of a building. `amount` and `everyTicks` are
  positive; `capacityItems` is non-negative. Validated at
  construction so the apply path can rely on the shape.
- `isDue(gameTime)` is true iff `gameTime % everyTicks == 0` — the
  same check `ProductionManager.tick` has always run, just lifted
  into a helper.
- `isActiveCadence(int)` mirrors the legacy `effectiveTicks > 0`
  short-circuit.

### ProductionPlan shape

- Immutable. `rules` is defensively copied; `bonusMultiplier` is
  non-negative. `EMPTY` is the additive default for a building with
  no production rules.
- `computeDueOutputs(gameTime, lastTick)` is a **pure function**:
  same inputs → same `Map<ItemId, Integer>`. No I/O, no
  `TimeProvider`, no `PlacedBuilding`. Iterates the rules, checks
  `isDue(gameTime)`, scales the per-rule amount by
  `bonusMultiplier`, rounds with `Math.round`, and merges into a
  sparse `LinkedHashMap` keyed by `ItemId`. A scaled amount that
  rounds to zero is dropped at the edge so the produced map is
  sparse.
- The `lastTick` parameter is kept on the signature for forward
  compatibility (a future carve may move to an interval-based
  accumulator) but is unused today; the current rules evaluate on
  the current `gameTime` only.

### TransformationRule shape

- One recipe in the Minecraft-free shape: a list of `StockCost`
  records, the output `ItemId`, the output amount, and the per-
  building capacity. Capacity is on the rule because the legacy
  `TransformationRecipe.outputCapacityStacks * 64` is a property of
  the recipe, not of the building.
- `apply(StockLedger)` is a pure single-shot that drains every
  input and adds the output in one ledger transaction. Throws
  `IllegalStateException` if any input is short — the same
  deterministic failure `StockLedger.take` raises. Outputs are
  added without a capacity check; the capacity cap is the
  per-`PlacedBuilding` cap and is the adapter's job, not the
  rule's.
- `canApply(StockLedger)` is a non-mutating pre-check that returns
  the same boolean the `apply` failure path would produce. The
  tick adapter can use it to skip recipes that obviously fail
  without paying for a `take` throw.
- `inputTotals()` is a per-item total of the input amounts. The
  future multi-pass budget loop in the tick adapter needs the per-
  input total to know how much of the stock is fair game across
  the loop; the helper pins the contract so the carve is wired.

### Town facade — what changes, what does not

`ProductionManager.tick` is touched in one place: the per-entry
amount calculation. The legacy inline arithmetic

```java
int boostedAmount = (int) Math.round(entry.amount() * totalMultiplier);
```

is replaced by a call to a new package-private helper

```java
int boostedAmount = computeBoostedAmount(entry, totalMultiplier, gameTime, lastTick);
```

which builds a one-rule `ProductionPlan` for the entry and asks
`computeDueOutputs` for the scaled amount. The capacity cap
(`Math.min(boostedAmount, max - current)`), the cadence gate
(`gameTime % effectiveTicks != 0 → continue`), the herd-fed gate,
the `changed` flag, the UI push cooldown, the dirty-marking
(`LevelTowns.get(level).markDirty()`) — all byte-for-byte identical.

The transformer pass (`tickTransformer`) is **not touched** in this
carve. Its multi-pass budget loop is its own domain helper (a
future `TransformationPlan.computeDueRecipes`); today the
`TransformationRule` value object is the shape the next carve
consumes. ADR-0014's no-`Town.java`-edits rule for parallel carves
is honored: `Town.java` is not touched at all.

### What this does NOT do (today)

- No full `ProductionManager.tick` rewrite. The transformer pass,
  the worker / skill multiplier, the herd-fed gate, the
  reservation refund, the dirty mark, the UI push — all stay
  where they were. The tick adapter is a thin pass-through that
  delegates one arithmetic step to the domain.
- No promotion of the production roll to source of truth. The
  `PlacedBuilding.stock` map and the `reserveStock` field stay the
  NBT round-trip owners; the new domain types are the projection
  the tick arithmetic now runs on.
- No `TransformationRule.apply` is wired into the production tick.
  The helper is exercised in unit tests (`TransformationRuleTest`)
  but the legacy `tickTransformer` loop is still the production
  path. A future carve folds the loop into a domain helper.
- No NBT key rename. `ReserveStock`, every `PlacedBuilding` NBT
  key, every `BuildingDataHandler` JSON key — all unchanged.
- No `net.minecraft` import lands in `domain/settlement/`. The
  `Item`-keyed legacy records stay in `town/`; the registry lookup
  that converts one to the other happens in the
  `ProductionManager.buildProductionPlan` helper, at the facade
  edge.

## Consequences

- + The arithmetic of "what did this building produce this tick" is
  exercisable on a bare JVM. `ProductionPlanTest` covers the
  single-rule, multi-rule, bonus-scaling, due-tick, drop-on-zero,
  empty-plan, and overlapping-rule paths without a Minecraft
  classpath. The same applies to `TransformationRuleTest` for the
  apply / canApply / inputTotals / defensive-copy paths.
- + The third value object family in the Settlement bounded
  context. `ProductionRule` + `ProductionPlan` + `TransformationRule`
  joins `ItemId` + `StockLedger` (ADR-0010) and `StandingBook` +
  `Acquisition` (ADR-0009) on the bare-JVM side of the layering
  rule. The domain gets wider each carve.
- + The seam at `ProductionManager.buildProductionPlan` is the
  single place the registry lookup happens. A future carve that
  moves to a pre-computed `ProductionPlan` field on
  `PlacedBuilding` (or replaces the per-entry `Item` resolution
  with a single per-building resolution) has one site to change.
- + The tick is not rewritten. The legacy `effectiveTicks` /
  `gameTime % effectiveTicks` cadence gate, the herd-fed block, the
  per-building `forceAdd` write — all preserved. The carve is
  additive: a future promotion to source-of-truth is its own
  openspec change.
- − `ProductionManager` gains three imports and two package-private
  helpers. The tick body shrinks by one line (the inline
  `Math.round(entry.amount() * totalMultiplier)` becomes a
  `computeBoostedAmount` call) and the change is in the hot path
  — the per-entry cost is a `ProductionPlan` allocation per
  rule. This is acceptable today: the building count is bounded
  (a few dozen per town) and the rule list is short (1–3 entries
  per building). A future carve that pre-computes the plan once
  per building per tick can collapse the allocation; the helper
  shape supports it.
- − `ProductionPlan.computeDueOutputs` does not yet consider the
  per-building capacity cap. The cap is checked in the tick adapter
  (`Math.min(boostedAmount, max - current)`). A carve that moves
  capacity into the domain can do so without changing the rule
  shape — `ProductionRule.capacityItems` is already there.

## Non-goals (this change)

- No `ProductionManager.tickTransformer` rewrite. The multi-pass
  budget loop stays in the adapter; a future carve folds it into a
  `TransformationPlan` helper.
- No `PlacedBuilding` accessor for a pre-computed plan. The plan
  is built per-tick per-entry; a future carve caches it.
- No promotion of `PlacedBuilding.stock` to a domain field. The
  `Map<Item, Integer>` stays the source of truth for per-building
  stock; ADR-0013's `StockLedger` is the dual-write companion
  for the reserve stock only.
- No `Town.java` edits. The change touches the production tick
  in `tick/`, the domain layer, the spec, and the docs. `Town`
  and the existing `ProductionEntry` / `TransformationRecipe`
  records are byte-for-byte unchanged.
- No `net.minecraft` import in the domain layer.
- No NBT key rename.

## Verification

- `./gradlew :common:compileJava :common:test --no-daemon` exits
  0.
- The new tests pass alongside the existing
  `ProductionPlanTest`, `TransformationRuleTest`, `ItemIdTest`,
  `StockLedgerTest`, `AcquisitionTest`, `ConstructionQueueTest`,
  `QuestLogTest`, `StandingBookTest`, `CitizenIdTest`,
  `MoraleLevelTest`, `MoraleMultiplierTest`, `DayPhaseTest`,
  `DaySimTest`. `DomainPurityTest` is green: no `import
  net.minecraft.*` or `import net.neoforged.*` lands in
  `domain/settlement/` or `domain/shared/`.
- `openspec validate settlement-production-domain --type change`
  exits 0.
- `ProductionManager.tick` runs the same set of buildings,
  gates, and writes it did before the carve. The legacy
  `effectiveTicks` arithmetic is preserved; the per-entry amount
  is computed by `ProductionPlan.computeDueOutputs` instead of an
  inline `Math.round`, but the result is identical for every
  `(amount, multiplier)` pair (rounding behaviour preserved).

## Related

- [ADR-0008](ADR-0008-ddd-foundation.md) — the DDD foundation.
- [ADR-0010](ADR-0010-stock-ledger.md) — the recipe for
  Minecraft-free value objects in the Settlement context.
- [ADR-0011](ADR-0011-construction-queue.md) — the second carve
  (construction queue).
- [ADR-0012](ADR-0012-quest-log.md) — the third carve (quest log).
- [ADR-0013](ADR-0013-stock-promote.md) — the dual-write
  `StockLedger` companion.
- [ADR-0014](ADR-0014-settlement-application.md) — the application-
  layer ports; this carve does not introduce an application service
  because the production tick is not a use case, it is a Minecraft
  adapter.
- `openspec/changes/settlement-production-domain/` — the change
  proposal + `domain-settlement` capability delta.
- `openspec/changes/ddd-foundation/tasks.md` §4.1 — the task this
  carve ticks.
