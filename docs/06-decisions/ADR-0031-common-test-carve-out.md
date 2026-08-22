# ADR-0031: Extend the `:common:test` carve-out to behaviour-on-instance pins (`new Town()`)

- **Status**: Accepted
- **Date**: 2026-08-22
- **Builds on**: ADR-0026 (the `:neoforge:test` carve-out — itself referenced only
  in code comments as "the rare test that needs to reach a static helper whose
  signature names a Minecraft type")

## Context

`common/build.gradle` already documents the bare-JVM discipline of
`:common:test`: the project's only fast feedback loop runs without a Minecraft
JVM, and the bulk of the test code stays bare-JVM. Reflection-only tests (e.g.
`TownStructuralFieldsTest`, which reads field and method signatures from the
loaded class without instantiating `Town`) are the default and remain valid.

PR #75 introduced two behaviour pins that need a real `new Town()`:

- `TownAddRoadSegmentFromPlannerTest` — pins `addRoadSegment` /
  `getPlannedRoads` emission order, null drop, `road_laid` flip, and the
  equal-segments-duplicate contract.
- `TownAddZoningFromPlannerTest` — pins `addZoning` / `getZoningCount`
  and `structuralFlags().industryZoned()`.

Both tests land assertions on **per-instance state** (`getPlannedRoads()`,
`getZoningCount()`, `structuralFlags()` reads), so the reflection-only
shape does not work — the mutators and getters are side-effecting instance
methods, not static lookups. The constructor `new Town()` is the only thing
that gives the test code a populated `Town` to drive.

The first `new Town()` triggers Town's static initializer (`<clinit>`),
which transitively resolves:

- JOML (`org.joml.Vector3f` via MC's `ServerLevel` + `Vec3`)
- Netty (`io.netty.handler.codec.DecoderException` via MC's network stack,
  referenced by `ResourceLocation.<clinit>`)
- Mojang's logging facade (`com.mojang.logging.LogUtils`, referenced by
  `BlockPos.<clinit>`)
- the SLF4J binding Town uses for its own logger

The plain bare-JVM `:common:test` classpath has none of these. Without a
classpath injection, the first `new Town()` raises a `NoClassDefFoundError`
chain the same way a naive `:neoforge:test` did before the
`writeServerLegacyClasspath` writer/injection pattern was introduced
(the sibling carve-out referenced in code comments as ADR-0026).

## Decision

Extend the `:common:test` carve-out so the MC test JVM can resolve
`new Town()`. The bare-JVM discipline still holds for the bulk of the test
code; the rare Town-side behaviour pin is allowed to construct a `Town`
instance, mirroring what the `:neoforge:test` target already permits.

Concretely, the `common/build.gradle` `testImplementation` block now ships:

- the ModDev merged JAR (MC classes — `BlockPos`, `ServerLevel`, `Vec3`,
  `ResourceLocation`, the full `net.minecraft.*` graph `Town`'s imports
  reach, transitively)
- SLF4J (`slf4j-api` + `slf4j-simple` — Town's static logger binding)
- brigadier + datafixerupper + authlib (the MC transitive deps Town
  reaches via its imports)
- JUnit 5 + Platform Launcher (the test framework; would be needed
  regardless)

A `doFirst` hook on `:common:test` reads ModDev's
`${buildDir}/moddev/serverLegacyClasspath.txt`, which lists every JAR the
MC test JVM needs minus the loader-specific pieces (Mixin's class
transformer, ModLauncher, etc.). The `dependsOn 'writeServerLegacyClasspath'`
declaration forces the writer to run before the test task graphs the fork;
the `doFirst` mutates the test task's `classpath` file collection at
execution time, appending each legacy classpath JAR to the test JVM.
The writer is idempotent and `UP-TO-DATE` on subsequent runs; only the
first invocation on a clean checkout pays the cost.

The carve is scoped to `testImplementation` only — none of the JARs propagate
to `:common`'s main or `apiElements` configurations, and none propagate to
other modules' classpaths. The `:common` artifact a downstream consumer
links against stays bare-JVM and Minecraft-free (ADR-0008 layering preserved).

The reflection-only shape stays valid and cheaper; the carve applies only to
the rare pin that needs per-instance state. Tests that do not need `new Town()`
keep working on a lighter classpath (the base merged JAR + SLF4J alone, no
legacy classpath injection) — the `doFirst` is on the test task as a whole,
so it runs unconditionally, but the marginal cost is reading a text file and
appending paths, not pulling additional network or disk resources.

## Trade-offs

- **`:common:test` no longer compiles in isolation, only with `:common`'s
  full Gradle graph.** Any teammate adding a new test file must remember
  that `new Town()` now requires the legacy classpath — the
  `doFirst` runs at test-execution time, so a naïve `./gradlew :common:test`
  on a clean checkout fails once before the legacy classpath file is
  generated. The error message explicitly tells the reader to run
  `./gradlew :common:writeServerLegacyClasspath` first or invoke the
  task graph that pulls the writer transitively.
- **The carve-out is now real-Town-constructible, not just reflection-only.**
  This widens the surface area: behaviour-on-instance tests now exercise
  Town's `<clinit>` and therefore the SLF4J binding + log output. A
  refactor that touches Town's static init may break `:common:test` in
  non-obvious ways (e.g. a new transitive MC dep). The strict-version
  pin on brigadier / datafixerupper / authlib catches a future MC bump
  that changes a transitive version here, rather than masking a runtime
  crash later.
- **Full integration tests still belong in `:neoforge:test`.** The
  `:neoforge:test` carve-out (referenced in code comments as ADR-0026)
  pulls in the same legacy classpath plus the rest of the MC runtime
  (the merged JAR's full transitives); it can drive tests that need a
  live `ServerLevel`, a tick loop, or the MC scheduler. `:common:test`
  with this carve-out still cannot drive those — it can only construct
  `Town` instances on bare JVM and exercise the Town-side mutators and
  getters. The `:neoforge:test` target remains the home for GameTest
  Framework-level pins.

## Non-goals

- Flipping `:common:test` to a full ModLauncher boot. The `:neoforge:test`
  target already serves that role; `:common:test` is the bare-JVM fast
  loop and stays focused on Town-side behaviour without a runtime.
- Pulling the legacy classpath into `:common`'s main configuration. The
  carve is `testImplementation` only — it does not affect what
  downstream consumers link against.
- Refactoring `Town` to be more bare-JVM-friendly by moving `BlockPos` /
  `ResourceLocation` references behind a domain-level position type.
  Out of scope for this carve; the static-init chain is the cost of
  `Town`'s current shape, not of the test.

## Related

- `common/build.gradle` — the `testImplementation` block + `doFirst`
  injection that implements the carve-out. The block-level comments
  name this ADR by ID.
- `neoforge/build.gradle` — the `writeServerLegacyClasspath` /
  `dependsOn` / `doFirst` mirror for the `:neoforge:test` target. The
  block-level comments there reference the same carve-out family as
  ADR-0026.
- `common/src/test/java/.../TownAddRoadSegmentFromPlannerTest.java` and
  `common/src/test/java/.../TownAddZoningFromPlannerTest.java` — the
  two pins this carve-out enables, both `new Town()`-driven.
- `neoforge/src/test/java/.../TownStructuralFlagsRealDerivationsTest.java`
  — the `:neoforge:test` companion that drives the same Town-side
  mutators on the fuller MC-aware classpath (Town helper + scheduler +
  full transitives). Both halves of the planner-population seam pass
  on `master`; neither is the SoT and neither subsumes the other.
- ADR-0008 — DDD layering (`infrastructure` is the only Minecraft-aware
  layer; `people` and `domain` stay bare-JVM). The carve-out preserves
  this for downstream consumers: only `:common:test`'s fork sees the
  legacy classpath.
