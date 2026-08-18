# Why

ADR-0008 carved the Realm, Diplomacy, and War bounded contexts as empty
landing zones. The act-5 behavior work since landed *outside* them, in
`behavior/diplomacy` (DiplomaticStatus, DiplomaticAI, the action
records) and `behavior/war` (Squad, BattleStateMachine, CasualtyModel)
— all compiled against Minecraft (`Town`, `Npc`), so none of it can
move into `domain/` without breaking imports mid-strangler (the exact
move ADR-0009 told us not to make).

Meanwhile the 2026-07-31 grilling settled the realm shape in
design prose: a metropolis plus expedition-founded colonies plus
foreign holdings (docs/03-design/realm decisions 1 & 4), the
autonomy–control slider's four bands (VISION §"autonomy–control
slider"), and the realm relation set (VISION §"immediate architecture
consequence": war / truce / alliance / tribute). None of it is named
in code yet.

**Why now.** The next three carves — a Realm aggregate, realm-scale
diplomacy decisions, campaign-level war outcomes — each need this
vocabulary on the bare JVM before they start, so their tests are pure
JUnit instead of scaffolding (the ADR-0009 pattern, third repetition).
This change is that seed: five value types, zero behavior change, zero
file moves.

Pillar citation: this change serves **no pillar directly** — it is the
scaffolding P1 (villages are autonomous; the slider bands) and the
act-5 "rule, and negotiate" verb rest on. Per openspec/config.yaml
§"rules.proposal", no pillar claim is asserted; the change is
engineering prep, not gameplay.

# What Changes

- **CAP-MOD** new Minecraft-free value types land in three until-now
  empty domain packages (all JDK-only, covered by the existing
  `DomainPurityTest` fence):
  - `domain/realm/RealmId.java` — record, strict non-blank factory,
    deliberately **no** EMPTY sentinel (the "player with no realm"
    representation is an open design question a sentinel would silently
    answer).
  - `domain/realm/HoldingKind.java` — `METROPOLIS | COLONY | FOREIGN`
    with `isHomeNetwork()` encoding the spine/periphery cut.
  - `domain/realm/AutonomyBand.java` — `FREE | ELEVATED | FOUNDED |
    CAPTURED` with the VISION gates (`deafToOrders`,
    `acceptsSoftOrders`, `requiresGarrison`) and a name-based
    `fromAcquisitionName(String)` that imports nothing from Settlement.
  - `domain/diplomacy/RelationStance.java` — `WAR | TRUCE | ALLIANCE |
    TRIBUTE | NEUTRAL`; NEUTRAL is the default; the legacy
    `DiplomaticStatus` mapping (AT_WAR→WAR, ALLY→ALLIANCE) is
    documented as a table for the future adapter, not encoded.
  - `domain/war/BattleOutcome.java` — record `(attackerWins,
    OptionalInt attackerCasualties, OptionalInt defenderCasualties)`
    with `decided` / `counted` factories; casualties validated
    non-negative.
- **DOCS** `docs/06-decisions/ADR-0017-realm-diplomacy-war-seed.md` —
  the decision record, including the no-sentinel and
  name-twin-coupling trade-offs.
- **DOCS** the three `package-info.java` files replace the "landing
  zone" placeholder with context-ownership statements.
- **TEST** five pure-JVM JUnit classes under
  `common/src/test/.../domain/`.

Explicitly unchanged: `behavior/diplomacy/**`, `behavior/war/**`,
`behavior/morale/**`, `Town.java`, `BattleStateMachine`, and every
other behavior file — no moves, no renames, no rewrites.

# Capabilities

No spec deltas in this change (`skip_specs`): the realm / diplomacy /
war capabilities have no spec text to modify yet, and the seeded types
carry no runtime behavior a WHEN/THEN scenario could exercise in a
running world. The executable form of these requirements is the unit
suite + `DomainPurityTest`; capability specs land with the first
behavior-bearing carve (the Realm aggregate), following the
settlement pattern where `domain-settlement` grew requirements only as
behavior landed.

# Impact

Affected code (new files only, plus three package-info edits):
- `common/src/main/java/org/lowern1ght/burg/domain/realm/RealmId.java`
- `common/src/main/java/org/lowern1ght/burg/domain/realm/HoldingKind.java`
- `common/src/main/java/org/lowern1ght/burg/domain/realm/AutonomyBand.java`
- `common/src/main/java/org/lowern1ght/burg/domain/diplomacy/RelationStance.java`
- `common/src/main/java/org/lowern1ght/burg/domain/war/BattleOutcome.java`
- `domain/{realm,diplomacy,war}/package-info.java` — ownership text.

Affected tests (new):
- `common/src/test/java/org/lowern1ght/burg/domain/realm/RealmIdTest.java`
- `common/src/test/java/org/lowern1ght/burg/domain/realm/HoldingKindTest.java`
- `common/src/test/java/org/lowern1ght/burg/domain/realm/AutonomyBandTest.java`
- `common/src/test/java/org/lowern1ght/burg/domain/diplomacy/RelationStanceTest.java`
- `common/src/test/java/org/lowern1ght/burg/domain/war/BattleOutcomeTest.java`

Affected docs:
- `docs/06-decisions/ADR-0017-realm-diplomacy-war-seed.md` — new.

Affected behavior packages: **none** — verified by diff inspection
(no `behavior/` path appears).

Affected datapacks: none. No JSON touched.

Affected STATUS.md: none. No row moves; no in-game behavior exists to
verify.

Verification:
- `openspec validate realm-diplomacy-war-seed --type change` exits 0.
- `./gradlew :common:compileJava :common:test --no-daemon` exits 0;
  `DomainPurityTest` green over the three new packages; the five new
  test classes pass alongside the existing suite.
- `git diff --name-only` contains no path under `behavior/`,
  `town/Town.java`, or `BattleStateMachine`.
