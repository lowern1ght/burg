# ADR-0026: `:neoforge:test` carve-out — ModDev legacy-classpath injection for plain-JUnit runs

- **Status**: Accepted
- **Date**: 2026-08-22 (back-dated to the day PR #52's follow-up `writeServerLegacyClasspath`
  injection landed; PR #52 itself merged 2026-08-21 and shipped the bare-JVM `:neoforge:test`
  target, the injection landed the same week)
- **Builds on**: nothing in-repo — this is the original carve-out the `:common:test`
  ADR-0031 sibling mirrors

## Context

Before PR #52, Burg had **no** `:neoforge:test` target at all. Tests that needed
to touch a class whose signature named a Minecraft type — `Town`, `RaidManager`,
the `BuiltInRegistries` helpers — either had to live in `:common:test` (forcing
a `:common`-runtime MC dep, which would violate ADR-0008 layering for
downstream consumers) or live under the `gametest` source set and only run on
the slow `runGameTestServer` path.

PR #52 introduced the bare-JVM `:neoforge:test` target: a Gradle `Test` task
with `useJUnitPlatform()` and a `testImplementation` block that ships the ModDev
merged JAR plus the four critical MC transitives Town's `<clinit>`` pulls in.
Reflection-only tests that just need the `Town` class loaded (e.g.
`TownStructuralFieldsTest`) work on that classpath alone.

The first `new Town()` is not a reflection-only test, though — it triggers
Town's static initializer, which transitively resolves:

- JOML (`org.joml.Vector3f` via MC's `ServerLevel` + `Vec3`)
- Netty (`io.netty.handler.codec.DecoderException` via MC's network stack,
  referenced by `ResourceLocation.<clinit>`)
- Mojang's logging facade (`com.mojang.logging.LogUtils`, referenced by
  `BlockPos.<clinit>`)
- the SLF4J binding Town uses for its own logger

The merged JAR plus the four explicitly listed `testImplementation` entries
still do not cover that transitive surface. Without further help, the first
`new Town()` in a clean checkout raises a `NoClassDefFoundError` chain and the
test JVM crashes with 31 failures — exactly the failure mode observed on the
PR #52 branch before the fix.

The fix was to wire ModDev's `writeServerLegacyClasspath` task into the test
task graph and inject the resulting JAR list at execution time. ModDev already
writes `${buildDir}/moddev/serverLegacyClasspath.txt` listing every JAR the
server-side MC test JVM needs (minus the loader-specific pieces — Mixin's
class transformer, ModLauncher, etc., which are intentionally absent because
`:neoforge:test` is a plain JUnit run, not a ModLauncher boot). ModDev only
triggers that writer transitively from `runServer` / `prepareServerRun`; on
a `:neoforge:test`-only invocation the file does not exist, so we have to
both `dependsOn` the writer and read its output before the test JVM forks.

## Decision

Pin the carve-out: `:neoforge:test` runs in a plain JUnit VM (no ModLauncher
boot, no `Bootstrap.run()`, no `@SubscribeEvent` scan) and pulls in the
ModDev-merged JAR plus four strict-version-pinned transitives plus the
ModDev-written legacy classpath JARs at execution time.

Concretely, `neoforge/build.gradle` ships:

