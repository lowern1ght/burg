# Why

**The mod's act 4–5 systems have nowhere to live.** `Town` is a ~1000-line
god-object (ARCHITECTURE.md §"State layer"); the Realm layer above Town,
diplomacy between realms, and ADR-0004 war-scale combat all need domain
models that are not "another field on Town". ADR-0008 records the owner
decision: five bounded contexts (Settlement, Realm, Diplomacy, War,
Content-as-shared-kernel), a classic domain/application/infrastructure
layering per context, and a strangler migration out of `Town` behind a
facade that keeps the NBT shape.

This change is the **foundation drop**: the ADR, this proposal, one real
capability spec (`domain-settlement`), and an empty Java package skeleton
under the (still current) `org.lowern1ght.burg` package. No
gameplay code moves. `Town.java` is not touched.

Pillar citation: this change serves **no pillar directly** — it is
engineering scaffolding. It exists to protect the delivery of all five
(P1–P5): the act-4 hub transition (`hub-becomes-window`), realm autonomy,
and war-scale combat all land on this structure. It introduces no hard
ban and lifts none.

It is act-agnostic: nothing here changes act-number state, so no
VISION.md act citation applies.

# What Changes

- **DOCS** `docs/06-decisions/ADR-0008-ddd-foundation.md` — the decision
  record: context map, layering, VO wrappers, strangler plan, non-goals.
- **DOCS** `docs/04-engineering/ARCHITECTURE.md` — a short "Target DDD
  shape" section pointing at the ADR; the existing subsystem map is kept
  intact (it still describes reality).
- **CAP-NEW** `domain-settlement`: the Settlement bounded context's
  contract — aggregate root, Minecraft-free domain, NBT facade stability
  (the strangler guarantees). Landed as a spec now so each later carve
  (Production, ConstructionQueue, Standing, QuestLog) has a home to add
  scenarios to.
- **CODE-SKELETON** empty packages under
  `common/src/main/java/org/lowern1ght/burg/`:
  `domain/{settlement,realm,diplomacy,war,shared}`,
  `application/{settlement,realm}`,
  `infrastructure/{persistence,neoforge}` — one `package-info.java` each
  so git tracks the directories. No classes, no `Town.java` move.

# Capabilities

## New Capabilities

- `domain-settlement`: the Settlement context's structural contract —
  Town as aggregate root, Minecraft types excluded from the domain via
  value-object wrappers, and the save-format stability the strangler
  migration depends on. Specs behavior-adjacent structure (testable:
  classpath purity, NBT round-trip), not class diagrams.

## Modified Capabilities

- None. Realm, Diplomacy, War, and Content get capability specs only
  when they gain behavior; skeleton packages alone make no spec claims.

# Impact

Affected code:
- `common/src/main/java/org/lowern1ght/burg/domain/**`,
  `application/**`, `infrastructure/**` — new, empty (package-info only).
- `common/src/main/java/org/lowern1ght/burg/town/Town.java` —
  **not touched** in this change.

Affected docs:
- `docs/06-decisions/` — ADR-0008 appended (numbering gap after 0006 is
  intentional; 0007 is reserved outside this branch).
- `docs/04-engineering/ARCHITECTURE.md` — additive section only.

Affected datapacks: none. No JSON is touched, no described.py self-gate
required.

Verification:
- `openspec validate ddd-foundation --type change` exits 0.
- The mod compiles with the skeleton in place (empty packages are inert).
- Old-world NBT compatibility is asserted by spec scenario, not by this
  change's code (there is no code); it becomes executable when the first
  carve lands.
