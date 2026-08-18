# tasks — `settlement-stock-ledger`

## 1. Domain types (JDK-only)

- [x] 1.1 `domain/shared/ItemId.java` — record wrapping the canonical
  `namespace:path` string. Static factories `of(String)` (strict,
  validates the shape, lowercases, throws on malformed input) and
  `parseOrEmpty(String)` (lenient, returns `EMPTY` on garbage). Accessors
  `namespace()` and `path()` split the canonical form.
- [x] 1.2 `domain/settlement/StockLedger.java` — immutable map from
  `ItemId` to a non-negative quantity. `get(ItemId) → int`,
  `add(ItemId, int)` (returns new ledger, rejects negative quantities),
  `take(ItemId, int)` (returns new ledger, throws
  `IllegalStateException` on insufficient stock), `merge(StockLedger)`
  (sums overlapping entries). `EMPTY` is a referentially-stable
  sentinel for the additive default. Zero-quantity entries drop at the
  edge so the persisted form stays sparse.

## 2. Town facade (strangler)

- [x] 2.1 Add `import` of `ItemId` and `StockLedger` to `town/Town.java`.
- [x] 2.2 Add `stockLedger()` accessor that rebuilds a `StockLedger`
  view from `reserveStock` via `BuiltInRegistries.ITEM.getKey(item)`.
  Read-only — the legacy `reserveStock` map stays the source of truth
  and `addStock` / `removeStock` / the queue's reservation cycle keep
  mutating it directly.

## 3. Unit tests (bare JVM)

- [x] 3.1 `ItemIdTest` — canonical form (lowercase `namespace:path`),
  equality across case, `parseOrEmpty` policy (null/empty/missing-colon
  read as `EMPTY`), `of()` strictness (throws on malformed input), and
  path-segment support (slashes, dots, underscores, dashes).
- [x] 3.2 `StockLedgerTest` — empty default, add accumulates, take
  drains and drops zero, take rejects insufficient and non-positive
  quantities, add rejects negative quantities, merge sums and drops
  zero, `of()` drops zero defensively, immutability, and unknown key
  reads as zero.

## 4. ADR

- [x] 4.1 Write `docs/06-decisions/ADR-0010-stock-ledger.md` recording:
  the stock-ledger shape (`get`/`add`/`take`/`merge`), the drop-on-zero
  discipline, the additive-NBT save-format guarantee, the JDK-only
  domain purity, the read-side-only strangler (writes still go through
  the legacy `reserveStock` map), and the non-goals (no
  `ProductionManager` tick rewrite, no promotion of the ledger to
  source of truth, no construction-queue rewrite — those land in
  separate carves).

## 5. Verification

- [x] 5.1 `openspec validate settlement-stock-ledger --type change`
  exits 0.
- [x] 5.2 `./gradlew :common:compileJava :common:test --no-daemon`
  exits 0 with the new tests passing alongside the existing
  `AcquisitionTest`, `StandingBookTest`, `CitizenIdTest`,
  `MoraleLevelTest`, `MoraleMultiplierTest`, `DayPhaseTest`,
  `DaySimTest`.

## 6. Explicit non-goals (future carves)

- [ ] 6.1 Promotion of the ledger to source of truth: `reserveStock`
  becomes a persistence-only adapter and the `StockLedger` becomes
  the field the production tick reads and writes. Tracked
  separately; depends on `ProductionManager` having its own test
  scaffolding. Own openspec change.
- [ ] 6.2 `ProductionManager` tick rewrite: read/write through
  `stockLedger()` instead of `reserveStock`. Tracked separately,
  depends on the promotion carve landing first. Own openspec change.
- [ ] 6.3 Construction-queue rewrite: `tryAddToConstructionQueue`
  and `tryQueueUpgrade` consult `stockLedger()` instead of
  `TownInventory.getStock` for the reserve portion of the check.
  Tracked separately.
- [ ] 6.4 Architecture test asserting no `net.minecraft` import
  lands in `domain/settlement/` or `domain/shared/`. ADR-0008
  §"Consequences" already calls this out — the present change sets
  the stage, a later carve adds the gate.
- [ ] 6.5 NBT-level change: optional future migration that writes
  `StockLedger` entries directly (one `StockLedgerTag` compound with
  per-item sub-compounds) instead of the current `ReserveStock`
  compound with `BuiltInRegistries.ITEM.getKey(item).toString()` keys.
  Today the NBT shape is preserved; a future carve may relax that
  constraint if it has a clean additive story.