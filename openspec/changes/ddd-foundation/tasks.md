# tasks — `ddd-foundation`

## 1. Decision record

- [x] 1.1 Write `docs/06-decisions/ADR-0008-ddd-foundation.md`:
  context map (Settlement / Realm / Diplomacy / War / Content), the
  three-layer rule, VO wrappers, strangler + facade plan, non-goals
  (no `Town` split in this change).
- [x] 1.2 Add a "Target DDD shape" section to
  `docs/04-engineering/ARCHITECTURE.md` pointing at ADR-0008; keep the
  existing subsystem map intact.

## 2. Package skeleton

- [x] 2.1 Create
  `common/src/main/java/org/lowern1ght/burg/domain/{settlement,realm,diplomacy,war,shared}/`
  with one `package-info.java` per package stating its context and layer
  rule (no Minecraft types in domain).
- [x] 2.2 Create
  `.../application/{settlement,realm}/` and
  `.../infrastructure/{persistence,neoforge}/` the same way.
- [x] 2.3 Confirm `Town.java` and everything under `town/` is untouched
  (`git diff --stat` shows only adds).

## 3. Spec + validation

- [x] 3.1 Write this change's `specs/domain-settlement/spec.md` with
  Purpose + Requirements + Scenarios (Minecraft-free domain, aggregate
  root, save-format stability).
- [x] 3.2 `openspec validate ddd-foundation --type change` exits 0.
- [x] 3.3 Mod still compiles with the skeleton present (empty packages
  are inert; gradle build green).

## 4. Later changes (explicitly NOT this one)

- [x] 4.1 First carve: Production out of `Town` — landed as
  `openspec/changes/settlement-production-domain/` (ADR-0015). The
  per-entry amount calculation in `ProductionManager.tick` is
  re-routed through `ProductionPlan.computeDueOutputs`; the
  transformer pass (`tickTransformer`) is unchanged; `Town.java`
  is unchanged; NBT is unchanged. The next carve is the
  transformer pass rewrite (`TransformationPlan` helper).
- [x] 4.2 Architecture test enforcing the layering once real classes
  exist (bare-JVM domain purity per `domain-settlement` scenario):
  `common/src/test/java/org/lowern1ght/burg/architecture/DomainPurityTest.java`
  walks the `domain/` subtree and fails on (a) `import net.minecraft.*`
  or `import net.neoforged.*` lines, and (b) bare Minecraft type names
  (`BlockPos`, `ItemStack`, `Level`, `CompoundTag`) used outside comments
  and string literals. Source-text scan only, no ArchUnit — keeps the
  test on the existing JUnit BOM. `:common:test` green.
