package org.lowern1ght.burg.tick;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.behavior.road.RoadSegment;
import org.lowern1ght.burg.town.Town;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bare-JVM seam pin for the act-5 planner-population wire.
 *
 * <p>The act-5 follow-up to the structural-fields carve retires
 * {@link TickScheduler#tickZoning(Town, long)} and
 * {@link TickScheduler#tickRoadPlans(Town, long)} (replacing their bodies
 * with planner-driven output) and lets the per-town SoTs flip from
 * {@code empty} to {@code non-empty} once the production zoning layer /
 * road planner commit their first decision. As of PR #71 neither planner
 * is wired into a production call path, so this class pins the seam at
 * the bare-JVM (no MinecraftServer / no {@code new Town()}) layer the next
 * carver will land on.
 *
 * <p>This is the third leg of the planner-population seam pin (each
 * independently valuable; together they prove the seam holds):
 * <ol>
 *   <li>{@link TickSchedulerStructuralWireTest} — the helper signatures
 *       on {@link TickScheduler}: package-private, static, {@code boolean}
 *       return, two declared parameters. (also in {@code :common:test}).</li>
 *   <li>{@link org.lowern1ght.burg.town.TownStructuralFieldsTest} — the
 *       per-town SoT surfaces: {@code zoningCount} is private-final
 *       {@code EnumMap}, {@code plannedRoads} is private-final
 *       {@code List}, and the mutators
 *       {@link Town#addZoning(Town.Zone, int)} and
 *       {@link Town#addRoadSegment(RoadSegment)} are public-void with
 *       the documented shapes. (also in {@code :common:test}).</li>
 *   <li><b>This file.</b> The seam <em>as a whole</em>: the mutators and
 *       the helpers exist together, the {@link Town.Zone} taxonomy has
 *       every value the structural triple names, and the road-planner-side
 *       class {@code org.lowern1ght.burg.behavior.road.RoadBuilder} is
 *       FQCN-reachable without initialising it — the real planner is
 *       shipped and the seam awaits a production caller to feed it.</li>
 * </ol>
 *
 * <p>The {@code :neoforge:test} counterparts
 * ({@code TickSchedulerStructuralFlagsWireTest} and
 * {@code TownStructuralFlagsRealDerivationsTest}) exercise the mutator
 * behaviour on a real {@link Town} instance — the same seam exercised
 * live, where this class owns the static shape only. The two angles are
 * complements, not duplicates.
 *
 * <p><b>What the seam does NOT do yet.</b> As of PR #71 no production
 * caller wires an {@link org.lowern1ght.burg.behavior.intent.ExpandIntent}
 * through {@code RoadBuilder.planTasks(...)}, and no zoning-layer class
 * exists yet (a glob over any path matching
 * {@code *zoning&#47;*Planner*.java} returns no hits as of PR #71).
 * This file is the seam's <em>landing pad</em>: a next carve that
 * flips one of the assertions in
 * {@link TickSchedulerStructuralWireTest} /
 * {@code TickSchedulerStructuralFlagsWireTest} to {@code true} (or
 * replaces it with a positive observation) is the carve that retires
 * the no-op helpers and routes the real planner output through
 * {@link Town#addZoning} / {@link Town#addRoadSegment}.
 */
class PlannerPopulationSeamTest {

    // The road planner class is FQCN-named here on purpose: a no-MC classpath
    // would forbid importing it (ExpandIntent pulls in net.minecraft.server
    // transitively), but `Class.forName(name, false, loader)` resolves the
    // .class without triggering its static initialiser.
    private static final String ROAD_BUILDER_FQCN =
        "org.lowern1ght.burg.behavior.road.RoadBuilder";

    private static final String EXPAND_INTENT_FQCN =
        "org.lowern1ght.burg.behavior.intent.ExpandIntent";

    private static final String SERVER_LEVEL_FQCN =
        "net.minecraft.server.level.ServerLevel";

    @Test
    @DisplayName("the four seam surfaces co-exist — Town.addZoning, Town.addRoadSegment, TickScheduler.tickZoning, TickScheduler.tickRoadPlans")
    void allFourSeamSurfacesCoExist() throws Exception {
        Method townAddZoning = Town.class.getMethod("addZoning", Town.Zone.class, int.class);
        Method townAddRoadSegment = Town.class.getMethod("addRoadSegment", RoadSegment.class);
        Method tickZoning = TickScheduler.class.getDeclaredMethod("tickZoning", Town.class, long.class);
        Method tickRoadPlans = TickScheduler.class.getDeclaredMethod("tickRoadPlans", Town.class, long.class);

        assertAll(
            () -> assertNotNull(townAddZoning,
                "Town.addZoning(Zone, int) must exist — the zoning layer's writer"),
            () -> assertNotNull(townAddRoadSegment,
                "Town.addRoadSegment(RoadSegment) must exist — the road planner's writer"),
            () -> assertNotNull(tickZoning,
                "TickScheduler.tickZoning(Town, long) must exist as the seam waiting for the zoning layer"),
            () -> assertNotNull(tickRoadPlans,
                "TickScheduler.tickRoadPlans(Town, long) must exist as the seam waiting for the road planner"),
            () -> assertEquals(void.class, townAddZoning.getReturnType(),
                "Town.addZoning is void — merge-friendly semantic"),
            () -> assertEquals(void.class, townAddRoadSegment.getReturnType(),
                "Town.addRoadSegment is void — append-friendly semantic"),
            () -> assertEquals(boolean.class, tickZoning.getReturnType(),
                "TickScheduler.tickZoning returns boolean — same shape as tickRaids for a uniform markDirty wrap"),
            () -> assertEquals(boolean.class, tickRoadPlans.getReturnType(),
                "TickScheduler.tickRoadPlans returns boolean — same shape as tickRaids for a uniform markDirty wrap")
        );
    }

    @Test
    @DisplayName("Town.Zone taxonomy is the closed set the structural triple names — CORE, INDUSTRY, ROAD, MILITARY")
    void zoneEnumIsTheClosedStructuralTaxonomy() {
        Town.Zone[] expected = {
            Town.Zone.CORE,
            Town.Zone.INDUSTRY,
            Town.Zone.ROAD,
            Town.Zone.MILITARY
        };

        assertAll(
            () -> assertEquals(4, Town.Zone.values().length,
                "the Zone taxonomy is the closed set CORE/INDUSTRY/ROAD/MILITARY — exactly four entries"),
            () -> assertEquals(java.util.Arrays.asList(expected),
                java.util.Arrays.asList(Town.Zone.values()),
                "the Zone values are enumerated in CORE→INDUSTRY→ROAD→MILITARY order;"
                    + " renaming or reordering would shift enum ordinals and break the"
                    + " ordinal-keyed EnumMap reads the addZoning merge relies on")
        );
    }

    @Test
    @DisplayName("RoadBuilder is FQCN-reachable from the bare-JVM classloader — the real road planner is shipped, the seam awaits a production caller")
    void roadBuilderClassIsShipped() throws Exception {
        // Class.forName(name, false, loader) resolves the .class without
        // running its <clinit>. This matches TownCorePopulatedWalkTest's
        // bbFitsInRadius discipline.
        Class<?> roadBuilder = Class.forName(ROAD_BUILDER_FQCN, false,
            PlannerPopulationSeamTest.class.getClassLoader());

        assertAll(
            () -> assertNotNull(roadBuilder,
                "RoadBuilder class is shipped — the planner-side class a production"
                    + " road-planner commit path will route ExpandIntents through"),
            () -> assertTrue(Modifier.isPublic(roadBuilder.getModifiers())
                    && Modifier.isFinal(roadBuilder.getModifiers()),
                "RoadBuilder is the planner front door — public so the seam caller"
                    + " can reach it, final so the planner-side class cannot be"
                    + " silently subclassed into a shadow writer"),
            () -> assertDoesNotThrow(() -> roadBuilder.getDeclaredMethod("planTasks",
                    Class.forName(EXPAND_INTENT_FQCN, false,
                        PlannerPopulationSeamTest.class.getClassLoader()),
                    Town.class,
                    Class.forName(SERVER_LEVEL_FQCN, false,
                        PlannerPopulationSeamTest.class.getClassLoader())),
                "RoadBuilder.planTasks(ExpandIntent, Town, ServerLevel) exists — the"
                    + " real-planner commit entry point; the seam awaits a production caller")
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
