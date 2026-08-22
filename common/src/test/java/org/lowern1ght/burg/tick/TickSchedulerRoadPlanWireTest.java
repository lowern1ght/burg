package org.lowern1ght.burg.tick;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.behavior.road.RoadPlanSource;
import org.lowern1ght.burg.behavior.road.RoadSegment;
import org.lowern1ght.burg.behavior.road.RoadType;
import org.lowern1ght.burg.domain.settlement.StructuralFlags;
import org.lowern1ght.burg.town.Town;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bare-JVM behaviour pin for the {@link TickScheduler#tickRoadPlans(Town, long)}
 * wire-up. Proves the helper routes a {@link RoadPlanSource}'s output
 * through {@link Town#addRoadSegment(RoadSegment)} end-to-end, with the
 * source installed via the package-private
 * {@link TickScheduler#setRoadPlanSource(RoadPlanSource)} setter.
 *
 * <p>This file is the third leg of the planner-population wire pin (each
 * independently valuable; together they prove the seam holds):
 * <ol>
 *   <li>{@link TickSchedulerStructuralWireTest} — the helper signature on
 *       {@link TickScheduler}: package-private, static, {@code boolean}
 *       return, two declared parameters.</li>
 *   <li>{@link org.lowern1ght.burg.tick.PlannerPopulationSeamTest} — the
 *       four seam surfaces co-exist (mutators + helpers + the
 *       {@code RoadBuilder} FQCN) and the planner-side class is shipped.</li>
 *   <li><b>This file.</b> The helper's <em>wired behaviour</em>: with a
 *       source that returns segments, those segments land on
 *       {@link Town#getPlannedRoads()} and flip the
 *       {@link StructuralFlags#roadLaid()} leg of the structural triple;
 *       with a source that returns nothing, the helper is the
 *       {@link RoadPlanSource#NONE} default (no SoT mutation, returns
 *       {@code false}).</li>
 * </ol>
 *
 * <p>The {@code :neoforge:test} counterpart
 * ({@code RoadPlanTickGameTest}) exercises the same wire-up on a real MC
 * server with a real {@code RoadBuilder} + {@code ExpandIntent}. This
 * file owns the bare-JVM view; the gametest owns the live-server view.
 *
 * <p><b>Why a source-level test, not a planner-level test.</b> The
 * planner-level test ({@code PathLayerGameTest}) already pins the
 * {@code RoadBuilder.planTasks(ExpandIntent, Town, ServerLevel) →
 * List<RoadTask>} shape. The carve this file pins is the seam above
 * the planner: the {@code TickScheduler.tickRoadPlans} helper delegates
 * to a {@link RoadPlanSource}, and that source's output flows into the
 * Town SoT via {@code addRoadSegment}. The unit under test is the
 * helper's wire-up, not the planner's A*.
 *
 * <p><b>Cleanup discipline.</b> {@link AfterEach} resets the installed
 * source back to {@code null} so a run with multiple {@code @Test}
 * methods does not leak a fake source across cases. A missing reset is
 * loud (next test's assertion on the {@code NONE} default would fail).
 */
class TickSchedulerRoadPlanWireTest {

    @AfterEach
    void resetRoadPlanSource() {
        // Reset to default after every test so a leak from one test does
        // not poison the next. Mirrors TickSchedulerQuestTickPortTest's
        // reflection-driven REGISTRY.clear() discipline.
        TickScheduler.setRoadPlanSource(null);
    }

    /**
     * Build a {@link RoadSegment} for a fake source to emit. Two endpoints,
     * a single-cell waypoint, classified as {@link RoadType#STREET} (the
     * cheapest road type the planner emits on flat terrain). Pure data;
     * the planner's A* is not invoked.
     */
    private static RoadSegment fakeSegment(long cellIndex) {
        BlockPos start = new BlockPos((int) cellIndex, 1, 0);
        BlockPos end = new BlockPos((int) cellIndex + 1, 1, 0);
        return new RoadSegment(start, end, List.of(start, end), RoadType.STREET);
    }

    @Test
    @DisplayName("with a fake source returning one segment — tickRoadPlans lands the segment on the SoT; getPlannedRoads size is 1")
    void fakeSourceOneSegmentLandsOnSot() {
        Town town = new Town();
        RoadSegment expected = fakeSegment(0L);

        TickScheduler.setRoadPlanSource((t, gameTime) -> List.of(expected));

        boolean changed = TickScheduler.tickRoadPlans(town, 0L);

        assertAll(
            () -> assertTrue(changed,
                "tickRoadPlans returns true when at least one segment lands — the caller's"
                    + " `if (tickRoadPlans(...)) LevelTowns.markDirty()` branch must take the"
                    + " dirty-mark path"),
            () -> assertEquals(1, town.getPlannedRoads().size(),
                "after tickRoadPlans, getPlannedRoads has exactly one segment — the source's"
                    + " single emit landed on the SoT via Town.addRoadSegment"),
            () -> assertSame(expected, town.getPlannedRoads().get(0),
                "the segment on the SoT is the same instance the source emitted —"
                    + " addRoadSegment is append-friendly and does not defensive-copy")
        );
    }

    @Test
    @DisplayName("with a fake source returning a multi-segment list — all segments land in emission order")
    void fakeSourceMultipleSegmentsLandInOrder() {
        Town town = new Town();
        RoadSegment a = fakeSegment(0L);
        RoadSegment b = fakeSegment(10L);
        RoadSegment c = fakeSegment(20L);

        TickScheduler.setRoadPlanSource((t, gameTime) -> List.of(a, b, c));

        boolean changed = TickScheduler.tickRoadPlans(town, 0L);

        assertAll(
            () -> assertTrue(changed,
                "tickRoadPlans returns true — three segments landed"),
            () -> assertEquals(3, town.getPlannedRoads().size(),
                "all three emitted segments are on the SoT in emission order"),
            () -> assertSame(a, town.getPlannedRoads().get(0),
                "first segment is the source's first emit"),
            () -> assertSame(b, town.getPlannedRoads().get(1),
                "second segment is the source's second emit"),
            () -> assertSame(c, town.getPlannedRoads().get(2),
                "third segment is the source's third emit")
        );
    }

    @Test
    @DisplayName("with a fake source returning an empty list — structural flag stays NONE; the helper returns false")
    void emptySourceKeepsStructuralFlagOnFloor() {
        Town town = new Town();

        // Pre-condition: NONE floor before the helper runs.
        assertSame(StructuralFlags.NONE, town.structuralFlags(),
            "before the helper, structuralFlags() is NONE — the empty-source path must"
                + " preserve the floor");

        TickScheduler.setRoadPlanSource((t, gameTime) -> List.of());

        boolean changed = TickScheduler.tickRoadPlans(town, 0L);

        assertAll(
            () -> assertFalse(changed,
                "tickRoadPlans returns false on an empty source — no SoT mutation, no"
                    + " dirty mark; the caller's `if (tickRoadPlans(...))` branch is"
                    + " not taken"),
            () -> assertEquals(0, town.getPlannedRoads().size(),
                "getPlannedRoads stays empty — the empty-source path does not append a"
                    + " synthetic segment at BlockPos.ZERO (the legacy mistake this carve"
                    + " retired)"),
            () -> assertSame(StructuralFlags.NONE, town.structuralFlags(),
                "structuralFlags() stays NONE — the road_laid leg stays on the floor"
                    + " because no segment landed"),
            () -> assertFalse(town.structuralFlags().roadLaid(),
                "structuralFlags().roadLaid() is false — empty source, no planned roads,"
                    + " the leg of the structural triple stays on the no-progress floor"),
            () -> assertFalse(town.structuralFlags().industryZoned(),
                "structuralFlags().industryZoned() is still false — the road planner does"
                    + " not cross-pollute the zoning leg; the legs are independent writers")
        );
    }

    @Test
    @DisplayName("with a fake source returning null — structural flag stays NONE; the helper tolerates null as empty")
    void nullSourceReturnTreatedAsEmpty() {
        Town town = new Town();

        TickScheduler.setRoadPlanSource((t, gameTime) -> null);

        boolean changed = TickScheduler.tickRoadPlans(town, 0L);

        assertAll(
            () -> assertFalse(changed,
                "tickRoadPlans returns false on a null source return — null is treated as"
                    + " the empty-list no-op path; the helper does not NPE on the iteration"),
            () -> assertEquals(0, town.getPlannedRoads().size(),
                "getPlannedRoads stays empty — null return is the empty path"),
            () -> assertSame(StructuralFlags.NONE, town.structuralFlags(),
                "structuralFlags() stays NONE — null return is the no-mutation path")
        );
    }

    @Test
    @DisplayName("with no source installed — the helper uses RoadPlanSource.NONE default; no SoT mutation; returns false")
    void noSourceInstalledUsesNoneDefault() {
        Town town = new Town();

        // Pre-condition: source field starts at null (the AfterEach reset, plus the
        // class-load default). The helper must resolve null → RoadPlanSource.NONE.
        boolean changed = TickScheduler.tickRoadPlans(town, 0L);

        assertAll(
            () -> assertFalse(changed,
                "tickRoadPlans returns false when no source is installed — the helper"
                    + " resolves null to RoadPlanSource.NONE; the caller skips its"
                    + " dirty-mark branch"),
            () -> assertEquals(0, town.getPlannedRoads().size(),
                "getPlannedRoads stays empty when no source is installed — the NONE"
                    + " default returns an empty list; the structural SoT is untouched"),
            () -> assertSame(StructuralFlags.NONE, town.structuralFlags(),
                "structuralFlags() stays NONE when no source is installed — this is the"
                    + " default behaviour that preserves the pre-carve no-op stub"
                    + " contract: a fresh town whose tickRoadPlans path runs without"
                    + " a production caller wired in stays on the structural floor")
        );
    }

    @Test
    @DisplayName("with a throwing fake source — the tick loop survives; no SoT mutation; returns false")
    void throwingSourceDoesNotBreakTickLoop() {
        Town town = new Town();

        TickScheduler.setRoadPlanSource((t, gameTime) -> {
            throw new IllegalStateException("simulated planner failure for tickRoadPlans"
                + " — the seam must survive this without breaking the tick loop");
        });

        // The helper's try/catch must absorb the throw, log it, and return false.
        // The test does not assert on the log (that's the Logger's contract, not
        // the helper's); it asserts on the observable contract: SoT untouched,
        // helper returns false, caller's markDirty branch is skipped.
        boolean changed = TickScheduler.tickRoadPlans(town, 0L);

        assertAll(
            () -> assertFalse(changed,
                "tickRoadPlans returns false on a throwing source — the helper's try/catch"
                    + " absorbs the throw so the tick loop survives"),
            () -> assertEquals(0, town.getPlannedRoads().size(),
                "getPlannedRoads stays empty — the exception fired before any addRoadSegment"
                    + " call; no partial mutation leaked through"),
            () -> assertSame(StructuralFlags.NONE, town.structuralFlags(),
                "structuralFlags() stays NONE — the throw path is the no-mutation path")
        );
    }

    @Test
    @DisplayName("RoadPlanSource.NONE constant returns an empty list — the default-source contract")
    void noneConstantReturnsEmptyList() {
        // Defensive pin on RoadPlanSource.NONE itself: the default returns an
        // empty list (not null), so the helper's null-tolerance branch is a
        // backstop rather than the expected path.
        List<RoadSegment> result = RoadPlanSource.NONE.planFor(new Town(), 0L);

        assertAll(
            () -> assertNotNull(result,
                "RoadPlanSource.NONE.planFor returns a non-null list — the default"
                    + " never returns null, so the helper's null-tolerance is a backstop"),
            () -> assertTrue(result.isEmpty(),
                "RoadPlanSource.NONE.planFor returns an empty list — the default"
                    + " is the no-mutation path; the structural SoT is untouched")
        );
    }

    @Test
    @DisplayName("same source call across two ticks with the same emit — each call lands; the SoT accumulates without dedup")
    void repeatedSourceCallsAccumulateOnSot() {
        // addRoadSegment is append-only (see TownAddRoadSegmentFromPlannerTest);
        // repeated identical emits accumulate by design, so the planner-side
        // dedup is the planner's responsibility, not the SoT's. The tick
        // helper must not interpose its own dedup.
        Town town = new Town();
        RoadSegment same = fakeSegment(0L);

        TickScheduler.setRoadPlanSource((t, gameTime) -> List.of(same));

        TickScheduler.tickRoadPlans(town, 100L);
        TickScheduler.tickRoadPlans(town, 200L);
        TickScheduler.tickRoadPlans(town, 300L);

        assertEquals(3, town.getPlannedRoads().size(),
            "three identical emits land three entries — addRoadSegment is append-only,"
                + " no tick-level dedup; planner-side dedup is the planner's job");
    }

    @Test
    @DisplayName("source receives (town, gameTime) — the same arguments tickRoadPlans was called with")
    void sourceReceivesTickHelperArguments() {
        // The source is a closure over (town, gameTime); both must propagate
        // verbatim from tickRoadPlans' caller. A production source that
        // rate-limits on gameTime depends on this; a bare-JVM test that
        // forgets to pass gameTime would silently break rate-limited sources.
        Town town = new Town();
        Object[] captured = new Object[2];

        TickScheduler.setRoadPlanSource((t, gameTime) -> {
            captured[0] = t;
            captured[1] = gameTime;
            return List.of();
        });

        TickScheduler.tickRoadPlans(town, 12345L);

        assertAll(
            () -> assertSame(town, captured[0],
                "the source receives the same Town instance tickRoadPlans was called"
                    + " with — the (town, gameTime) pair propagates verbatim"),
            () -> assertEquals(12345L, captured[1],
                "the source receives the same gameTime tickRoadPlans was called with"
                    + " — rate-limited sources depend on this; the helper does not"
                    + " re-derive or default the gameTime argument")
        );
    }
}
