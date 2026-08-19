# ADR-0021: Cloth Config API as the user-facing configuration surface

- **Status**: Accepted
- **Date**: 2026-08-19
- **Decided by**: owner (foundation carve request)

## Context

The mod already exposes runtime knobs to world datapacks (`BuilderConfig`,
`BuildingData`, `EraTransition`, `FoodList`, `Quest`, `SettlerJobs`,
`TradePrice`) and to the `Town` SavedData in NBT, but the player has no
in-game way to set anything. Now that act 4 is in flight (`hub-becomes-window`,
`burg-common.toml`-style values are about to matter for real), we need:

1. A user-editable config that the player can read and write from the Mods
   screen without touching the filesystem.
2. A foundation that future carves can add knobs to without re-architecting
   the config layer each time.
3. The same value to be reachable from the bare-JVM population simulation
   so the scale test under `:common:test` (the only fast feedback loop in a
   project whose verification bar is in-game) can exercise the new knob.

ADR-0008 set the layering: the `infrastructure/` layer is the only one that
may import Minecraft or NeoForge. The bare-JVM `people/` package must stay
free of those. ADR-0014 added an `application/` layer. The constraint that
the simulation runs on a bare JVM (the only fast loop) is not negotiable.

## Decision

Add **Cloth Config API 15.0.140 for NeoForge 1.21.1** as a runtime dep, and
carve one concrete knob — `villagerGrowthMultiplier` — as the foundation
the next carves can grow.

### Dependency

Cloth Config is **not on Maven Central**. The canonical source is
[`https://maven.shedaniel.me/`](https://maven.shedaniel.me/) (the
maintainer's own repo, where version `15.0.140` for MC `1.21(.1)` is the
latest; `16.x` targets `1.21.2+` and won't resolve against our
`neoforge_version = 21.1.77` and `minecraft_version = 1.21.1`).

Added to `buildSrc/.../multiloader-common.gradle` so both `:common` and
`:neoforge` see it:

- `:common` declares it `compileOnly` (the config class in
  `infrastructure/config/` references its types but the simulator does
  not).
- `:neoforge` declares it `runtimeOnly` so the JAR is bundled with the
  mod and the end user does not have to install Cloth separately. Also
  `compileOnly` so `OuatForgeClient` can use the `ConfigBuilder` API
  for the screen.

### Layering — `BurgConfig` in `infrastructure/`

The data side lives in
`common/src/main/java/org/lowern1ght/burg/infrastructure/config/BurgConfig.java`,
exposing a `ModConfigSpec.DoubleValue` (`net.neoforged.neoforge.common` —
**not** Minecraft client) plus a `static refreshMultiplier()` that pushes
the loaded value into a bare-JVM
`org.lowern1ght.burg.people.GrowthMultiplier` value object. The
`people/` package stays free of NeoForge (ADR-0008): the wire site in
`DaySim.tickDay` reads from `GrowthMultiplier.current().apply(candidates)`,
not from `BurgConfig`.

The screen side is a `me.shedaniel.clothconfig2.api.ConfigBuilder` in
`neoforge/src/.../OuatForgeClient.java` (it imports Minecraft client
classes, so it cannot live in `:common`). The Cloth screen's
`setSaveConsumer` updates the `ModConfigSpec` and the spec's reload
event re-fires `BurgConfig.refreshMultiplier()`, so a GUI save takes
effect on the next simulation tick without a world reload.

ADR-0008's "no `net.minecraft.*` in `domain/`" is preserved verbatim:
the config class is in `infrastructure/`, where the import is legal.

### Wire site — `DaySim.tickDay`

```java
int chances = Math.min(mothers.size(), fathers.size());
// ADR-0021: config-driven growth rate. User-tunable multiplier on the
// per-day candidate count, applied at the wire site so the bare-JVM
// simulation reads the same value the GUI writes.
chances = GrowthMultiplier.current().apply(chances);
```

