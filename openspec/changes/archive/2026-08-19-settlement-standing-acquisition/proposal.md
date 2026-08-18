# Why

ADR-0008 carved out the empty Settlement bounded context; ADR-0009 names
the first two value objects that live inside it: **Acquisition** (the
town's relation to outside authority) and **Standing** (per-citizen
relationship score), with **StandingBook** as the town-level roll.

`Town` is the save-format owner today and stays where it is — this change
adds the strangler facade: two fields, four accessors, two additive NBT
keys. Worlds saved before this commit load unchanged because missing keys
default to `FREE` and an empty book.

**Why now.** The act-4 hub transition (P5 `vanilla-feel`, VISION.md
§"earned-crown trajectory") and the act-5 realm layer (P1
villages-are-autonomous, VISION.md §"realm grows from inside") both need
exactly these primitives. Without standing, the act-4 transition has no
gate; without acquisition, the act-5 realm has nothing to govern. The
carve stays tiny — type-only, no behavior change — so the next two carves
(build the act-4 transition predicate, build the act-5 realm) start from
a domain that's already on the JVM and write tests, not scaffolding.

Pillar citation: this change serves **no pillar directly** — it is the
scaffolding both P1 (autonomy under a chief) and P5 (hub-as-window,
gated on standing) rest on. Per openspec/config.yaml §"rules.proposal",
no pillar claim is asserted; the change is engineering prep, not
gameplay.

# What Changes

- **CAP-MOD** `domain-settlement`: two new value objects land in
  `domain/settlement/` (immutable records), one shared identity wrapper in
  `domain/shared/` (`CitizenId`). All three are Minecraft-free (ADR-0008
  §"Minecraft types leave the domain"). The `Town` aggregate root gains a
  strangler facade — two fields, four accessors, two additive NBT keys.
  No existing field, method, or NBT key is renamed.
- **DOCS** `docs/06-decisions/ADR-0009-standing-acquisition.md` —
  decision record for the four-step acquisition ladder and the standing
  value-object shape.
- **TEST** three pure-JVM JUnit classes under `common/src/test/.../domain/`:
  `AcquisitionTest`, `StandingBookTest`, `CitizenIdTest`. No Minecraft,
  no GameTest.

# Capabilities

## Modified Capabilities

- `domain-settlement`: the existing capability spec gains two
  requirements (`standing value object` and `acquisition lifecycle`) and
  two scenarios for the strangler facade. The pre-existing requirements
  (`settlement domain is Minecraft-free`, `Town stays the aggregate root
  of Settlement`, `save format survives the strangler`) are untouched —
  this change actually demonstrates the third, since additive NBT is the
  mechanism.

# Impact

Affected code:
- `common/src/main/java/org/lowern1ght/burg/domain/shared/CitizenId.java`
  — new record, JDK-only.
- `common/src/main/java/org/lowern1ght/burg/domain/settlement/Acquisition.java`
  — new enum (`FREE`, `ELEVATED`, `FOUNDED`, `CAPTURED`).
- `common/src/main/java/org/lowern1ght/burg/domain/settlement/Standing.java`
  — new record (CitizenId + int).
- `common/src/main/java/org/lowern1ght/burg/domain/settlement/StandingBook.java`
  — new immutable book.
- `common/src/main/java/org/lowern1ght/burg/town/Town.java` — additive
  fields, accessors, and NBT load/save. `chatSubscribers` and every
  other pre-existing field are byte-for-byte untouched.

Affected tests:
- `common/src/test/java/org/lowern1ght/burg/domain/settlement/AcquisitionTest.java`
- `common/src/test/java/org/lowern1ght/burg/domain/settlement/StandingBookTest.java`
- `common/src/test/java/org/lowern1ght/burg/domain/shared/CitizenIdTest.java`

Affected docs:
- `docs/06-decisions/ADR-0009-standing-acquisition.md` — new.
- `openspec/changes/ddd-foundation/specs/domain-settlement/spec.md` —
  unchanged; this change's delta lives in this proposal's own
  `specs/domain-settlement/spec.md` and the ddd-foundation change is
  archived separately.

Affected datapacks: none. No JSON touched.

Affected STATUS.md: none. No row moves out of `build-green`; the only
state that would do that is a recorded in-world walk-through of the
act-4 transition, which depends on this carve's successors.

Verification:
- `openspec validate settlement-standing-acquisition --type change`
  exits 0.
- `./gradlew :common:compileJava :common:test --no-daemon` exits 0 with
  the new tests passing.
- Old saves load unchanged: any world file produced before this commit,
  fed through `Town.fromNbt(...)`, produces a town with
  `getAcquisition() == FREE` and `getStandingBook().isEmpty()`. Verified
  by unit test in `StandingBookTest` (the additive default path).