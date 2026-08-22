package org.lowern1ght.burg.tick;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.settlement.StructuralFlags;
import org.lowern1ght.burg.town.Town;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MC-aware behaviour pin for the structural-flags SoT wire-up helpers in
 * {@link TickScheduler} once they have been retired to no-op stubs.
 *
 * <p>The act-5 follow-up to the structural-fields carve originally landed
 * two synthetic-write helpers — {@link TickScheduler#tickZoning(Town, long)}
 * and {@link TickScheduler#tickRoadPlans(Town, long)} — that wrote the
 * first increment on the per-town SoTs ({@code zoningCount} and
 * {@code plannedRoads}) from the production tick path so
 * {@link Town#structuralFlags()} flipped from {@link StructuralFlags#NONE}
 * to non-{@code NONE} the moment a town ticked. Both helpers were
 * package-private (no {@code public} modifier) on purpose: they existed
 * as the seam the production tick path read, not as a public API
 * surface for outside callers.
 *
 * <p>The synthetic writes were misleading on a pre-act-5 codebase — there
 * was no real zoning layer or road planner to back them — so every town
 * the tick loop touched had its structural triple flipped to a
 * synthetic value, and the hub-mode gate's structural leg fired
 * spuriously on every save. The helper bodies have been retired to
 * no-op stubs (return {@code false}, never mutate the SoT); the method
 * signatures are preserved so the seam the future production zoning
 * layer / road planner wire into is already in place.
 *
 * <p><b>What this pins.</b> Five claims about the no-op wire that the
 * production tick path relies on:
 *
 * <ol>
 *   <li><b>Fresh-town collapse.</b> A {@code new Town()} with no helper
 *       calls reports {@code structuralFlags() == NONE}, exactly the
 *       no-progress floor the act-4 follow-up landed.</li>
 *   <li><b>Helpers never flip the gate.</b> Calling
 *       {@link TickScheduler#tickZoning(Town, long)} or
 *       {@link TickScheduler#tickRoadPlans(Town, long)} on a fresh town
 *       returns {@code false} and does not mutate the SoT — the helper
 *       is a no-op stub, not a synthetic write site.</li>
 *   <li><b>Zoning leg stays on the floor.</b> Even after
 *       {@link TickScheduler#tickZoning(Town, long)} runs, the
 *       {@code industryZoned} leg of the gate is still {@code false};
 *       the (future) production zoning layer is the only sanctioned
 *       writer via {@link Town#addZoning(org.lowern1ght.burg.town.Town.Zone, int)}.</li>
 *   <li><b>Road leg stays on the floor.</b> Even after
 *       {@link TickScheduler#tickRoadPlans(Town, long)} runs, the
 *       {@code roadLaid} leg of the gate is still {@code false}; the
 *       (future) production road planner's commit path is the only
 *       sanctioned writer via
 *       {@link Town#addRoadSegment(org.lowern1ght.burg.behavior.road.RoadSegment)}.</li>
 *   <li><b>SoT stays empty.</b> Calling both helpers in sequence does
 *       not populate either map; the hub-mode gate's structural triple
 *       collapses to {@link StructuralFlags#NONE} forever unless a real
 *       mutator runs.</li>
 * </ol>
 *
 * <p>The bare-JVM signature pin lives in {@code :common:test}'s
 * {@link TickSchedulerStructuralWireTest}; the post-tick no-op pin (a
 * fresh town stays at {@code-NONE} after the bare-JVM tick helpers run)
 * lives in {@code :neoforge:test}'s
 * {@link TickSchedulerStructuralFlagsPostTickNoneTest}. This file holds
 * the per-helper pin for the no-op stub bodies.
 */
class TickSchedulerStructuralFlagsWireTest {

    @Test
    @DisplayName("fresh town — tickZoning and tickRoadPlans are no-op stubs: both return false on every call, neither writes the SoT")
    void freshTownHelpersAreNoOps() {
        Town town = new Town();

        assertAll(
            () -> assertFalse(TickScheduler.tickZoning(town, 0L),
                "first tickZoning call returns false — the synthetic first-cell write has"
                    + " been removed; the helper is a no-op stub until the production zoning"
                    + " layer (a future carve) takes over the seam"),
            () -> assertEquals(0, town.getZoningCount().size(),
                "after tickZoning, getZoningCount is still empty — the no-op stub never"
                    + " calls Town.addZoning; the SoT stays on the empty-map floor"),
            () -> assertFalse(TickScheduler.tickZoning(town, 0L),
                "second tickZoning call also returns false — the helper is no-op on every call,"
                    + " not just the first; idempotence is implicit because there is no write"),
            () -> assertEquals(0, town.getZoningCount().size(),
                "second tickZoning is a no-op — zoningCount stays empty regardless of how many"
                    + " ticks pass"),
            () -> assertFalse(TickScheduler.tickRoadPlans(town, 0L),
                "first tickRoadPlans call returns false — the synthetic one-cell segment at"
                    + " BlockPos.ZERO has been removed; the helper is a no-op stub until the"
                    + " production road planner (a future carve) takes over the seam"),
            () -> assertEquals(0, town.getPlannedRoads().size(),
                "after tickRoadPlans, getPlannedRoads is still empty — the no-op stub never"
                    + " calls Town.addRoadSegment; the SoT stays on the empty-list floor"),
            () -> assertFalse(TickScheduler.tickRoadPlans(town, 0L),
                "second tickRoadPlans call also returns false — the helper is no-op on every call"),
            () -> assertEquals(0, town.getPlannedRoads().size(),
                "second tickRoadPlans is a no-op — plannedRoads stays empty regardless of how many"
                    + " ticks pass")
        );
    }

    @Test
    @DisplayName("fresh town before any helper call — structuralFlags collapses to NONE")
    void freshTownDerivesNone() {
        Town town = new Town();

        assertSame(StructuralFlags.NONE, town.structuralFlags(),
            "a fresh town (no zoning, no roads, no buildings) reports structuralFlags() == NONE;"
                + " the act-4 gate's structural leg is off the floor until the production"
                + " zoning layer / road planner writes the SoT through Town.addZoning /"
                + " Town.addRoadSegment");
    }

    @Test
    @DisplayName("after tickZoning — structuralFlags stays NONE; the helper does NOT flip the industry_zoned leg")
    void tickZoningDoesNotFlipStructuralFlags() {
        Town town = new Town();

        // Pre-condition: NONE floor.
        assertSame(StructuralFlags.NONE, town.structuralFlags(),
            "before the helper call, the gate is on the NONE floor");

        assertFalse(TickScheduler.tickZoning(town, 0L),
            "the tickZoning helper is a no-op stub — it returns false instead of landing"
                + " the synthetic addZoning(CORE, 1) write");

        // Post-condition: structural triple still NONE — no synthetic flip.
        StructuralFlags observed = town.structuralFlags();
        assertAll(
            () -> assertSame(StructuralFlags.NONE, observed,
                "after tickZoning, structuralFlags() is still NONE — the no-op stub never"
                    + " writes the zoning SoT; the structural triple's industry_zoned leg"
                    + " stays on the empty-map floor"),
            () -> assertFalse(observed.isAnySet(),
                "structuralFlags().isAnySet() stays false after tickZoning — the gate's"
                    + " permissive form does not fire on a town the zoning layer has not"
                    + " touched"),
            () -> assertFalse(observed.industryZoned(),
                "structuralFlags().industryZoned() is false — the no-op helper never writes"
                    + " Town.addZoning; the (future) zoning layer is the only sanctioned"
                    + " writer for this leg"),
            () -> assertFalse(observed.roadLaid(),
                "structuralFlags().roadLaid() is still false — tickZoning does not touch the"
                    + " road SoT either; the legs are independent")
        );
    }

    @Test
    @DisplayName("after tickRoadPlans — structuralFlags stays NONE; the helper does NOT flip the road_laid leg")
    void tickRoadPlansDoesNotFlipStructuralFlags() {
        Town town = new Town();

        // Pre-condition: NONE floor.
        assertSame(StructuralFlags.NONE, town.structuralFlags(),
            "before the helper call, the gate is on the NONE floor");

        assertFalse(TickScheduler.tickRoadPlans(town, 0L),
            "the tickRoadPlans helper is a no-op stub — it returns false instead of appending"
                + " the synthetic one-cell segment at BlockPos.ZERO");

        StructuralFlags observed = town.structuralFlags();
        assertAll(
            () -> assertSame(StructuralFlags.NONE, observed,
                "after tickRoadPlans, structuralFlags() is still NONE — the no-op stub never"
                    + " writes the planned-roads SoT; the structural triple's road_laid leg"
                    + " stays on the empty-list floor"),
            () -> assertFalse(observed.isAnySet(),
                "structuralFlags().isAnySet() stays false after tickRoadPlans — the gate's"
                    + " permissive form does not fire on a town the road planner has not"
                    + " committed"),
            () -> assertFalse(observed.roadLaid(),
                "structuralFlags().roadLaid() is false — the no-op helper never writes"
                    + " Town.addRoadSegment; the (future) road planner is the only sanctioned"
                    + " writer for this leg"),
            () -> assertFalse(observed.industryZoned(),
                "structuralFlags().industryZoned() is still false — tickRoadPlans does not"
                    + " touch the zoning SoT either; the legs are independent")
        );
    }

    @Test
    @DisplayName("tickZoning then tickRoadPlans — structuralFlags stays NONE; neither leg fires spuriously")
    void bothHelpersTogetherKeepBothLegsOnTheFloor() {
        Town town = new Town();

        TickScheduler.tickZoning(town, 0L);
        TickScheduler.tickRoadPlans(town, 0L);

        StructuralFlags observed = town.structuralFlags();
        assertAll(
            () -> assertFalse(observed.industryZoned(),
                "industry_zoned is false — tickZoning is a no-op, the zoning SoT stays empty"),
            () -> assertFalse(observed.roadLaid(),
                "road_laid is false — tickRoadPlans is a no-op, the planned-roads SoT stays"
                    + " empty"),
            () -> assertFalse(observed.corePopulated(),
                "core_populated is false — no buildings have been placed, the real-derivation"
                    + " leg is independent of the SoT-write legs"),
            () -> assertFalse(observed.isAnySet(),
                "structuralFlags().isAnySet() is false — the gate's structural triple"
                    + " collapses to NONE forever unless a real mutator runs"),
            () -> assertSame(StructuralFlags.NONE, observed,
                "structuralFlags() returns the NONE sentinel — record-equality; both no-op"
                    + " helpers together cannot flip the gate; only the production zoning"
                    + " layer / road planner writes the SoT")
        );
    }

    @Test
    @DisplayName("the no-op helpers do not append any segment to plannedRoads — there is no synthetic segment at BlockPos.ZERO")
    void noSyntheticSegmentIsAppended() {
        Town town = new Town();

        // Even after multiple calls at multiple game times, the planned-roads
        // list stays empty. The legacy synthetic write appended a one-cell
        // STREET at BlockPos.ZERO on the first call; the no-op stub never
        // produces a segment, so the SoT never sees a BlockPos.ZERO entry.
        for (int i = 0; i < 3; i++) {
            long gameTime = (long) (i + 1) * 100L;
            TickScheduler.tickRoadPlans(town, gameTime);
        }

        assertTrue(town.getPlannedRoads().isEmpty(),
            "plannedRoads stays empty across 3 calls — the no-op stub does not append"
                + " a synthetic segment; the SoT only grows when the (future) production"
                + " road planner calls Town.addRoadSegment from RoadBuilder.planTasks");

        assertEquals(0, town.getZoningCount().size(),
            "zoningCount stays empty across 3 calls — the no-op stub does not write"
                + " Town.addZoning; the SoT only grows when the (future) production"
                + " zoning layer commits a decision");
    }
}