The `GrowthMultiplier` value object clamps to `[0.5, 2.0]` on construction
(domain invariant) and floors `apply(int)` at `1` so a town never
deadlocks at "rounded down to zero births". `Math.max(1, candidates *
BurgConfig.villagerGrowthMultiplier)` is the goal's literal form; the
value-object form is one method call, no floating-point arithmetic at
the wire site, and the clamp + floor are reusable for the next knob.

### Test — `GrowthMultiplierTest.java`

The config class itself is not bare-JVM-testable: Cloth's
`ConfigBuilder` is `@Environment(EnvType.CLIENT)` and NeoForge's
`IConfigScreenFactory` lives in `net.neoforged.neoforge.client.gui`.
The value semantics — range, default, `apply(int)` floor, the static
`current()` slot — **are** testable on the bare JVM, and that is what
`GrowthMultiplierTest` pins. The pinned behaviours:

- Default = 1.0; `apply(n)` floors at 1.
- In-range values round-trip exactly.
- Out-of-range values clamp to the band, not reject.
- NaN / ±infinity are rejected (programmer error, not a config-file artifact).
- `current()` starts as `DEFAULT`, accepts an override, `resetCurrent()` restores.
- `equals` / `hashCode` follow the value.

`DomainPurityTest` still passes (`domain/` and `application/` remain
free of `net.minecraft.*` and `net.neoforged.*` imports — the rule is
unchanged).

## Consequences

- **Cloth ships to the end user as a bundled runtime dep.** No
  "download Cloth Config from CurseForge" instruction. The user installs
  Burg and gets the screen.
- **One knob today; the next carves are mechanical.** Adding
  `defBurstDamage` to the GUI is one field in `BurgConfig`, one
  `defineInRange` in the spec, one `startDoubleField` in the screen
  builder, and one `apply` method on a `BurstDamage` value object
  beside `GrowthMultiplier`. The structure is in place.
- **`people/` stays bare-JVM-testable.** The wire site reads from
  `GrowthMultiplier.current()`, not from a NeoForge type. The
  simulator's bare-JVM tests (the only fast feedback loop) continue to
  work; `DomainPurityTest` continues to pass.
- **Cloth's `ConfigBuilder` is loaded lazily on the client only.**
  The class itself is annotated `@Environment(EnvType.CLIENT)`; the
  server side of the world never class-loads it. No cost on dedicated
  servers.
- **Persistence is NeoForge's `ModConfigSpec`, not Cloth's.** Cloth's
  `setSaveRunnable` is a GUI callback, not a file format. The data side
  is the NightConfig-backed TOML file that NeoForge's
  `IConfigSpec` already writes and reads. Future carves can
  drop in `ModConfigSpec`-only fields (e.g. computed booleans the GUI
  derives from two scalar fields) without changing the format.

## Migration checklist for the next carve

- [ ] Add `<fieldName>Comment` + `<fieldName>Define` in
  `BurgConfig.SPEC`.
- [ ] Add a `static double <fieldName>()` accessor or
  `ConfigValue<T> get<FieldName>()` for the typed value.
- [ ] Add one entry in the `ConfigBuilder` in `OuatForgeClient` with
  `setMin`/`setMax` (if numeric) and `setTooltip` mirroring the spec
  comment.
- [ ] Push the value into a `people/` value object (clamp + apply
  method) in `refreshMultiplier()`. Do not let `people/` see
  `BurgConfig` directly — keep the import graph one-way
  (`infrastructure → people`).
- [ ] Bare-JVM test the value object; pin the clamp, default, and
  range.

## Related

- ADR-0008 — DDD layering (`infrastructure` is the only Minecraft-aware
  layer).
- ADR-0014 — `application/` layer.
- `~/.agents/skills/burg-buildings/SKILL.md` — 5 laws each measured over
  125 NBTs; the simulator is the reference, the GUI is decoration.
- [Cloth Config Forge wiki](https://shedaniel.gitbook.io/cloth-config/setup-cloth-config/setup-with-forge) — the `maven.shedaniel.me` repository and
  `me.shedaniel.cloth:cloth-config-forge:<version>` coordinates used
  here.
