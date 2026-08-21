package org.lowern1ght.burg.tick;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.town.Town;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Signature pin for the structural-flags SoT wire-up helpers in
 * {@link TickScheduler}.
 *
 * <p>The act-5 follow-up to the structural-fields carve lands the first
 * increment on the per-town SoTs ({@code zoningCount} and
 * {@code plannedRoads}) from the production tick path so
 * {@code Town#structuralFlags()} flips from NONE to non-NONE the moment
 * a town ticks. The wiring lives in two static helpers,
 * {@link TickScheduler#tickZoning(Town, long)} and
 * {@link TickScheduler#tickRoadPlans(Town, long)}, which
 * {@code TickScheduler.tick(MinecraftServer)} invokes once per town per
 * tick and which the {@code :neoforge:test} behavioral test exercises
 * directly.
 *
 * <p>Both helpers are package-private (no {@code public} modifier) on
 * purpose: they exist as the seam the production tick path reads, not as
 * a public API surface for outside callers — the mutators
 * ({@code Town.addZoning}, {@code Town.addRoadSegment}) are the public
 * read/write surface, and the helpers just stamp the first write through
 * from inside the package. This test pins that visibility and the
 * shape of the helpers' signatures so a future refactor cannot
 * silently change the seam.
 *
 * <p>What this pins (the discipline that makes the strict
 * {@code Town#structuralFlags()} derivation collapse to
 * {@code StructuralFlags#NONE} on every fresh save flip the moment the
 * production tick path calls the helpers):
 * <ol>
 *   <li>{@code TickScheduler.tickZoning(Town, long)} is the first zoning
 *       write site from inside the package. It is package-private,
 *       {@code boolean}-returning, and takes a {@link Town} plus a
 *       {@code long} {@code gameTime}.</li>
 *   <li>{@code TickScheduler.tickRoadPlans(Town, long)} is the first road
 *       write site from inside the package. It is package-private,
 *       {@code boolean}-returning, and takes a {@link Town} plus a
 *       {@code long} {@code gameTime}.</li>
 *   <li>The helpers are called from {@code TickScheduler.tick} as the
 *       next step after the {@code tickRaids} wire — the seam is in
 *       place for the act-5 zoning / road-planner carves to plug
 *       planner-driven output in without touching the call site.</li>
 * </ol>
 */
class TickSchedulerStructuralWireTest {

    @Test
    @DisplayName("TickScheduler.tickZoning(Town, long) is the SoT-write helper — package-private, boolean, two declared parameters")
    void tickZoningSignatureIsRight() throws Exception {
        Method helper = TickScheduler.class.getDeclaredMethod("tickZoning", Town.class, long.class);

        assertNotNull(helper, "tickZoning must exist on TickScheduler");
        assertAll(
            () -> assertTrue(isPackagePrivate(helper.getModifiers()),
                "tickZoning is package-private — the structural-flags seam, not a public API surface;"
                    + " callers inside org.lowern1ght.burg.tick (the production loop,"
                    + " :neoforge:test) reach it directly"),
            () -> assertTrue(Modifier.isStatic(helper.getModifiers()),
                "tickZoning is static — the same shape tickRaids uses, so :neoforge:test can"
                    + " call it without a MinecraftServer or a BehaviorEngine instance"),
            () -> assertEquals(boolean.class, helper.getReturnType(),
                "tickZoning returns boolean — true iff the first zoning increment landed on this"
                    + " call, so the caller (TickScheduler.tick) can wrap with LevelTowns.markDirty()"),
            () -> assertEquals(2, helper.getParameterCount(),
                "tickZoning takes exactly two parameters (Town town, long gameTime)"),
            () -> assertEquals(Town.class, helper.getParameterTypes()[0],
                "first parameter is Town — the per-town facade the helper mutates"),
            () -> assertEquals(long.class, helper.getParameterTypes()[1],
                "second parameter is long — the current gameTime, kept in the signature for"
                    + " parity with tickRaids and for future rate-limiting decisions")
        );
    }

    @Test
    @DisplayName("TickScheduler.tickRoadPlans(Town, long) is the SoT-write helper — package-private, boolean, two declared parameters")
    void tickRoadPlansSignatureIsRight() throws Exception {
        Method helper = TickScheduler.class.getDeclaredMethod("tickRoadPlans", Town.class, long.class);

        assertNotNull(helper, "tickRoadPlans must exist on TickScheduler");
        assertAll(
            () -> assertTrue(isPackagePrivate(helper.getModifiers()),
                "tickRoadPlans is package-private — the structural-flags seam, not a public API surface"),
            () -> assertTrue(Modifier.isStatic(helper.getModifiers()),
                "tickRoadPlans is static — the same shape tickRaids uses, so :neoforge:test can"
                    + " call it without a MinecraftServer"),
            () -> assertEquals(boolean.class, helper.getReturnType(),
                "tickRoadPlans returns boolean — true iff the first segment landed on this call"),
            () -> assertEquals(2, helper.getParameterCount(),
                "tickRoadPlans takes exactly two parameters (Town town, long gameTime)"),
            () -> assertEquals(Town.class, helper.getParameterTypes()[0],
                "first parameter is Town — the per-town facade the helper mutates"),
            () -> assertEquals(long.class, helper.getParameterTypes()[1],
                "second parameter is long — the current gameTime, kept in the signature for parity"
                    + " with tickRaids and for future rate-limiting decisions")
        );
    }

    @Test
    @DisplayName("the three structural-flags wire-up helpers coexist on TickScheduler — tickRaids, tickZoning, tickRoadPlans")
    void allThreeWireHelpersCoexist() throws Exception {
        // The seam is the three helpers together: tickRaids lands the act-4 fire tick,
        // tickZoning + tickRoadPlans land the structural SoTs. The carve replaces the
        // NONE-floor with a real write site; if a future refactor drops any of the three,
        // this assertion breaks and points at the missing seam.
        Method raids = TickScheduler.class.getDeclaredMethod("tickRaids", Town.class, long.class);
        Method zoning = TickScheduler.class.getDeclaredMethod("tickZoning", Town.class, long.class);
        Method roads = TickScheduler.class.getDeclaredMethod("tickRoadPlans", Town.class, long.class);

        assertAll(
            () -> assertNotNull(raids, "tickRaids is the pre-existing wire helper — must still be there"),
            () -> assertNotNull(zoning, "tickZoning is the new wire helper — must be declared alongside tickRaids"),
            () -> assertNotNull(roads, "tickRoadPlans is the new wire helper — must be declared alongside tickRaids"),
            () -> assertEquals(boolean.class, raids.getReturnType(),
                "tickRaids returns boolean — same shape as the new helpers, so the call site can"
                    + " wrap them uniformly with LevelTowns.markDirty()")
        );
    }

    // True iff the modifier set has no public/protected/private bit set —
    // i.e. package-private (the default visibility in Java). Equivalent
    // to `Modifier.isPackagePrivate(modifiers)` (Java 9+), reproduced
    // here so the test class compiles against the project's pinned JDK.
    private static boolean isPackagePrivate(int mods) {
        return (mods & (Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE)) == 0;
    }
}
