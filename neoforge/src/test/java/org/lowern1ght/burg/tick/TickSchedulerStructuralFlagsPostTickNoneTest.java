package org.lowern1ght.burg.tick;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.datapack.QuestDataHandler;
import org.lowern1ght.burg.domain.settlement.StructuralFlags;
import org.lowern1ght.burg.people.RaidConfig;
import org.lowern1ght.burg.town.Town;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour pin for the planner-population carve: a fresh town's
 * {@link Town#structuralFlags()} stays at {@link StructuralFlags#NONE}
 * even after the {@link TickScheduler}'s bare-JVM tick helpers
 * ({@link TickScheduler#tickQuests(Town, long)} and
 * {@link TickScheduler#tickRaids(Town, long)}) run. The hub-mode gate's
 * structural leg must not fire spuriously on a town whose planner has
 * never committed and whose zoning layer has never run.
 *
 * <p>The earlier PR #56 carve landed two synthetic write helpers
 * ({@code TickScheduler.tickZoning} and
 * {@code TickScheduler.tickRoadPlans}) that wrote the first increment
 * on the per-town SoTs the moment the tick loop ran. That made
 * {@code structuralFlags()} flip from {@code NONE} to non-{@code NONE}
 * for every town that ticked — even ones whose planner / zoning layer
 * had no real work to do. The synthetic helpers were removed by the
 * PR #57 follow-up; the mutators
 * ({@link Town#addZoning(org.lowern1ght.burg.town.Town.Zone, int)} and
 * {@link Town#addRoadSegment(org.lowern1ght.burg.behavior.road.RoadSegment)})
 * remain the only sanctioned write paths into the per-town SoTs.
 *
 * <p>This test pins the post-carve state on the bare-JVM test surface
 * (no MinecraftServer required) — a fresh {@code new Town()} is fed
 * through the two package-private tick helpers that
 * {@link TickScheduler#tick(net.minecraft.server.MinecraftServer)}
 * routes to, and the assertions check that the structural triple and
 * the underlying SoTs are untouched. A regression that re-introduces
 * a synthetic write (the original PR #56 mistake) would flip one of
 * the two maps to non-empty and break the assertions; a regression
 * that wires the planner / zoning layer into the tick path before its
 * real output exists would fail the same assertion for a different
 * reason.
 *
 * <p>Companion pins (same carve, three angles):
 * <ol>
 *   <li>{@link TickSchedulerStructuralWireTest} in {@code :common:test}
 *       — reflection-based pin that {@code tickZoning(Town, long)} and
 *       {@code tickRoadPlans(Town, long)} no longer exist on
 *       {@link TickScheduler}. Cheap target, no MinecraftServer
 *       needed.</li>
 *   <li>{@link TickSchedulerStructuralFlagsPlannerPinTest} in
 *       {@code :neoforge:test} — planner-commit end-to-end pin: calling
 *       {@link Town#addRoadSegment(org.lowern1ght.burg.behavior.road.RoadSegment)}
 *       flips {@code roadLaid()}, calling
 *       {@link Town#addZoning(org.lowern1ght.burg.town.Town.Zone, int)}
 *       flips {@code industryZoned()}, and the aggregator reads both
 *       through {@link Town#structuralFlags()}. Same MC-aware target as
 *       this file.</li>
 *   <li><b>This file.</b> The "no spurious write" pin — the tick
 *       helpers themselves do not write the structural SoT, so a town
 *       whose planner / zoning layer never ran collapses to
 *       {@link StructuralFlags#NONE} forever regardless of how many
 *       ticks pass.</li>
 * </ol>
 */
class TickSchedulerStructuralFlagsPostTickNoneTest {

    @AfterEach
    void resetQuestDataHandlerRegistry() {
        // Reflection-driven cleanup so a run with multiple @Test methods
        // does not leak quest defs across cases. Cleared via the same
        // path `QuestDataHandler.reload` uses (REGISTRY.clear()).
        try {
            Field registry = QuestDataHandler.class.getDeclaredField("REGISTRY");
            registry.setAccessible(true);
            ((Map<String, ?>) registry.get(null)).clear();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("failed to reset QuestDataHandler.REGISTRY", e);
        }
        // Same discipline the bare-JVM TickSchedulerRaidTest uses for the
        // volatile RaidConfig slot — restore the additive default so the
        // next test starts from a known baseline.
        RaidConfig.resetCurrent();
    }

    @Test
    @DisplayName("fresh town — structuralFlags() stays NONE even after TickScheduler.tickQuests + tickRaids run; the gate never fires spuriously")
    void structuralFlagsStayNoneAfterBareJvmTickHelpersRun() {
        Town town = new Town();

        // Pre-condition: NONE floor — the planner has not committed, the
        // zoning layer has not run, no building has been placed. The hub
        // mode's structural triple is on the floor regardless of
        // acquisition. Pin it before the tick helpers run so a regression
        // that lazily flips the SoT on first read is loud.
        assertSame(StructuralFlags.NONE, town.structuralFlags(),
            "pre-tick floor — a fresh town's structural triple collapses to"
                + " NONE. The planner-population carve removed the synthetic"
                + " tickZoning / tickRoadPlans helpers, so the structural SoT"
                + " starts empty and stays empty until a real mutator runs");

        // Drive the two bare-JVM tick helpers the production
        // TickScheduler.tick method calls in its per-town update.
        //
        // tickQuests at gameTime=1L — with no QuestDefs registered (the
        // AfterEach reset clears the registry), the for-loop is a no-op
        // and the helper returns false. Even with stray defs from a prior
        // test, a fresh town's prerequisitesMet returns true for any
        // def without required buildings, so the worst case is a Quest
        // being appended — never a write to the structural SoT.
        //
        // tickRaids at gameTime=1L — under the default 600s/12000-tick
        // cooldown (RaidConfig.DEFAULT.cooldownTicks()), the gate does
        // NOT fire (1L < 12000L). The helper returns false and does not
        // call town.setLastRaidFireTick. Even if it did fire (e.g. a
        // future test sets the cooldown to 0), the write goes to
        // town.lastRaidFireTick — never to the structural SoT.
        TickScheduler.tickQuests(town, 1L);
        TickScheduler.tickRaids(town, 1L);

        // Post-condition: structural triple still NONE, the SoT maps
        // still empty. The hub-mode gate's structural leg stays quiet —
        // no spurious fire even after the bare-JVM tick helpers have run.
        assertAll(
            () -> assertSame(StructuralFlags.NONE, town.structuralFlags(),
                "post-tick floor — tickQuests + tickRaids do not write the"
                    + " structural SoT. A regression that re-introduces the"
                    + " synthetic tickZoning / tickRoadPlans helpers from"
                    + " PR #56 would pin structuralFlags() to a non-NONE"
                    + " value and the gate would fire spuriously; a"
                    + " regression that wires a future planner / zoning"
                    + " layer into the tick path before its real output"
                    + " exists would fail the same assertion"),
            () -> assertTrue(town.getZoningCount().isEmpty(),
                "zoningCount stays empty — the tick helpers do not write"
                    + " the zoning SoT; the (future) zoning layer is the"
                    + " only sanctioned writer via Town.addZoning"),
            () -> assertTrue(town.getPlannedRoads().isEmpty(),
                "plannedRoads stays empty — the tick helpers do not write"
                    + " the planned-roads SoT; the road planner's commit"
                    + " path (a future carve) is the only sanctioned"
                    + " writer via Town.addRoadSegment")
        );
    }

    @Test
    @DisplayName("fresh town — running the bare-JVM tick helpers multiple times in a row does not flip the structural triple either")
    void structuralFlagsStayNoneAcrossRepeatedBareJvmTicks() {
        Town town = new Town();

        // Drive the tick helpers several times at different game times.
        // Each call must be a structural no-op for a fresh town — even
        // at game times past the raid cooldown (tickRaids may stamp
        // lastRaidFireTick, but never touches the structural SoT).
        for (int i = 0; i < 4; i++) {
            long gameTime = (long) (i + 1) * 100L;
            TickScheduler.tickQuests(town, gameTime);
            TickScheduler.tickRaids(town, gameTime);

            assertSame(StructuralFlags.NONE, town.structuralFlags(),
                "iteration " + (i + 1) + " of 4 — structuralFlags() is NONE"
                    + " after tickQuests + tickRaids at gameTime=" + gameTime
                    + ". The structural SoT must not flip regardless of how"
                    + " many ticks have passed; a regression that wires a"
                    + " planner / zoning layer into the tick path on the"
                    + " wrong tick would break here");
        }

        // Final aggregate check on the SoT maps.
        assertAll(
            () -> assertTrue(town.getZoningCount().isEmpty(),
                "zoningCount stays empty across 4 ticks — the zoning layer"
                    + " is a future carve, and the tick helpers do not"
                    + " substitute for it"),
            () -> assertTrue(town.getPlannedRoads().isEmpty(),
                "plannedRoads stays empty across 4 ticks — the road"
                    + " planner's commit path is the only sanctioned"
                    + " writer, and the tick helpers do not substitute"
                    + " for it")
        );
    }
}