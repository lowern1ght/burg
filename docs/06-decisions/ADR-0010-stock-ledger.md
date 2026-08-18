# ADR-0010: Stock ledger — the second domain carve out of `Town`

- **Status**: Accepted
- **Date**: 2026-08-19
- **Decided by**: owner (architecture session)
- **Builds on**: ADR-0008, ADR-0009

## Context

ADR-0009 landed the first two value objects — `CitizenId`,
`Acquisition`, `Standing`, `StandingBook` — and proved the strangler
pattern: additive NBT, missing-key defaults, no rename. This change
lands the **next** carve out of `Town.java`: the stock bag.

`Town.reserveStock` is the floating-reserve counterpart to the per-
building storage tracked by `PlacedBuilding`. It is a
`Map<net.minecraft.world.item.Item, Integer>`, mutated by `addStock`,
`takeStock`, the construction queue's reservation/refund cycle, and
the player's `/town deposit` command, and persisted to NBT under the
`ReserveStock` compound tag (each key is `BuiltInRegistries.ITEM.getKey(item).toString()`,
each value is the quantity).

The `ProductionManager` tick reads and writes this map; the UI
serializes it; `TownInventory.getStock` aggregates it with the per-
building stocks to answer "does the town have N of this for the queue?".

The carve the roadmap needs next — act-4 "reserveStock becomes a
budgeted ledger" — wants to reason about **quantities without
`Item`**, because the domain layer's only guarantee is Minecraft-free
(ADR-0008 §"Minecraft types leave the domain"). The `Item` reference
leaks the registry, the mod-loader classpath, and the per-mod item
namespace into a place where pure arithmetic should suffice.

The carve is also the second time the team exercises the strangler
facade on `Town`, and the second time the domain gets a fresh
JUnit class on a bare JVM. Like ADR-0009, this change is **additive
and behaviour-preserving**. The `ProductionManager` tick logic is not
touched.

## Decision

Land two value objects in the domain layer — `ItemId` (the canonical
identity for an item) and `StockLedger` (the immutable roll) — and a
strangler accessor on `Town` that rebuilds a `StockLedger` view from
the existing `reserveStock` map. The existing field, the existing
mutators, the existing NBT shape, and the existing tick logic are
byte-for-byte unchanged.

### Domain types (JDK-only)

| Type | Kind | Where |
|---|---|---|
| `ItemId` | record `ItemId(String value)` wrapping the canonical `namespace:path` form | `domain/shared/` |
| `StockLedger` | immutable map from `ItemId` to a non-negative integer, `EMPTY` sentinel | `domain/settlement/` |

Both live in `domain/` and import nothing from `net.minecraft` or
NeoForge. `ItemId.of(String)` is the strict factory; `ItemId.parseOrEmpty(String)`
is the lenient converter used by the additive NBT load path.
`StockLedger.add(ItemId, int)` and `StockLedger.take(ItemId, int)`
return a new ledger; entries whose quantity falls back to zero are
dropped at the edge so the persisted form stays sparse.

### ItemId shape

- The canonical form is lowercase `namespace:path`. `ItemId.of(raw)`
  normalises the input so two callers — one passing `"Minecraft:Stone"`,
  the other passing `"minecraft:stone"` — get the same `ItemId` and
  hash to the same bucket.
- The validator accepts the same character set as Minecraft's
  `ResourceLocation`: letters, digits, `_`, `-`, `.` in the namespace;
  same plus `/` in the path. Spaces and other illegal characters
  cause `of()` to throw `IllegalArgumentException`; `parseOrEmpty()`
  silently returns `EMPTY` instead.
- `ItemId.EMPTY` is a referentially-stable sentinel (the same shape
  as `CitizenId.EMPTY`) used by the additive NBT load path when the
  stored string is absent or malformed.

### StockLedger shape

- Immutable. Every mutator returns a new ledger so the domain stays
  thread-safe without copy-on-write wrappers.
- `get(ItemId)` returns the running quantity, or `0` when the item is
  not on the roll. The roll is sparse: an item never seen reads as
  zero, never as "absent" — the same discipline as `StandingBook`.
- `add(ItemId, int)` returns a new ledger with the running quantity
  bumped. Negative quantities are rejected with
  `IllegalArgumentException` — drain is the job of `take`, not `add`.
- `take(ItemId, int)` returns a new ledger with the quantity reduced;
  throws `IllegalStateException` if the ledger has less than the
  requested amount. Rejects non-positive quantities so the call
  site cannot accidentally underflow by passing the wrong sign.
- `merge(StockLedger)` returns a new ledger whose entries are the
  union of the two, with overlapping quantities summed. Entries that
  fall back to zero are dropped.
- An entry whose quantity falls back to zero is dropped at the edge
  so the persisted form (in a future carve) stays sparse. The
  `EMPTY` sentinel is referentially stable so equality checks
  elsewhere are cheap.

### Strangler facade on `Town`

| Surface | Shape |
|---|---|
| Accessor | `stockLedger() → StockLedger` (read-only view, rebuilt from `reserveStock` on every call) |

