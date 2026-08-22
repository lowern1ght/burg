package org.lowern1ght.burg.tick;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.settlement.StructuralFlags;
import org.lowern1ght.burg.town.Town;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MC-aware behavioural pin for the structural-flags SoT after the
 * planner-population carve. The {@link TickScheduler} side of the
 * structural-flags wire now ships two <em>no-op stubs</em>:
 * {@link TickScheduler#tickZoning(Town, long)} and
 * {@link TickScheduler#tickRoadPlans(Town, long)}. Their bodies are
 * {@code return false;} on every call — the synthetic first-increment
 * writes that previously flipped {@link Town#structuralFlags()} from
 * {@link StructuralFlags#NONE} to non-{@code NONE} on every tick have
 * been removed. The method signatures are preserved (package-private,
 * static, {@code boolean}, {@code (Town, long)}) so the seam the
 * (future) production zoning layer / road planner wire into is already
 * in place — the next carve just replaces the helper bodies with
 * planner-driven output.
 *
 * <p>This file pins the post-carve contract at three layers:
 * <ol>
 *   <li><b>TickScheduler helpers are no-op stubs.</b> Reflection on the
 *       class confirms {@code tickZoning} and {@code tickRoadPlans}
 *       still exist (the seam is preserved), and a direct call on a
 *       fresh town returns {@code false} without mutating the SoT. A
 *       regression that either re-introduced the synthetic write or
 *       removed the method signature would break this pin.</li>
 *   <li><b>Town-side mutator semantics drive the gate.</b> A direct
 *       {@link Town#addZoning(Town.Zone, int)} call on a fresh town
 *       flips the {@code industryZoned} leg of
 *       {@link Town#structuralFlags()} from {@code false} to
 *       {@code true} — the structural triple's permissive leg fires
 *       the moment the (future) zoning layer emits its first
 *       decision.</li>
 *   <li><b>TickScheduler.tickRaids is the only remaining structural
 *       flip helper.</b> Reflection on {@code tickRaids} pins its
 *       signature (package-private, static, {@code boolean},
 *       {@code (Town, long)}); this is the seam the raid-cadence wire
 *       reaches from {@code :neoforge:test} without a
 *       {@code MinecraftServer}, untouched by the carve.</li>
 * </ol>
 *
 * <p>The cheap {@code :common:test} targets' wire tests pin the
 * bare-JVM view of the seam ({@link TickSchedulerStructuralWireTest},
 * {@code PlannerPopulationSeamTest}); the MC-aware per-helper no-op
 * pins live in {@code :neoforge:test}'s
 * {@link TickSchedulerStructuralFlagsWireTest} and
 * {@link TickSchedulerStructuralFlagsPostTickNoneTest}. The Town-side
 * mutator pins live in
 * {@code TownStructuralFlagsRealDerivationsTest} (same source set).
 * This file is the {@code :neoforge:test} leg that ties the
 * TickScheduler-side stub contract to the Town-side mutator contract
 * at the seam the planner-population carve exposed.
 */
class TickSchedulerStructuralFlagsPlannerPinTest {

    // ---------------------------------------------------------------------
    // TickScheduler side — synthetic helpers are no-op stubs
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("TickScheduler.tickZoning(Town, long) exists as a no-op stub — the signature is preserved so the future zoning layer's seam is already in place")
    void tickZoningIsNoOpStub() {
        // Reflection confirms the seam is preserved — the helper is still on the class,
        // package-private + static + boolean return + (Town, long) parameters.
        Method helper = findDeclaredMethod(TickScheduler.class, "tickZoning", Town.class, long.class);
        assertAll(
            () -> assertNotNull(helper,
                "tickZoning must still exist on TickScheduler — the seam the (future) zoning"
                    + " layer wires into is preserved; removing it would force the next carve"
                    + " to re-introduce both the method and the call site"),
            () -> assertTrue(isPackagePrivate(helper.getModifiers()),
                "tickZoning is package-private — same visibility as tickRaids, the MC-aware"
                    + " test seam"),
            () -> assertTrue(Modifier.isStatic(helper.getModifiers()),
                "tickZoning is static — :neoforge:test calls it without a TickScheduler"
                    + " instance, just like tickRaids"),
            () -> assertEquals(boolean.class, helper.getReturnType(),
                "tickZoning returns boolean — the dirty-mark contract; the no-op body always"
                    + " returns false"),
            () -> assertEquals(2, helper.getParameterCount(),
                "tickZoning takes exactly two parameters (Town town, long gameTime)"),
            () -> assertEquals(Town.class, helper.getParameterTypes()[0],
                "first parameter is Town — the per-town facade the helper reads"),
            () -> assertEquals(long.class, helper.getParameterTypes()[1],
                "second parameter is long — the current gameTime")
        );

        // Behaviour: the body is `return false;` on every call. The structural SoT
        // stays on the empty-map floor — no synthetic addZoning(CORE, 1) write races
        // the (future) production zoning layer.
        Town town = new Town();
        assertAll(
            () -> assertFalse(TickScheduler.tickZoning(town, 0L),
                "tickZoning(town, 0L) returns false — the no-op stub body is `return false;`;"
                    + " the caller's `if (tickZoning(...)) LevelTowns.markDirty()` branch is"
                    + " never taken"),
            () -> assertEquals(0, town.getZoningCount().size(),
                "after tickZoning, getZoningCount is empty — the no-op stub never calls"
                    + " Town.addZoning; the structural SoT stays on the empty-map floor"),
            () -> assertSame(StructuralFlags.NONE, town.structuralFlags(),
                "structuralFlags() stays at NONE — the no-op stub never flips the"
                    + " industry_zoned leg; the gate's structural triple stays at the"
                    + " no-progress floor"),
            () -> assertFalse(TickScheduler.tickZoning(town, 0L),
                "a second tickZoning call also returns false — the helper is no-op on every"
                    + " call, not just the first; idempotence is implicit because there is"
                    + " no write to be undone")
        );
    }

    @Test
    @DisplayName("TickScheduler.tickRoadPlans(Town, long) exists as a no-op stub — the signature is preserved so the road planner's seam is already in place")
    void tickRoadPlansIsNoOpStub() {
        // Reflection confirms the seam is preserved — the helper is still on the class,
        // package-private + static + boolean return + (Town, long) parameters.
        Method helper = findDeclaredMethod(TickScheduler.class, "tickRoadPlans", Town.class, long.class);
        assertAll(
            () -> assertNotNull(helper,
                "tickRoadPlans must still exist on TickScheduler — the seam the (future)"
                    + " road planner wires into is preserved; removing it would force the"
                    + " next carve to re-introduce both the method and the call site"),
            () -> assertTrue(isPackagePrivate(helper.getModifiers()),
                "tickRoadPlans is package-private — same visibility as tickRaids, the"
                    + " MC-aware test seam"),
            () -> assertTrue(Modifier.isStatic(helper.getModifiers()),
                "tickRoadPlans is static — :neoforge:test calls it without a TickScheduler"
                    + " instance, just like tickRaids"),
            () -> assertEquals(boolean.class, helper.getReturnType(),
                "tickRoadPlans returns boolean — the dirty-mark contract; the no-op body"
                    + " always returns false"),
            () -> assertEquals(2, helper.getParameterCount(),
                "tickRoadPlans takes exactly two parameters (Town town, long gameTime)"),
            () -> assertEquals(Town.class, helper.getParameterTypes()[0],
                "first parameter is Town — the per-town facade the helper reads"),
            () -> assertEquals(long.class, helper.getParameterTypes()[1],
                "second parameter is long — the current gameTime")
        );

        // Behaviour: the body is `return false;` on every call. The structural SoT
        // stays on the empty-list floor — no synthetic one-cell segment at
        // BlockPos.ZERO races the (future) production road planner's commit path.
        Town town = new Town();
        assertAll(
            () -> assertFalse(TickScheduler.tickRoadPlans(town, 0L),
                "tickRoadPlans(town, 0L) returns false — the no-op stub body is `return"
                    + " false;`; the caller's `if (tickRoadPlans(...)) LevelTowns.markDirty()`"
                    + " branch is never taken"),
            () -> assertEquals(0, town.getPlannedRoads().size(),
                "after tickRoadPlans, getPlannedRoads is empty — the no-op stub never"
                    + " calls Town.addRoadSegment; the structural SoT stays on the"
                    + " empty-list floor"),
            () -> assertSame(StructuralFlags.NONE, town.structuralFlags(),
                "structuralFlags() stays at NONE — the no-op stub never flips the"
                    + " road_laid leg; the gate's structural triple stays at the"
                    + " no-progress floor"),
            () -> assertFalse(TickScheduler.tickRoadPlans(town, 0L),
                "a second tickRoadPlans call also returns false — the helper is no-op on"
                    + " every call, not just the first; idempotence is implicit because"
                    + " there is no write to be undone")
        );
    }

    @Test
    @DisplayName("TickScheduler.tickRaids(Town, long) is the only remaining structural-flip helper — unchanged by the carve")
    void tickRaidsSignatureIsRight() throws Exception {
        Method helper = TickScheduler.class.getDeclaredMethod("tickRaids", Town.class, long.class);

        assertAll(
            () -> assertTrue(isPackagePrivate(helper.getModifiers()),
                "tickRaids is package-private — the raid-cadence seam, untouched by the"
                    + " planner-population carve"),
            () -> assertTrue(Modifier.isStatic(helper.getModifiers()),
                "tickRaids is static — the shape :neoforge:test can call without"
                    + " a MinecraftServer"),
            () -> assertEquals(boolean.class, helper.getReturnType(),
                "tickRaids returns boolean — the cooldown-gated fire decision"),
            () -> assertEquals(2, helper.getParameterCount(),
                "tickRaids takes exactly two parameters (Town town, long gameTime)"),
            () -> assertEquals(Town.class, helper.getParameterTypes()[0],
                "first parameter is Town — the per-town facade the helper reads"),
            () -> assertEquals(long.class, helper.getParameterTypes()[1],
                "second parameter is long — the current gameTime")
        );
    }

    @Test
    @DisplayName("Town-side seam: addZoning(Zone.CORE, 5) flips industryZoned() from false to true — the zoning layer's mutator is the only sanctioned writer")
    void addZoningFlipsIndustryZonedOnTown() {
        // Pre-condition: NONE floor — the (future) zoning layer has not committed yet.
        Town town = new Town();
        assertSame(StructuralFlags.NONE, town.structuralFlags(),
            "fresh town has structuralFlags() == NONE — no zoning-layer commit, the"
                + " industry_zoned leg of the structural triple is on the floor");

        // The (future) zoning layer emits a CORE increment of 5 cells. The mutator
        // merges into the per-zone EnumMap; the structural gate's permissive
        // industry_zoned derivation fires on the next structuralFlags() read.
        town.addZoning(Town.Zone.CORE, 5);

        assertAll(
            () -> assertEquals(java.util.Map.of(Town.Zone.CORE, 5), town.getZoningCount(),
                "addZoning(Zone.CORE, 5) lands the singleton CORE entry — the merge"
                    + " semantics see exactly one zone with exactly 5 cells; the"
                    + " EnumMap's view through getZoningCount matches the wire shape"),
            () -> assertTrue(town.structuralFlags().industryZoned(),
                "structuralFlags().industryZoned() flips true — the structural triple's"
                    + " permissive leg fires the moment any zoning decision lands,"
                    + " regardless of which zone the layer chose; CORE counts because"
                    + " industryZoned() reads map emptiness, not the INDUSTRY entry"),
            () -> assertFalse(town.structuralFlags().roadLaid(),
                "structuralFlags().roadLaid() stays false — the zoning-layer mutator"
                    + " does not cross-pollute the road_laid leg; the two legs are"
                    + " independent writers"),
            () -> assertEquals(StructuralFlags.of(false, true, false), town.structuralFlags(),
                "structuralFlags() returns the zoning-only partial — industry_zoned=true"
                    + " alone, corePopulated real-derivation false (no buildings),"
                    + " roadLaid false; non-NONE shapes compare by record equality")
        );
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    // Returns the declared method with the given name and parameter types,
    // or null if no such method exists. This is the absence-safe variant
    // of Class#getDeclaredMethod, which throws NoSuchMethodException on miss.
    private static Method findDeclaredMethod(Class<?> klass, String name, Class<?>... paramTypes) {
        for (Method m : klass.getDeclaredMethods()) {
            if (!m.getName().equals(name)) continue;
            Class<?>[] declared = m.getParameterTypes();
            if (declared.length != paramTypes.length) continue;
            boolean same = true;
            for (int i = 0; i < declared.length; i++) {
                if (!declared[i].equals(paramTypes[i])) { same = false; break; }
            }
            if (same) return m;
        }
        return null;
    }

    // True iff the modifier set has no public/protected/private bit set —
    // i.e. package-private (the default visibility in Java).
    private static boolean isPackagePrivate(int mods) {
        return (mods & (Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE)) == 0;
    }
}