- the ModDev merged JAR (`moddev/artifacts/neoforge-<version>-minecraft-merged.jar`)
- SLF4J (`slf4j-api` + `slf4j-simple` — Town's static logger binding)
- brigadier + datafixerupper + authlib (the MC transitive deps Town reaches
  via its imports; strict versions match `:common:test`'s so a future MC bump
  that changes a transitive version is caught here, not masked)
- JUnit 5 + Platform Launcher (the test framework; would be needed regardless)
- the ModDev `writeServerLegacyClasspath` output, injected at execution time

The injection is wired via `dependsOn 'writeServerLegacyClasspath'` on the
test task + a `doFirst` block that reads `${buildDir}/moddev/serverLegacyClasspath.txt`
line-by-line and appends every existing JAR path to the test task's
`ConfigurableFileCollection` `classpath` before the test JVM forks. The
writer is idempotent and `UP-TO-DATE` on subsequent runs; only the first
invocation on a clean checkout pays the cost (a few seconds, writing the
file). The carve targets `serverLegacyClasspath.txt` (not the `client` /
`data` / `gameTestServer` variants) because the wire-up uses server-side MC
types — `Town`'s static init pulls in `ServerLevel` + `Vec3`, both server-side.

The carve is scoped to `testImplementation` only — none of the JARs propagate
to `:neoforge`'s `main` or `apiElements` configurations, and none propagate
to other modules' classpaths. The `:neoforge` artifact a downstream consumer
links against stays Minecraft-free in its published surface; the MC types
only enter the test fork.

The reflection-only shape stays valid and cheaper; the carve applies only to
the rare pin that needs per-instance state. Tests that do not need `new Town()`
keep working on a lighter classpath (the base merged JAR + SLF4J alone, no
legacy classpath injection) — the `doFirst` is on the test task as a whole,
so it runs unconditionally, but the marginal cost for the reflection-only
tests is reading a text file and appending paths, not pulling additional
network or disk resources.

## Trade-offs

- **`:neoforge:test` no longer runs in isolation on a clean checkout.** Any
  teammate invoking `./gradlew :neoforge:test` on a clean checkout fails once
  before the legacy classpath file is generated. The `doFirst` error message
  explicitly tells the reader to run `./gradlew :neoforge:writeServerLegacyClasspath`
  first or invoke the task graph that pulls the writer transitively (e.g.
  `./gradlew :neoforge:test --rerun-tasks` after any merged-JAR refresh).
- **The `:neoforge:test` carve-out is real-Town-constructible, not just
  reflection-only.** This widens the surface area: behaviour-on-instance
  tests now exercise Town's `<clinit>` and therefore the SLF4J binding + log
  output. A refactor that touches Town's static init may break `:neoforge:test`
  in non-obvious ways (e.g. a new transitive MC dep). The strict-version pin
  on brigadier / datafixerupper / authlib catches a future MC bump that
  changes a transitive version here, rather than masking a runtime crash.
- **Full integration tests still belong in `runGameTestServer`.** The
  `:neoforge:test` target is bare-JUnit; it cannot drive a live `ServerLevel`,
  a tick loop, the MC scheduler, or a `@GameTestHolder` test. The
  `gametest` source set + the `runGameTestServer` task (configured in the
  same `neoforge/build.gradle`) are the path for those. The `:neoforge:test`
  target remains the home for cheap behaviour pins that need the static
  initialization chain but do not need a live server.
- **Test class loading still scans every test class.** The `useJUnitPlatform()`
  configuration with `showStandardStreams = true` means the test fork
  resolves every test class before deciding which `@Test` methods to run —
  a `Town`-side test added later is fine, but a test that depends on a
  `@SubscribeEvent` listener would silently fail at class-init (the JUnitUserDev
  bus, which ModDev's `unitTest { enable() }` block would have scanned for
  `@SubscribeEvent`, is intentionally **not** enabled here — see the
  `unitTest { }` comment block in `neoforge/build.gradle` for the rationale).

## Non-goals

- Flipping `:neoforge:test` to a full ModLauncher boot. The `runGameTestServer`
  task already serves that role; `:neoforge:test` is the bare-JUnit fast loop
  and stays focused on Town-side behaviour without a full server runtime.
- Pulling the legacy classpath into `:neoforge`'s main configuration. The
  carve is `testImplementation` only — it does not affect what downstream
  consumers link against.
- Refactoring `Town` to be more bare-JVM-friendly by moving `BlockPos` /
  `ResourceLocation` references behind a domain-level position type.
  Out of scope for this carve; the static-init chain is the cost of
  `Town`'s current shape, not of the test.
- Enabling ModDev's `unitTest { enable() }` block to use the
  `JUnitUserDev` bus for `@SubscribeEvent` listeners. The `:neoforge:test`
  target is bare-JUnit; the `gametest` source set + `runGameTestServer`
  task covers the live-server path.

## Consequences

- `:neoforge:test` runs in a plain JUnit VM, not a ModLauncher boot. 56 tests
  pass on `master` (the count grew over the post-#52 history; PR #52 itself
  shipped 9, the rest accumulated as Town-side behaviour pins landed).
- Reflection-only tests remain preferred where possible. The bare merged-JAR
  classpath without the legacy injection handles field/method introspection
  cheaply; the legacy classpath injection is needed only when the test
  triggers `new Town()` or another class whose `<clinit>` walks the full
  transitive chain.
- The sister ADR-0031 carve-out for `:common:test` mirrors this exact
  pattern: same merged JAR, same SLF4J, same strict-version pins on
  brigadier / datafixerupper / authlib, same `writeServerLegacyClasspath`
  `dependsOn` + `doFirst` injection. The two ADRs document the same shape
  applied to two different test targets; they are not redundant — each
  describes the discipline and trade-offs from the perspective of its own
  target.
- A refactor that drops one of the four strict-version-pinned transitives
  (e.g. brigadier) from `testImplementation` is a deliberate decision
  documented in a successor ADR. The strict versions are not opportunistic.

## Related

- `neoforge/build.gradle` — the `testImplementation` block + `dependsOn
  'writeServerLegacyClasspath'` + `doFirst` injection that implements the
  carve-out. The block-level comments name this ADR by ID.
- `common/build.gradle` — the sister carve-out for `:common:test`, mirroring
  the same shape (ADR-0031). The block-level comments there cross-reference
  this ADR as the originating pattern.
- `neoforge/src/test/java/.../TownStructuralFlagsRealDerivationsTest.java`
  — the most recent `new Town()`-driven pin on `:neoforge:test`, exercising
  Town-side mutators on the MC-aware classpath (Town helper + scheduler +
  full transitives). Sibling to `:common:test`'s
  `TownAddRoadSegmentFromPlannerTest` / `TownAddZoningFromPlannerTest`.
- `neoforge/src/test/java/.../NeoforgeTestHarness.java` — the test harness
  every `:neoforge:test` class extends. The `extends NeoforgeTestHarness`
  is the canonical entry point that triggers `Town`'s `<clinit>` so each
  test does not have to repeat the construction.
- `.github/workflows/gametest.yml` — runs `:neoforge:runGameTestServer`
  on every PR. The carve-out this ADR documents does **not** touch the
  gametest workflow; the workflow gates the `runGameTestServer` path,
  this ADR documents the cheap `:neoforge:test` fast loop that sits next
  to it.
- ADR-0031 — the sister carve-out for `:common:test`, mirroring this pattern
  with `new Town()` pins that exercise Town-side mutators and getters from
  the bare-JVM fast loop.
- ADR-0008 — DDD layering (`infrastructure` is the only Minecraft-aware
  layer; `people` and `domain` stay bare-JVM). The carve-out preserves
  this for downstream consumers: only `:neoforge:test`'s fork sees the
  legacy classpath.