The accessor walks `reserveStock`, resolves each `Item` to its
`ResourceLocation` string via `BuiltInRegistries.ITEM.getKey(item)`,
wraps it in an `ItemId`, and hands the lot to `StockLedger.of(view)`.
This rebuild is allocation-cheap — the reserve is small (a few dozen
entries at most) and the rebuild only fires when the caller actually
asks for the domain view. Production ticks that never touch
`stockLedger()` pay nothing.

The accessor is **read-only**. The existing `addStock(Item, int)`,
the queue's reservation/refund cycle, and `TownInventory.removeStock`
keep mutating the legacy `reserveStock` map directly. NBT round-trip
is unchanged: `toNbt` still writes the `ReserveStock` compound tag
the same way, `fromNbt` still reads it the same way, every existing
field is byte-for-byte identical. `chatSubscribers`, `Standings`,
`Acquisition`, the construction queue, every other carve — all
untouched.

### What this does NOT do (today)

- No `ProductionManager` rewrite. The tick logic that mutates
  `reserveStock` continues to operate on the `Item`-keyed map. The
  domain view is a **read-side projection**, not the source of truth.
- No promotion of the ledger. `reserveStock` stays the backing
  store; the ledger is what the domain layer sees. A future carve
  will swap the roles — the ledger becomes the source of truth,
  `reserveStock` becomes a persistence-only adapter — but that swap
  is its own openspec change.
- No construction-queue rewrite. `tryAddToConstructionQueue` and
  `tryQueueUpgrade` continue to check `inv.getStock(item)` (which
  reads both buildings and `reserveStock`) and call
  `inv.removeStock(cost)` to drain the reserve. Domain code that
  wants to express "does the town have N oak logs?" uses
  `town.stockLedger().get(ItemId.of("minecraft:oak_log"))` — but no
  caller is migrated in this PR.
- No NBT key rename. `ReserveStock` is the same compound tag with
  the same per-item keys.
- No `net.minecraft` import lands in `domain/shared/` or
  `domain/settlement/`. The Minecraft-keyed map is rebuilt at the
  `Town` facade edge and never enters a domain signature.

## Consequences

- + The domain layer can reason about quantities without `Item`. A
  test or a future carve that wants to assert "the town holds 50 oak
  logs" no longer needs a Minecraft `Item` reference.
- + The strangler pattern is exercised a second time. The `EMPTY`
  sentinel, the additive NBT default, the immutable roll, the
  drop-on-zero edge — all of it is the same shape as
  `StandingBook`, so the next carve inherits a known recipe.
- + Domain purity is now testable in two places. `ItemIdTest` and
  `StockLedgerTest` run on a bare JVM alongside `CitizenIdTest`,
  `StandingBookTest`, and `AcquisitionTest`. ADR-0008 §"Consequences"
  called out the bare-JVM test as the first fast feedback loop; the
  loop is now wide enough to cover stock too.
- + The `ProductionManager` tick is **not touched**. The roadmap
  sequence (this carve now, the carve that promotes the ledger
  later) preserves the only verification base we have — the in-game
  tick — until the promotion carve has its own tests to lean on.
- − `Town` gains one accessor (`stockLedger()`) and two imports.
  The increment is small (~25 LOC) and stays inside the strangler
  budget ADR-0008 set.
- − The accessor rebuilds on every call. A caller that asks for the
  ledger in a tight loop will pay the rebuild cost each time. This
  is acceptable today — the reserve is tiny — and the next carve
  (the one that promotes the ledger to source of truth) eliminates
  the rebuild by holding a single `StockLedger` field.
- − The strangler is asymmetric on read vs write. Reads go through
  the domain view; writes still go through the `Item`-keyed map.
  The asymmetry is the point — it is what makes the carve additive.
  The next carve removes the asymmetry by reversing the direction.

## Non-goals (this change)

- No `ProductionManager` tick rewrite. No NPC behavior change. No
  cost / recipe / era / quest / queue / era-transition coupling.
- No datapack key changes.
- No migration helper. NBT is additive; old worlds read unchanged.
- No architecture test asserting `domain/` Minecraft-freedom — that
  is a future change (already on the backlog as ADR-0009 task 6.4).
- No replacement of `reserveStock` with `StockLedger` as the
  backing store. The map stays; the ledger is a read view.

## Verification

- `./gradlew :common:compileJava :common:test --no-daemon` exits 0.
- Five new JUnit classes pass: `AcquisitionTest`,
  `StandingBookTest`, `CitizenIdTest`, `ItemIdTest`,
  `StockLedgerTest`.
- `openspec validate settlement-stock-ledger --type change` exits 0.
- `Town.fromNbt` on a tag with no `ReserveStock` key produces a
  town whose `stockLedger().isEmpty() == true` and whose
  `stockLedger()` equals `StockLedger.EMPTY`. Verified by the
  additive-default path in `StockLedgerTest` (an empty source map
  collapses to `EMPTY`).

## Related

- [ADR-0008](ADR-0008-ddd-foundation.md) — the DDD foundation this
  carve is the second execution of.
- [ADR-0009](ADR-0009-standing-acquisition.md) — the first carve
  (standing + acquisition); the recipe this carve follows.
- `openspec/changes/settlement-stock-ledger/` — the change proposal
  + `domain-settlement` capability delta.
- `domain-settlement` spec §"stock ledger value object" and
  §"item identity wrapper" — the executable form of the
  requirements.