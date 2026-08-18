# ADR-0017: Realm / Diplomacy / War — seeding the domain layer from existing behavior

- **Status**: Accepted
- **Date**: 2026-08-19
- **Decided by**: owner (architecture session)
- **Builds on**: ADR-0008, ADR-0009

## Context

ADR-0008 carved five bounded contexts and left `domain/realm`,
`domain/diplomacy`, and `domain/war` as empty landing zones. Since then
the act-5 behavior work landed *outside* the domain layer, in the
`behavior/` packages:

- `behavior/diplomacy/` — `DiplomaticStatus` (NEUTRAL, ALLY, TRUCE,
  AT_WAR), `DiplomaticRegistry`, `DiplomaticAI` (morale-threshold
  decisions), and the four `DiplomaticAction` records.
- `behavior/war/` — `Squad`, `SquadGoal`, `BattleState`,
  `BattleStateMachine`, `BattleContext`, `CasualtyModel`.
- `behavior/morale/` — the morale curve those decisions read.

These compile against Minecraft (`Town`, `Npc`), so they cannot simply
move into `domain/` — and ADR-0009's migration discipline (additive,
no import-breaking moves mid-strangler) says don't. Meanwhile the realm
design (docs/03-design/realm) settled in the 2026-07-31 grilling: a
realm is a metropolis plus expedition-founded colonies plus foreign
holdings; VISION names the autonomy–control slider bands and the
relation set (war / truce / alliance / tribute).

The next carves (a realm aggregate, realm-scale diplomacy decisions,
campaign-level war outcomes) all need this vocabulary on the bare JVM
before they can write tests instead of scaffolding — the same reason
ADR-0009 seeded `Acquisition` and `Standing`.

## Decision

Seed five Minecraft-free value types across the three empty contexts.
New types, no file moves, no behavior changes, no battle-engine
rewrite. The `behavior/` packages keep running untouched; adapters
bridge the two vocabularies later, at the behavior edge.

### Domain types (JDK-only)

| Type | Kind | Where | Seeded from |
|---|---|---|---|
| `RealmId` | record wrapping a non-blank canonical string | `domain/realm/` | open storage question (realm README Q1) |
| `HoldingKind` | enum `METROPOLIS, COLONY, FOREIGN` | `domain/realm/` | realm README decisions 1 & 4 |
| `AutonomyBand` | enum `FREE, ELEVATED, FOUNDED, CAPTURED` + gates | `domain/realm/` | VISION §"autonomy–control slider" |
| `RelationStance` | enum `WAR, TRUCE, ALLIANCE, TRIBUTE, NEUTRAL` | `domain/diplomacy/` | VISION relations + behavior `DiplomaticStatus` |
| `BattleOutcome` | record `(boolean attackerWins, OptionalInt, OptionalInt)` | `domain/war/` | behavior war model, summarized |

All five import nothing from `net.minecraft` or NeoForge — the
`DomainPurityTest` fence already covers the new packages, and the five
new test classes run on a bare JVM.

### RealmId has no EMPTY sentinel — deliberately

`CitizenId` and `ItemId` both carry an EMPTY sentinel for the additive
NBT load path. `RealmId` does not, because "how a player with no realm
is represented" is an *open* design question (realm README Q1: nullable
`realmId` vs value-object-on-profile). A sentinel would silently answer
it. The strict factory `of(String)` rejects blank; when the storage
decision lands, its facade chooses null-vs-sentinel explicitly.

### HoldingKind — three kinds, not a village list

The pre-grilling sketch (`villages: List<Town>`) is dead: the realm's
spine is `METROPOLIS` + `COLONY` (grows from inside, expedition-founding
only) and the periphery is `FOREIGN` (attached via elevated / founded /
captured). `isHomeNetwork()` encodes the spine/periphery cut, which is
the distinction trade, war, and autonomy gating all key off.

### AutonomyBand — the slider's four named stops

The bands are name-aligned with `Acquisition` (Settlement context) by
design, but mapped **by name** (`fromAcquisitionName(String)`), not by
import — Realm must not depend on Settlement's internals (ADR-0008:
contexts talk through edges). Unknown names default to FREE, inheriting
ADR-0009's forward-compat rule. The gates mirror the VISION text
verbatim: FREE is deaf to orders; ELEVATED and FOUNDED accept *soft*
orders (the builder still sleeps, still has morale — pillar 4); CAPTURED
requires a garrison. Numeric autonomy drift (realm README Q2) is
deliberately unmodelled — if it lands as a float, these bands become
its cut-offs and the vocabulary survives.

