# tasks — `settlement-production-domain`

## 1. Domain types (JDK-only)

- [x] 1.1 `domain/settlement/ProductionRule.java` — record
  `(ItemId output, int amount, long everyTicks, int capacityItems)`,
  validated at construction (`amount > 0`, `everyTicks > 0`,
  `capacityItems >= 0`). `isDue(long gameTime)` returns
  `gameTime % everyTicks == 0`; `isActiveCadence(int)` mirrors
  the legacy `effectiveTicks > 0` short-circuit.
- [x] 1.2 `domain/settlement/ProductionPlan.java` — immutable
  `(List<ProductionRule> rules, double bonusMultiplier)`, `EMPTY`
  sentinel, defensive copy of `rules` on construction, `bonusMultiplier
  >= 0` enforced. `computeDueOutputs(long gameTime, long lastTick)`
  is a pure function that iterates the rules, applies `isDue`,
  scales the per-rule amount by the bonus multiplier, rounds with
  `Math.round`, and merges into a sparse `Map<ItemId, Integer>`.
  Scaled amounts that round to zero are dropped at the edge.
  `lastTick` is kept on the signature for forward-compat (a future
  interval-based accumulator) but is unused today.
- [x] 1.3 `domain/settlement/TransformationRule.java` — record
  `(List<StockCost> inputs, ItemId output, int outputAmount,
  int outputCapacityItems)` with a nested `StockCost(ItemId, int)`
  record validated at construction. `apply(StockLedger)` is a pure
  single-shot that drains every input and adds the output in one
  ledger transaction, throwing `IllegalStateException` on
  insufficient input (same failure path as `StockLedger.take`).
  `canApply(StockLedger)` is a non-mutating pre-check; `inputTotals()`
  sums duplicate inputs into a per-item total. The full multi-pass
  budget loop stays in `ProductionManager.tickTransformer` for this
  carve.

## 2. Town facade / ProductionManager wiring (strangler)

- [x] 2.1 `tick/ProductionManager.java` — add three
  `net.minecraft.core.registries.BuiltInRegistries` / domain imports
  for `ItemId`, `ProductionRule`, `ProductionPlan`. Add two
  package-private helpers: `buildProductionPlan(ProductionEntry,
  double)` and `computeBoostedAmount(ProductionEntry, double, long,
  long)`. The per-entry amount calculation in `tick` is re-routed
  through `computeBoostedAmount` — the inline
  `Math.round(entry.amount() * totalMultiplier)` is gone.
- [x] 2.2 `tickTransformer` is not touched. The multi-pass budget
  loop and the per-building capacity cap stay in the adapter; a
  future carve folds them into a `TransformationPlan` helper.
- [x] 2.3 `Town.java` is not touched. The legacy
  `ProductionEntry` / `TransformationRecipe` records stay the
  source of truth and the NBT round-trip owner. The domain types
  are the projection the tick arithmetic now runs on.
- [x] 2.4 NBT shape unchanged. `ReserveStock`, every
  `PlacedBuilding` NBT key, every `BuildingDataHandler` JSON key —
  byte-for-byte identical.

## 3. Unit tests (bare JVM)

- [x] 3.1 `ProductionPlanTest` — `ProductionRule` validates
  (positive amount / cadence, non-negative capacity), `isDue` ticks
  on the `everyTicks` boundary from tick 0, `isActiveCadence`
  mirrors the legacy short-circuit, an empty plan produces nothing
  at any tick (and is referentially stable on `EMPTY`), a single
  rule emits its scaled amount on its due tick, the bonus multiplier
  scales the per-tick amount before rounding, a non-positive bonus
  collapses the plan to no output, multiple rules emit independently,
  overlapping rules on the same item sum into one map entry, a
  scaled amount that rounds to zero is dropped, `rules()` and
  `bonusMultiplier()` are read-only views, a tick that produces
  nothing returns the empty map.
- [x] 3.2 `TransformationRuleTest` — `StockCost` rejects zero /
  negative amounts, `TransformationRule` rejects zero / negative
  output amounts and negative capacity, `apply` drains all inputs
  and adds the output in one ledger transaction, `apply` sums
  duplicate inputs (e.g. 2x2 wheat → 1 flour), `apply` with
  multiple distinct inputs drains all of them in one transaction,
  `apply` throws `IllegalStateException` when any input is short
  and the source ledger is unchanged (immutability), `apply` throws
  when the missing input is not on the ledger at all, `canApply` is
  a non-mutating pre-check matching the apply failure path,
  `inputTotals` sums duplicate inputs into a per-item total, the
  inputs list is defensively copied, `inputs()` is a read-only view.

## 4. ADR

- [x] 4.1 Write `docs/06-decisions/ADR-0015-production-domain.md`
  recording: the `ProductionRule` / `ProductionPlan` /
  `TransformationRule` shapes, the per-entry amount re-route through
  `ProductionPlan.computeDueOutputs`, the additive-NBT save-format
  guarantee (untouched), the JDK-only domain purity (no
  `net.minecraft` import in `domain/settlement/`), the read-side
  projection pattern ADR-0010 set up, the non-goals (no full
  `ProductionManager.tick` rewrite, no promotion of
  `PlacedBuilding.stock` to source of truth, no
  `ProductionManager.tickTransformer` rewrite, no `Town.java`
  edits, no NBT key rename).

## 5. Verification

- [x] 5.1 `openspec validate settlement-production-domain --type
  change` exits 0.
- [x] 5.2 `./gradlew :common:compileJava :common:test --no-daemon`
  exits 0 with the new tests passing alongside the existing
  `ProductionPlanTest`, `TransformationRuleTest`, `ItemIdTest`,
  `StockLedgerTest`, `AcquisitionTest`, `ConstructionQueueTest`,
  `QuestLogTest`, `StandingBookTest`, `CitizenIdTest`,
  `MoraleLevelTest`, `MoraleMultiplierTest`, `DayPhaseTest`,
  `DaySimTest`.
- [x] 5.3 `DomainPurityTest` is green: no `import net.minecraft.*`
  or `import net.neoforged.*` lands in `domain/settlement/` or
  `domain/shared/`. No bare `BlockPos` / `ItemStack` / `Level` /
  `CompoundTag` token in `domain/`.

## 6. Explicit non-goals (future carves)

- [ ] 6.1 `ProductionManager.tickTransformer` rewrite: the
  multi-pass budget loop folds into a `TransformationPlan` helper
  that consumes the `TransformationRule` value object. Tracked
  separately; own openspec change.
- [ ] 6.2 Promotion of `PlacedBuilding.stock` to a domain field:
  the `Map<Item, Integer>` becomes a per-building
  `StockLedger`-shaped value, the `Item`-keyed map becomes a
  persistence-only adapter. Tracked separately; depends on a
  per-building-domain accessor landing.
- [ ] 6.3 Pre-computed `ProductionPlan` per `PlacedBuilding` per
  tick: the per-entry `ProductionPlan` allocation collapses into
  one allocation per building. The helper shape supports it.
- [ ] 6.4 Architecture test asserting no `net.minecraft` import
  lands in `domain/settlement/` or `domain/shared/`. The fence
  already exists in `DomainPurityTest`; a future carve may add
  per-class banned-imports for the new types.
- [ ] 6.5 NBT-level change: optional future migration that writes
  per-building production roll entries directly (one
  `ProductionRoll` compound with per-item sub-compounds) instead
  of the current `PlacedBuilding` stock. Today the NBT shape is
  preserved; a future carve may relax that constraint if it has a
  clean additive story.
