# ADR-0009: Standing and Acquisition — the first domain carve out of `Town`

- **Status**: Accepted
- **Date**: 2026-08-19
- **Decided by**: owner (architecture session)
- **Builds on**: ADR-0008

## Context

ADR-0008 set up the bounded-context scaffolding and named the future
carves (`Production`, `ConstructionQueue`, `Standing`, `QuestLog`) but
left `Town.java` itself untouched. This change lands the first one —
`Standing` and `Acquisition` — because both are needed by the next two
carves that are already in flight:

- the **act-4 hub transition** (`hub-becomes-window` change) is gated on
  a per-citizen standing score plus a structural predicate;
- the **act-5 realm layer** (`realm` bounded context, empty package
  only) needs a per-town `Acquisition` to know whether the town governs
  itself, has a chief, has been founded, or has been captured.

Both carves will land as their own openspec changes; this one is the
**scaffolding drop** that makes their tests possible on a bare JVM
rather than only in-game.

The current `Town` already exposes a `chatSubscribers` set persisted as
a list of UUID strings in NBT. That is the pattern this carve follows
exactly — additive keys, missing-key defaults, no rename of existing
fields. The new keys are:

- `Acquisition` — a single string, one of `FREE`, `ELEVATED`,
  `FOUNDED`, `CAPTURED`. Always written; reads as `FREE` when missing.
- `Standings` — a list of compound tags `{Id, Value}`, sparse (omitted
  when no citizen has accumulated standing). The `Id` field is the
  canonical `UUID.toString()` form — the same string form Minecraft
  itself emits and the existing `ChatSubscribers` list already uses.

## Decision

Land the four value-object types in the domain layer and a small
strangler facade on `Town`. No behavior change, no UI change, no
gameplay change. Pure data: types, a few accessors, two NBT keys.

### Domain types (JDK-only)

| Type | Kind | Where |
|---|---|---|
| `CitizenId` | record `CitizenId(String value)` wrapping the canonical UUID string | `domain/shared/` |
| `Acquisition` | enum `FREE, ELEVATED, FOUNDED, CAPTURED` (ordinal = ladder rank) | `domain/settlement/` |
| `Standing` | record `Standing(CitizenId, int)` with `withDelta`, `withValue`, `isZero` | `domain/settlement/` |
| `StandingBook` | immutable map from `CitizenId` to `Standing`, `EMPTY` sentinel for the additive default | `domain/settlement/` |

All four live in `domain/` and import nothing from `net.minecraft` or
NeoForge. `CitizenId.parseOrEmpty(String)` is the lenient edge
converter; `CitizenId.parse(String)` is strict and throws on garbage.
The Town facade converts `java.util.UUID` → `CitizenId` at the call
boundary and never accepts `CitizenId` from outside the package.

### Standing value-object shape

- The score is a plain `int`, not a bucketed enum. The act-4 standing
  threshold is a continuous number (50 by default in the shipped
  builder datapack), and the bucketed reading of "is this citizen
  trusted yet?" lives downstream as `MoraleLevel` (`behavior.morale`).
  Merging them would conflate two different things: standing is a
  per-citizen relationship, morale is a per-town property.
- A citizen whose score is `0` is **dropped from the roll** at the
  `StandingBook` boundary. This keeps the persisted NBT sparse and
  matches the additive default — a town without `Standings` reads as
  "no citizen has accumulated standing".
- Mutations return a new book. `Town.adjustStanding(UUID, int)` rebuilds
  the field; the book is never handed out by reference.

### Acquisition as a four-step ladder

`FREE → ELEVATED → FOUNDED → CAPTURED` is monotonic by ordinal rank.
`FREE` is the additive NBT default. The enum is forward-compatible —
`Acquisition.fromNbtOrDefault(String)` returns `FREE` for any
unrecognized value, so a future build that adds a fifth value reads
old saves as `FREE` rather than throwing.

The ladder is named, not validated today. A future change may add
assertions that the ladder only moves up; this change does not.

### Strangler facade on `Town`

