# tasks — `settlement-standing-acquisition`

## 1. Domain types (JDK-only)

- [x] 1.1 `domain/shared/CitizenId.java` — record wrapping the canonical
  `UUID.toString()` form. Static factories `of(UUID)`, `parse(String)`,
  `parseOrEmpty(String)`. Used as the key in `StandingBook`.
- [x] 1.2 `domain/settlement/Acquisition.java` — enum `FREE`,
  `ELEVATED`, `FOUNDED`, `CAPTURED`. Static `fromNbtOrDefault(String)`
  returns `FREE` for null / empty / unknown (forward-compat).
- [x] 1.3 `domain/settlement/Standing.java` — record `Standing(CitizenId,
  int)` with `withDelta(int)` and `withValue(int)`. `isZero()` reads as
  `value == 0`.
- [x] 1.4 `domain/settlement/StandingBook.java` — immutable book with
  `standingFor`, `set`, `adjust`, `entries`. Drops zero-score entries at
  the edge so the persisted NBT stays sparse. `EMPTY` is a referentially
  stable sentinel for the additive default.

## 2. Town facade (strangler)

- [x] 2.1 Add `private Acquisition acquisition = FREE` and
  `private StandingBook standingBook = EMPTY` to `town/Town.java`.
- [x] 2.2 Add accessors: `getAcquisition()` / `setAcquisition(Acquisition)`,
  `standingFor(UUID)` (returns `Standing`), `adjustStanding(UUID, int)`,
  `getStandingBook()` (read-only view).
- [x] 2.3 `toNbt` writes `Acquisition` (always) and `Standings` (only
  when non-empty). `fromNbt` reads them additively — missing keys default
  to `FREE` / `EMPTY`. `chatSubscribers` and every other field are
  unchanged.

## 3. Unit tests (bare JVM)

- [x] 3.1 `AcquisitionTest` — additive default, round-trip, ladder order,
  `precedes`.
- [x] 3.2 `StandingBookTest` — empty default, set / adjust, drop-on-zero,
  immutability.
- [x] 3.3 `CitizenIdTest` — canonical-form discipline, `parseOrEmpty`
  policy.

## 4. ADR

- [x] 4.1 Write `docs/06-decisions/ADR-0009-standing-acquisition.md`
  recording: the four-step acquisition ladder, the standing value-object
  shape, the additive-NBT save-format guarantee, the JDK-only domain
  purity, and the non-goals (no behavior change, no act-4 transition
  trigger, no realm wiring — those land in separate carves).

## 5. Verification

- [x] 5.1 `openspec validate settlement-standing-acquisition --type change`
  exits 0.
- [x] 5.2 `./gradlew :common:compileJava :common:test --no-daemon` exits 0
  with the new tests passing alongside the existing
  `MoraleLevelTest`, `MoraleMultiplierTest`, `DayPhaseTest`, `DaySimTest`.

## 6. Explicit non-goals (future carves)

- [ ] 6.1 Act-4 hub transition predicate (`Town.hubMode()` reads
  standing + structural predicate). Tracked separately, depends on
  `hub-becomes-window` capability landing. Own openspec change.
- [ ] 6.2 Realm layer that reads `acquisition` to govern FREE/ELEVATED/
  FOUNDED/CAPTURED towns. Tracked separately, depends on the realm
  bounded-context package (already empty) gaining real code. Own
  openspec change.
- [ ] 6.3 NPC behavior reading per-citizen standing (currently NPCs have
  no awareness of standing). Tracked separately.
- [ ] 6.4 Architecture test asserting no `net.minecraft` import lands in
  `domain/settlement/` or `domain/shared/`. ADR-0008 §"Consequences"
  already calls this out — the present change sets the stage, a later
  carve adds the gate.