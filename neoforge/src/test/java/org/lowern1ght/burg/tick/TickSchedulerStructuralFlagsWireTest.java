package org.lowern1ght.burg.tick;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.behavior.road.RoadSegment;
import org.lowern1ght.burg.behavior.road.RoadType;
import org.lowern1ght.burg.domain.settlement.StructuralFlags;
import org.lowern1ght.burg.town.Town;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MC-aware behavior test for the structural-flags SoT wire-up helpers in
 * {@link TickScheduler}.
 *
 * <p>The act-5 follow-up to the structural-fields carve lands the first
 * increment on the per-town SoTs ({@code zoningCount} and
 * {@code plannedRoads}) from the production tick path so
 * {@link Town#structuralFlags()} flips from {@link StructuralFlags#NONE}
 * to non-{@code NONE} the moment a town ticks. The two helpers,
 * {@link TickScheduler#tickZoning(Town, long)} and
 * {@link TickScheduler#tickRoadPlans(Town, long)}, are the seam; this
 * test exercises them on a bare {@code new Town()} (no MinecraftServer
 * required) and asserts the gate's behavior end to end.
 *
 * <p><b>What this pins.</b> Four claims about the wire-up that the
 * production tick path relies on:
 *
 * <ol>
 *   <li><b>Fresh-town collapse.</b> A {@code new Town()} with no helper
 *       calls reports {@code structuralFlags() == NONE}, exactly the
 *       no-progress floor the act-4 follow-up landed.</li>
 *   <li><b>Zoning leg flips on the first call.</b>
 *       {@link TickScheduler#tickZoning(Town, long)} populates the
 *       zoningCount map (one cell, CORE zone); the helper returns
 *       {@code true} once. {@code structuralFlags().isAnySet()} flips
 *       to {@code true} and the gate moves off the NONE floor.</li>
 *   <li><b>Road leg flips on the first call.</b>
 *       {@link TickScheduler#tickRoadPlans(Town, long)} appends a
 *       one-cell segment at the origin; the helper returns
 *       {@code true} once. {@code structuralFlags().isAnySet()} is
 *       still {@code true} and the road_laid leg now contributes.</li>
 *   <li><b>Idempotent.</b> A second call to either helper returns
 *       {@code false} and does not mutate the SoT — repeated ticks
 *       from the production loop are no-ops once the first increment
 *       lands.</li>
 * </ol>
 *
 * <p>The bare-JVM signature pin lives in
 * {@code :common:test}'s {@code TickSchedulerStructuralWireTest}; the
 * gate's other derivations ({@code corePopulated()} from placed
 * buildings) are pinned by {@code :neoforge:test}'s
 * {@code TownStructuralFlagsRealDerivationsTest}.
 */
class TickSchedulerStructuralFlagsWireTest {

    @Test
    @DisplayName("fresh town — first tickZoning/tickRoadPlans calls land the SoT; subsequent calls are idempotent no-ops")
    void freshTownHelpersFireOnceThenAreNoOps() {
        Town town = new Town();

        assertAll(
            () -> assertTrue(TickScheduler.tickZoning(town, 0L),
                "first tickZoning call lands the first cell — returns true on the SoT write"),
            () -> assertEquals(Map.of(Town.Zone.CORE, 1), town.getZoningCount(),
                "after the first tickZoning call, getZoningCount observes the CORE=1 increment"),
            () -> assertFalse(TickScheduler.tickZoning(town, 0L),
                "second tickZoning call returns false — the helper is idempotent once"
                    + " zoningCount is non-empty"),
            () -> assertEquals(Map.of(Town.Zone.CORE, 1), town.getZoningCount(),
                "second tickZoning is a no-op — zoningCount is unchanged"),
            () -> assertTrue(TickScheduler.tickRoadPlans(town, 0L),
                "first tickRoadPlans call lands the first segment (returns true)"),
            () -> assertEquals(1, town.getPlannedRoads().size(),
                "after the first tickRoadPlans call, getPlannedRoads has exactly one segment"),
            () -> assertFalse(TickScheduler.tickRoadPlans(town, 0L),
                "second tickRoadPlans call returns false — the helper is idempotent once"
                    + " plannedRoads is non-empty"),
            () -> assertEquals(1, town.getPlannedRoads().size(),
                "second tickRoadPlans is a no-op — plannedRoads size is unchanged")
        );
    }

    @Test
    @DisplayName("fresh town before any helper call — structuralFlags collapses to NONE")
    void freshTownDerivesNone() {
        Town town = new Town();

        assertSame(StructuralFlags.NONE, town.structuralFlags(),
            "a fresh town (no zoning, no roads, no buildings) reports structuralFlags() == NONE;"
                + " the act-4 gate's structural leg is off the floor until the tick path lands"
                + " its first write");
    }

    @Test
    @DisplayName("after tickZoning — structuralFlags flips from NONE to non-NONE on the industry_zoned leg")
    void tickZoningFlipsStructuralFlags() {
        Town town = new Town();

        // Pre-condition: NONE floor
        assertSame(StructuralFlags.NONE, town.structuralFlags(),
            "before the helper call, the gate is on the NONE floor");

        assertTrue(TickScheduler.tickZoning(town, 0L),
            "the first tickZoning call lands the first cell — returns true");

        // Post-condition: structural flags flip on industry_zoned alone
        StructuralFlags observed = town.structuralFlags();
        assertAll(
            () -> assertNotEquals(StructuralFlags.NONE, observed,
                "after tickZoning, structuralFlags() is no longer NONE — the industry_zoned"
                    + " leg flipped, the gate's structural triple has a non-zero entry"),
            () -> assertTrue(observed.isAnySet(),
                "structuralFlags().isAnySet() flips to true on the first increment"),
            () -> assertTrue(observed.industryZoned(),
                "structuralFlags().industryZoned() is true — the zoningCount map is non-empty"),
            () -> assertFalse(observed.roadLaid(),
                "structuralFlags().roadLaid() is still false — only tickZoning ran; plannedRoads"
                    + " is still empty, the road leg is independent of the zoning leg"),
            () -> assertEquals(StructuralFlags.of(false, true, false), observed,
                "structuralFlags() returns the zoning-only partial shape — record-equality,"
                    + " industryZoned=true alone, the other two legs still on the floor")
        );
    }

    @Test
    @DisplayName("after tickRoadPlans — structuralFlags flips on the road_laid leg, distinct from industry_zoned")
    void tickRoadPlansFlipsStructuralFlags() {
        Town town = new Town();

        // Pre-condition: NONE floor
        assertSame(StructuralFlags.NONE, town.structuralFlags(),
            "before the helper call, the gate is on the NONE floor");

        assertTrue(TickScheduler.tickRoadPlans(town, 0L),
            "the first tickRoadPlans call lands the first segment — returns true");

        StructuralFlags observed = town.structuralFlags();
        assertAll(
            () -> assertNotEquals(StructuralFlags.NONE, observed,
                "after tickRoadPlans, structuralFlags() is no longer NONE — the road_laid leg"
                    + " flipped, the gate's structural triple has a non-zero entry"),
            () -> assertTrue(observed.isAnySet(),
                "structuralFlags().isAnySet() flips to true on the first append"),
            () -> assertFalse(observed.industryZoned(),
                "structuralFlags().industryZoned() is still false — only tickRoadPlans ran;"
                    + " zoningCount is still empty, the zoning leg is independent of the road leg"),
            () -> assertTrue(observed.roadLaid(),
                "structuralFlags().roadLaid() is true — the plannedRoads list is non-empty"),
            () -> assertEquals(StructuralFlags.of(false, false, true), observed,
                "structuralFlags() returns the road-only partial shape — record-equality,"
                    + " roadLaid=true alone, the other two legs still on the floor")
        );
    }

    @Test
    @DisplayName("tickZoning then tickRoadPlans — structuralFlags flips both industry_zoned and road_laid, gate is fully open on the structural triple")
    void bothHelpersTogetherOpenBothLegs() {
        Town town = new Town();

        TickScheduler.tickZoning(town, 0L);
        TickScheduler.tickRoadPlans(town, 0L);

        StructuralFlags observed = town.structuralFlags();
        assertAll(
            () -> assertTrue(observed.industryZoned(),
                "industry_zoned is true — tickZoning ran first"),
            () -> assertTrue(observed.roadLaid(),
                "road_laid is true — tickRoadPlans ran second"),
            () -> assertFalse(observed.corePopulated(),
                "core_populated is still false — no buildings have been placed, the real-derivation"
                    + " leg is independent of the SoT-write legs"),
            () -> assertTrue(observed.isAnySet(),
                "structuralFlags().isAnySet() is true — the gate's permissive form fires once"
                    + " any leg is set"),
            () -> assertEquals(StructuralFlags.of(false, true, true), observed,
                "structuralFlags() returns the zoning+road partial shape — both industry_zoned"
                    + " and road_laid are true, core_populated still false, the gate's structural"
                    + " triple has two non-zero entries")
        );
    }

    @Test
    @DisplayName("the road segment the helper lands is a one-cell STREET at BlockPos.ZERO — the minimal valid segment")
    void firstSegmentShape() {
        Town town = new Town();

        TickScheduler.tickRoadPlans(town, 0L);

        RoadSegment first = town.getPlannedRoads().get(0);
        assertAll(
            () -> assertNotNull(first, "the first segment is non-null"),
            () -> assertEquals(BlockPos.ZERO, first.start(),
                "start is BlockPos.ZERO — the minimal coordinate"),
            () -> assertEquals(BlockPos.ZERO, first.end(),
                "end is BlockPos.ZERO — the single-cell segment degenerates to a point"),
            () -> assertEquals(List.of(BlockPos.ZERO), first.waypoints(),
                "waypoints is a single-element list with BlockPos.ZERO — defensively copied"
                    + " via List.copyOf so the helper can't accidentally share state with the"
                    + " planner's internal lists"),
            () -> assertSame(RoadType.STREET, first.type(),
                "type is STREET — the minimal default for non-water terrain; the production"
                    + " road planner will replace this with the real classified type")
        );
    }
}
