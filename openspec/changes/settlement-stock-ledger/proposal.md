# Why

ADR-0009 carved `Standing` and `Acquisition` out of `Town.java` as the
first strangler-facade exercise: additive NBT keys, missing-key defaults,
no rename of existing fields, and a fresh `StandingBook` /
`Acquisition` domain view the engine can read without `net.minecraft` on
the classpath. This change lands the **second** carve — the stock bag —
following the same recipe.

`Town.reserveStock` is the floating-reserve counterpart to per-building
storage (`PlacedBuilding.getStock`). It is a
`Map<net.minecraft.world.item.Item, Integer>`, mutated by `addStock`,
the construction queue's reservation/refund cycle, and `TownInventory`'s
drain on build placement, and persisted to NBT under the `ReserveStock`
compound tag (each key is `BuiltInRegistries.ITEM.getKey(item).toString()`,
each value is the quantity). `ProductionManager` reads and writes this
map every dawn tick; the UI serializes it; the construction queue
reads it to answer "does the town have N of this?".

The next domain move the roadmap wants — a budgeted stock ledger that
the production tick can reason about **without `Item`** — cannot land
without the equivalent of `ItemId`. The `Item` reference leaks the
registry, the mod-loader classpath, and the per-mod namespace into a
place where pure arithmetic should suffice. This carve lands `ItemId`
and `StockLedger`, exposes a Minecraft-free view through
`Town.stockLedger()`, and does **not** touch the production tick.

**Why now.** `ProductionManager` is the second-largest class in the
mod (300+ LOC) and the test coverage on it is GameTest-only. Landing
the value-object scaffolding before the tick rewrite means the tick
rewrite has its own types to lean on and its own JUnit class to run
alongside the GameTest coverage. The asymmetry this carve introduces —
read goes through the domain view, write still goes through the legacy
`Item`-keyed map — is the strangler pattern ADR-0008 / ADR-0009 set
up: additive, no behavior change, no rename.

Pillar citation: this change serves **no pillar directly** — it is the
scaffolding the budgeted-stock ledger lands on. Per
`openspec/config.yaml` §"rules.proposal", no pillar claim is asserted;
the change is engineering prep, not gameplay.

# What Changes

- **CAP-MOD** `domain-settlement`: two new value objects land —
  `ItemId` (record wrapping the canonical `namespace:path` string) in
  `domain/shared/`, and `StockLedger` (immutable map from `ItemId` to a
  non-negative quantity) in `domain/settlement/`. Both are Minecraft-free
  (ADR-0008 §"Minecraft types leave the domain"). The `Town` aggregate
  root gains a strangler accessor — `stockLedger()` — that rebuilds a
  Minecraft-free `StockLedger` view from the legacy `reserveStock` map
  on every call. No existing field, method, or NBT key is renamed.
- **DOCS** `docs/06-decisions/ADR-0010-stock-ledger.md` — decision
  record for the stock-ledger shape and the additive-only strangler.
- **TEST** two new pure-JVM JUnit classes under `common/src/test/.../domain/`:
  `ItemIdTest`, `StockLedgerTest`. No Minecraft, no GameTest.

# Capabilities

## Modified Capabilities

- `domain-settlement`: the existing capability spec gains two
  requirements (`item identity wrapper` and `stock ledger value
  object`) and one scenario for the strangler accessor. The
  pre-existing requirements (`settlement domain is Minecraft-free`,
  `Town stays the aggregate root of Settlement`, `save format
  survives the strangler`) are unchanged — this carve actually
  exercises the third a second time, since the `ReserveStock` NBT
  shape is preserved and an old world's empty reserve reads as the
  `StockLedger.EMPTY` sentinel.

# Impact

Affected code:
- `common/src/main/java/org/lowern1ght/burg/domain/shared/ItemId.java`
  — new record, JDK-only. Wraps the canonical `namespace:path` string
  from `ResourceLocation`.
- `common/src/main/java/org/lowern1ght/burg/domain/settlement/StockLedger.java`
  — new immutable book (the second of its kind, after
  `StandingBook`). `add`, `take`, `merge` return a new ledger;
  zero-quantity entries drop at the edge.
- `common/src/main/java/org/lowern1ght/burg/town/Town.java` — one new
  accessor (`stockLedger()`), two new imports (`ItemId`,
  `StockLedger`). `reserveStock`, `addStock`, `removeStock`, the
  construction queue, the NBT round-trip, and every other field are
  byte-for-byte untouched.

Affected tests:
- `common/src/test/java/org/lowern1ght/burg/domain/shared/ItemIdTest.java`
- `common/src/test/java/org/lowern1ght/burg/domain/settlement/StockLedgerTest.java`

Affected docs:
- `docs/06-decisions/ADR-0010-stock-ledger.md` — new.
- `openspec/changes/settlement-standing-acquisition/specs/domain-settlement/spec.md`
  — unchanged; this change's delta lives in this proposal's own
  `specs/domain-settlement/spec.md` and the standing-acquisition
  change is archived separately.

Affected datapacks: none. No JSON touched.

Affected STATUS.md: none. No row moves out of `build-green`; the
only state that would do that is a recorded in-world walk-through of
the production tick reading through the ledger, which depends on this
carve's successors.

Verification:
- `openspec validate settlement-stock-ledger --type change` exits 0.
- `./gradlew :common:compileJava :common:test --no-daemon` exits 0
  with the new tests passing alongside the existing
  `AcquisitionTest`, `StandingBookTest`, `CitizenIdTest`,
  `MoraleLevelTest`, `MoraleMultiplierTest`, `DayPhaseTest`,
  `DaySimTest`.
- Old saves load unchanged: any world file produced before this
  commit, fed through `Town.fromNbt(...)`, produces a town whose
  `stockLedger().isEmpty() == true` (the additive-default path).
  Verified by `StockLedgerTest` (`emptyIsTheDefault`,
  `mergeCancelDrops`).