# tasks — `realm-diplomacy-war-seed`

## 1. Realm domain types (JDK-only)

- [x] 1.1 `domain/realm/RealmId.java` — record wrapping a non-blank
  canonical string; strict `of(String)` trims and rejects blanks; **no**
  EMPTY sentinel (the no-realm representation is an open design
  question — realm README Q1).
- [x] 1.2 `domain/realm/HoldingKind.java` — enum `METROPOLIS, COLONY,
  FOREIGN`; `isHomeNetwork()` encodes the spine/periphery cut from the
  2026-07-31 grilling.
- [x] 1.3 `domain/realm/AutonomyBand.java` — enum `FREE, ELEVATED,
  FOUNDED, CAPTURED` matching the VISION slider; gates
  `deafToOrders` / `acceptsSoftOrders` / `requiresGarrison`;
  `fromAcquisitionName(String)` maps by name with unknown → FREE
  (no Settlement import).

## 2. Diplomacy + War domain types (JDK-only)

- [x] 2.1 `domain/diplomacy/RelationStance.java` — enum `WAR, TRUCE,
  ALLIANCE, TRIBUTE, NEUTRAL`; NEUTRAL default; legacy
  `DiplomaticStatus` mapping documented as a table, not encoded.
- [x] 2.2 `domain/war/BattleOutcome.java` — record `(boolean
  attackerWins, OptionalInt, OptionalInt)`; factories `decided` /
  `counted`; non-negative validation. Battle state machine untouched.

## 3. package-info ownership

- [x] 3.1 `domain/realm/package-info.java`, `domain/diplomacy/
  package-info.java`, `domain/war/package-info.java` — replace the
  ADR-0008 landing-zone placeholder with context-ownership statements
  citing ADR-0017 and the 03-design docs.

## 4. Unit tests (bare JVM)

- [x] 4.1 `RealmIdTest` — trim/canonical form, value equality, blank
  rejection.
- [x] 4.2 `HoldingKindTest` — three kinds, home-network cut.
- [x] 4.3 `AutonomyBandTest` — the three gates, name mapping,
  unknown → FREE.
- [x] 4.4 `RelationStanceTest` — five stances, NEUTRAL default.
- [x] 4.5 `BattleOutcomeTest` — decided/counted factories, negative +
  null rejection, value equality.

## 5. ADR

- [x] 5.1 `docs/06-decisions/ADR-0017-realm-diplomacy-war-seed.md` —
  records the seed, the no-sentinel decision for RealmId, the
  name-twin coupling of AutonomyBand/Acquisition, and the non-goals
  (no adapter, no battle rewrite, no storage decision).

## 6. Verification

- [x] 6.1 `openspec validate realm-diplomacy-war-seed --type change`
  exits 0.
- [x] 6.2 `./gradlew :common:compileJava :common:test --no-daemon`
  exits 0 — `DomainPurityTest` green over the three new packages, the
  five new test classes pass alongside the existing suite.
- [x] 6.3 Diff inspection: no path under `behavior/`, no `Town.java`,
  no `BattleStateMachine`.

## 7. Explicit non-goals (future carves)

- [ ] 7.1 Adapter bridging `behavior/diplomacy.DiplomaticStatus` ↔
  `RelationStance` (mapping table lives on the enum until then).
- [ ] 7.2 `Realm` aggregate + `LevelRealms` storage + `Town.realmId`
  (realm README Q1 — own change).
- [ ] 7.3 Numeric autonomy / drift model (realm README Q2).
- [ ] 7.4 Battle-engine adapter folding `CasualtyModel` totals into
  `BattleOutcome` counts.
- [ ] 7.5 Application layer (ports / use cases) for the three
  contexts — lands with the first real use case, per ADR-0014's
  pattern.