### RelationStance — realm-scale verbs, legacy names documented

`WAR | TRUCE | ALLIANCE | TRIBUTE | NEUTRAL` per VISION's relations
list, with NEUTRAL as the never-interacted default (matching
`DiplomaticRegistry.between`'s semantics). The mapping from the
town-scale `DiplomaticStatus` (AT_WAR→WAR, ALLY→ALLIANCE, TRUCE→TRUCE,
NEUTRAL→NEUTRAL) is documented on the enum as a table for the future
adapter but deliberately not encoded as a method — no code path needs
it yet. `TRIBUTE` is new at the stance level: in the town engine,
`TributeAction` keeps the status column NEUTRAL (tribute is a ledger
riding on any posture); whether realm-scale instances compose
(diplomacy README decision 2 suggests orthogonal flags) is an open
question this seed does not answer. The enum names the verbs.

### BattleOutcome — a summary, not an engine

`(attackerWins, OptionalInt attackerCasualties, OptionalInt
defenderCasualties)` with `decided(boolean)` and `counted(boolean, int,
int)` factories. Casualties are optional because an auto-resolved
engagement may know the winner without counting bodies; when present
they are validated non-negative. The in-game battle state machine
(`BattleStateMachine`, `CasualtyModel`) is **untouched** — this PR does
not rewrite it, port it, or reference it. A future adapter folds
`CasualtyModel` totals into these counts at the behavior edge.

### package-info: context ownership stated

The three `package-info.java` files drop the "landing zone" placeholder
and name their owning ADR, their design doc, and the Minecraft-free
fence.

## Consequences

- + Realm, diplomacy, and war carves now start from a domain that is
  already on the JVM; their tests are pure JUnit (the ADR-0008 fast
  feedback loop, extended to three more contexts).
- + The 2026-07-31 grilling decisions (spine/periphery, slider bands)
  are now *named in code*, not just prose — the next design conversation
  cites `HoldingKind.isHomeNetwork()` instead of re-reading the README.
- + No behavior-file moves: `behavior/diplomacy`, `behavior/war`,
  `behavior/morale` compile exactly as before; zero import churn.
- − Two parallel vocabularies exist until the adapter lands
  (`DiplomaticStatus` vs `RelationStance`). The mapping table on the
  enum is the bridge contract; leaving it unencoded means no premature
  coupling, but also no compiler help if the legacy enum drifts.
- − `AutonomyBand` and `Acquisition` are name-twins that must be
  renamed in lockstep if either changes. The alternative (a
  realm→settlement dependency) was worse; the coupling is documented at
  both ends.

## Non-goals (this change)

- No `Realm` aggregate, no `LevelRealms` SavedData, no `Town.realmId`
  field — storage representation stays open (realm README Q1).
- No adapter between `behavior/diplomacy` and `RelationStance`; no
  refactor of `DiplomaticAI` to read domain types.
- No battle state machine changes of any kind (`BattleStateMachine`,
  `Squad`, `CasualtyModel`, `BattleState` untouched).
- No numeric autonomy value, no drift model (realm README Q2).
- No application layer for any of the three contexts (ports/use cases
  land with their first real use case, per ADR-0014's pattern).

## Verification

- `./gradlew :common:compileJava :common:test --no-daemon` exits 0 —
  `DomainPurityTest` green over the three new packages, five new test
  classes (`RealmIdTest`, `HoldingKindTest`, `AutonomyBandTest`,
  `RelationStanceTest`, `BattleOutcomeTest`) pass alongside the
  existing suite.
- `openspec validate realm-diplomacy-war-seed --type change` exits 0.
- No file under `behavior/` appears in the diff.

## Related

- [ADR-0008](ADR-0008-ddd-foundation.md) — bounded contexts, the
  landing zones this seed fills, the Minecraft-free rule.
- [ADR-0009](ADR-0009-standing-acquisition.md) — the carve pattern
  (additive, JDK-only, bare-JVM tests) this follows.
- `docs/03-design/realm/README.md`, `docs/03-design/diplomacy/README.md`,
  `docs/03-design/war/README.md` — the design decisions the types name.
- `openspec/changes/realm-diplomacy-war-seed/` — the change proposal.
