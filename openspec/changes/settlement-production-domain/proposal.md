# Why

`tick.ProductionManager` is still pure Minecraft: the per-entry amount calculation lives inline in the tick loop, the production roll is `Map<Item, Integer>`, and the only verification base for the cadence math is a running game. ADR-0015's carve extracts the production **rules** and the **plan** (one building's rules + bonus multiplier, with a pure `computeDueOutputs`) into the Settlement domain. The `ProductionManager` re-routes the per-entry amount calculation through the new helper; everything else — the herd-fed gate, the cadence gate, the per-building capacity cap, the `forceAdd` write, the UI push, the dirty mark, the transformer pass — is byte-for-byte unchanged. NBT is untouched, `Town.java` is untouched, the legacy `ProductionEntry` / `TransformationRecipe` records stay the source of truth. The carve also lands a `TransformationRule` value object + a pure `apply(StockLedger)` so the future multi-pass budget loop in the transformer pass has a substrate to consume on a bare JVM.

# What Changes

- **CODE-NEW** `domain/settlement/ProductionRule.java` — record `(ItemId output, int amount, long everyTicks, int capacityItems)` with `isDue(gameTime)` and `isActiveCadence(int)`. Validated at construction.
- **CODE-NEW** `domain/settlement/ProductionPlan.java` — immutable `(List<ProductionRule> rules, double bonusMultiplier)`, `EMPTY` sentinel, pure `computeDueOutputs(gameTime, lastTick) → Map<ItemId, Integer>`. Sparse — scaled amounts that round to zero are dropped.
- **CODE-NEW** `domain/settlement/TransformationRule.java` — record `(List<StockCost> inputs, ItemId output, int outputAmount, int outputCapacityItems)` with pure `apply(StockLedger)`, non-mutating `canApply(StockLedger)`, and `inputTotals()`.
- **CODE-MOD** `tick/ProductionManager.java` — add `buildProductionPlan(ProductionEntry, double)` and `computeBoostedAmount(...)` package-private helpers; the per-entry amount calculation in `tick` now goes through `computeBoostedAmount` instead of the inline `Math.round(entry.amount() * totalMultiplier)`. `tickTransformer` is not touched. `Town.java` is not touched. NBT keys are unchanged.
- **TEST-NEW** `ProductionPlanTest` + `TransformationRuleTest` — bare-JVM JUnit, exercising `computeDueOutputs` (single rule, multi rule, bonus scaling, due-tick, drop-on-zero, empty plan, overlapping rules, view stability) and `apply` / `canApply` / `inputTotals` (single input, multiple inputs, duplicate inputs, insufficient input, defensive copy, view read-only).
- **DOCS** `docs/06-decisions/ADR-0015-production-domain.md` — the decision record.
- **TASK** tick `openspec/changes/ddd-foundation/tasks.md` §4.1 done.

# Capabilities

## Modified Capabilities

- `domain-settlement`: production rules value object + plan + transformation rule, with the production tick re-routed through the new helpers.

# Impact

Affected code:
- `common/src/main/java/org/lowern1ght/burg/domain/settlement/ProductionRule.java` — new.
- `common/src/main/java/org/lowern1ght/burg/domain/settlement/ProductionPlan.java` — new.
- `common/src/main/java/org/lowern1ght/burg/domain/settlement/TransformationRule.java` — new.
- `common/src/main/java/org/lowern1ght/burg/tick/ProductionManager.java` — adds two package-private helpers and re-routes the per-entry amount calculation.
- `common/src/test/java/org/lowern1ght/burg/domain/settlement/ProductionPlanTest.java` — new.
- `common/src/test/java/org/lowern1ght/burg/domain/settlement/TransformationRuleTest.java` — new.

Affected docs:
- `docs/06-decisions/ADR-0015-production-domain.md` — new.
- `openspec/changes/ddd-foundation/tasks.md` — §4.1 ticked.

Unaffected code (intentional):
- `town/Town.java` — untouched. ADR-0014's no-`Town.java`-edits rule for parallel carves.
- `town/ProductionEntry.java`, `town/TransformationRecipe.java` — untouched. They stay the source of truth and the NBT round-trip owner; the domain types are the projection the tick arithmetic now runs on.
- `BuildingDataHandler`, `PlacedBuilding`, `TownInventory`, `SettlerJobsDataHandler` — untouched.
- `tickTransformer` — untouched in this carve; it stays the production path for the legacy multi-pass loop.

Affected datapacks: none. `data/burg/buildings/*.json`, `data/burg/jobs/settler.json` — all unchanged.

Verification:
- `openspec validate settlement-production-domain --type change` exits 0.
- `./gradlew :common:compileJava :common:test --no-daemon` exits 0.
- `DomainPurityTest` (fence #1: import `net.minecraft.*` / `net.neoforged.*`, fence #2: bare type-name `BlockPos` / `ItemStack` / `Level` / `CompoundTag` in `domain/`) is green.
- `ProductionManager.tick` writes the same set of buildings / caps / multipliers as before; only the per-entry amount path is re-routed.