| Surface | Shape |
|---|---|
| Field | `private Acquisition acquisition = FREE` |
| Field | `private StandingBook standingBook = EMPTY` |
| Accessor | `getAcquisition()`, `setAcquisition(Acquisition)` |
| Accessor | `standingFor(UUID) → Standing`, `adjustStanding(UUID, int)` |
| Accessor | `getStandingBook() → StandingBook` (read-only) |

`toNbt` writes `Acquisition` always and `Standings` only when
non-empty. `fromNbt` reads both additively — missing keys default to
`FREE` / `EMPTY`. `chatSubscribers` and every other pre-existing field
are byte-for-byte unchanged.

### What this does NOT do (today)

- No act-4 transition predicate. `hub-becomes-window` lands its own
  predicate that reads `standingBook` + a structural flag; this change
  only makes the standing score exist.
- No act-5 realm wiring. `Acquisition` is persisted and readable; a
  future change teaches the realm layer what to do with each value.
- No NPC awareness of standing. NPCs neither grant nor react to standing
  today; the value goes nowhere.
- No UI for standing. The hub screen, the chat log, the anchor block —
  all unchanged.
- No migration helper. NBT is additive; old worlds read as `FREE` /
  empty book and need no per-key rewrite.

## Consequences

- + The act-4 hub transition and the act-5 realm layer both have their
  data on the JVM before they start writing logic. Their tests can be
  pure JUnit instead of GameTest, and GameTest coverage can focus on
  the engine seam they actually touch.
- + The strangler pattern is exercised — additive NBT keys, missing-key
  defaults, the `EMPTY` sentinel — exactly as ADR-0008 §"Migration:
  strangler + shim" described. The next carve (Production out of Town,
  already in the ddd-foundation §"Later changes" list) inherits the
  same shape.
- + Domain purity is now testable: `AcquisitionTest`,
  `StandingBookTest`, `CitizenIdTest` run on the bare JVM without any
  Minecraft class on the classpath. ADR-0008 §"Consequences" called
  this out as the first fast feedback loop; it is here.
- − `Town` now has two more fields and four more accessors. The class
  is bigger and the `toNbt`/`fromNbt` pair is longer. The increment is
  small (40 LOC, additive-only) and stays inside the strangler budget
  ADR-0008 set.
- − The ladder is not enforced. A future caller could set
  `setAcquisition(CAPTURED)` directly on a town whose prior value was
  `FREE`. This is fine for now — the only callers of `setAcquisition`
  are tests — but a future change that introduces the act-5 realm will
  want to assert monotonic motion. The shape is right for that future
  assertion; the assertion itself is not in scope here.

## Non-goals (this change)

- No Town split, no Carve of Production / ConstructionQueue /
  QuestLog. Those are their own carves (ADR-0008 §"Non-goals").
- No game-rule or datapack key changes.
- No NPC / quest / era / chat-log coupling.
- No architecture test enforcing `domain/` Minecraft-freedom. ADR-0008
  §"Consequences" calls this out — the present change sets the stage,
  a later carve adds the gate.

## Verification

- `./gradlew :common:compileJava :common:test --no-daemon` exits 0.
- Three new JUnit classes pass:
  `AcquisitionTest`, `StandingBookTest`, `CitizenIdTest`.
- `openspec validate settlement-standing-acquisition --type change`
  exits 0.
- `Town.fromNbt` on a tag with no `Acquisition` and no `Standings`
  keys produces a town whose `getAcquisition() == FREE` and
  `getStandingBook().isEmpty()`. Unit-tested by `StandingBookTest`
  (`adjustCanClear`, `emptyIsTheDefault`); also visually verified by
  reviewing the load branch against the existing pre-carve NBT
  contract.

## Related

- [ADR-0008](ADR-0008-ddd-foundation.md) — the DDD foundation this
  carve is the first execution of.
- `openspec/changes/settlement-standing-acquisition/` — the change
  proposal + `domain-settlement` capability delta.
- `hub-becomes-window` change — the act-4 transition that consumes
  `standingBook` once its own predicate lands.
- `domain-settlement` spec §"standing value object" and
  §"acquisition lifecycle" — the executable form of the requirements.