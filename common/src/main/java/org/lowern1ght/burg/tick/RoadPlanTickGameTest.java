package org.lowern1ght.burg.tick;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.lowern1ght.burg.Constants;
import org.lowern1ght.burg.behavior.intent.ExpandIntent;
import org.lowern1ght.burg.behavior.intent.IntentCost;
import org.lowern1ght.burg.behavior.road.RoadBuilder;
import org.lowern1ght.burg.behavior.road.RoadLayerFromStructures;
import org.lowern1ght.burg.behavior.road.RoadPlanner;
import org.lowern1ght.burg.behavior.road.RoadSegment;
import org.lowern1ght.burg.behavior.road.RoadType;
import org.lowern1ght.burg.behavior.road.TerrainCost;
import org.lowern1ght.burg.behavior.task.RoadTask;
import org.lowern1ght.burg.domain.settlement.StructuralFlags;
import org.lowern1ght.burg.town.Town;

import java.util.List;

/**
 * Live-Minecraft pin for the {@link TickScheduler#tickRoadPlans(Town, long)}
 * wire-up. Drives the seam on a real MC server with a real
 * {@link RoadBuilder} + real {@link ExpandIntent}, and verifies the
 * emitted {@link RoadSegment} lands on the {@link Town} SoT via
 * {@link Town#addRoadSegment(RoadSegment)} — the structural SoT's
 * {@code road_laid} leg flips, and {@link Town#structuralFlags()} moves
 * off the {@link StructuralFlags#NONE} floor.
 *
 * <p>This file is the live-server leg of the wire-up pin set:
 * <ol>
 *   <li>{@link TickSchedulerStructuralWireTest} ({@code :common:test}) —
 *       the helper signature on {@link TickScheduler}: package-private,
 *       static, {@code boolean} return, two declared parameters.</li>
 *   <li>{@link org.lowern1ght.burg.tick.PlannerPopulationSeamTest}
 *       ({@code :common:test}) — the four seam surfaces co-exist (the
 *       mutators, the helpers, the {@code RoadBuilder} FQCN).</li>
 *   <li>{@link TickSchedulerRoadPlanWireTest} ({@code :common:test}) —
 *       the helper's wired behaviour with a fake source (one segment →
 *       SoT size 1; empty list → {@code NONE} flag; throw → no-op).</li>
 *   <li><b>This file.</b> The helper's wired behaviour on a real MC
 *       server with the real {@code RoadBuilder} + real
 *       {@code ExpandIntent}. End-to-end on the live dedicated
 *       {@code runGameTestServer} bootstrap.</li>
 * </ol>
 *
 * <p><b>Why live-server.</b> The bare-JVM wire pin
 * ({@link TickSchedulerRoadPlanWireTest}) proves the
 * {@code setRoadPlanSource → tickRoadPlans → addRoadSegment} plumbing
 * is correct on a plain classpath. The live-server pin proves the
 * <i>real planner</i> ({@link RoadPlanner} + {@link RoadLayerFromStructures})
 * flows through that plumbing: a fresh {@link Town} runs
 * {@code TickScheduler.tickRoadPlans} with a real
 * {@link RoadBuilder#planTasks(ExpandIntent, Town, ServerLevel)} source
 * installed, the planner emits one segment, that segment lands on the
 * SoT, and {@code structuralFlags().roadLaid()} flips from
 * {@code false} to {@code true} on the live server. Anything that
 * worked bare-JVM but breaks on the live server — wrong MC type
 * casting, lifecycle issues with the gametest server's tick loop, a
 * source that captures a stale {@link ServerLevel} reference — surfaces
 * here.
 *
 * <p><b>Static-source cleanup.</b> {@link TickScheduler#setRoadPlanSource}
 * mutates a process-wide static field. The test uses {@code try/finally}
 * to reset to {@code null} on every exit path, so a failure in
 * {@code helper.succeed()} (which the framework converts to a
 * {@code helper.fail()}) still triggers the reset before the next test
 * in the same batch runs. The reset is also defensive against a
 * missing test framework cleanup hook — even on a JVM crash, the
 * static field cannot leak past the {@code finally} block in normal
 * test execution.
 *
 * <p><b>Why no separate gametest for the empty-source path.</b> The
 * empty-source path is the {@link TickSchedulerRoadPlanWireTest}'s
 * {@code emptySourceKeepsStructuralFlagOnFloor} case. Adding the same
 * shape as a {@code @GameTest} would duplicate the bare-JVM pin on the
 * live server without exercising any new live-server behaviour. The
 * live-server leg is the planner-flowing-through-plumbing case; the
 * bare-JVM leg owns the helper-shape cases.
 */
@GameTestHolder(Constants.MOD_ID)
@PrefixGameTestTemplate(false)
public class RoadPlanTickGameTest {

    @GameTest(template = "empty5x5", timeoutTicks = 100, batch = "path")
    public static void roadPlanTick_wiresRoadBuilderToTownSotOnLiveServer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos from = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockPos to = helper.absolutePos(new BlockPos(0, 1, 4));
        plantGrassFloor(level, from, to);

        Town town = new Town();
        town.setName("RoadPlanTickLive");

        // A real ExpandIntent → real RoadBuilder → real RoadSegment. The
        // ExpandIntent is the production-side counterpart of the act-4
        // caller; the gametest fabricates one because no production caller
        // feeds intents today. The seam's job is to route whatever the
        // source returns through Town.addRoadSegment; the gametest pins
        // that routing with the real planner on a real level.
        ExpandIntent intent = new ExpandIntent(from, to, town, 5, IntentCost.empty());
        RoadBuilder builder = new RoadBuilder(
            new RoadPlanner(new TerrainCost()),
            new RoadLayerFromStructures()
        );

        // The source is a closure over the gametest's level reference. Each
        // tickRoadPlans call routes the intent through the planner; the
        // planner emits one task whose segment we surface as the source's
        // output. addRoadSegment receives that segment; the SoT grows.
        TickScheduler.setRoadPlanSource((t, gameTime) -> {
            // The same level that the gametest sees is the level the planner
            // reads chunks from. Capturing it in the closure is the production
            // shape: a real source would carry a level reference through
            // its own install path.
            List<RoadTask> tasks = builder.planTasks(intent, t, level);
            return tasks.stream().map(RoadTask::segment).toList();
        });

        try {
            // Pre-condition: the structural SoT is empty; the structural triple
            // sits on the NONE floor (no zoning-layer commit, no road-planner
            // commit, no placed buildings inside the core radius).
            assertStructuralFloorBeforeTick(town);

            // Drive the seam once. A real planner produces at least one segment
            // for a 4-cell flat-grass path (PathLayerGameTest pins the same
            // planner shape — see {@code roadBuilder_producesRoadTask}).
            boolean changed = TickScheduler.tickRoadPlans(town, level.getGameTime());

            // Post-condition: the SoT has exactly one segment (the planner
            // emits one task per ExpandIntent for the planning slice, see
            // RoadBuilder.planTasks javadoc). The structural leg flips.
            assertStructuralFlagsAfterTick(town, changed, helper);
        } finally {
            // Reset the process-wide source field so the next test in the
            // batch starts on the RoadPlanSource.NONE default. A missing
            // reset would poison later tests in the same JVM with our
            // closure, which captures this helper's level — a stale level
            // reference would silently break subsequent tests.
            TickScheduler.setRoadPlanSource(null);
        }

        helper.succeed();
    }

    /**
     * Convenience pin for the planner's "no segments" path on the live
     * server: with a source that returns an empty list, the seam must
     * still leave the SoT untouched and the structural triple on the
     * {@code NONE} floor. Complements
     * {@link TickSchedulerRoadPlanWireTest#emptySourceKeepsStructuralFlagOnFloor}
     * (bare-JVM) by exercising the same logic on the live server — a
     * regression that returned synthetic segments on the live bootstrap
     * would surface here.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "path")
    public static void roadPlanTick_emptySourceKeepsStructuralFloorOnLiveServer(GameTestHelper helper) {
        Town town = new Town();

        TickScheduler.setRoadPlanSource((t, gameTime) -> List.of());

        try {
            assertStructuralFloorBeforeTick(town);

            boolean changed = TickScheduler.tickRoadPlans(town, helper.getLevel().getGameTime());

            helper.assertTrue(!changed,
                "tickRoadPlans returns false on the live server when the source returns"
                    + " an empty list — the seam is total; the live MC tick loop survives"
                    + " the no-op path");
            helper.assertTrue(town.getPlannedRoads().isEmpty(),
                "getPlannedRoads stays empty even on the live server when the source emits"
                    + " nothing — the SoT-write seam's no-op path is the empty-list"
                    + " contract, not a synthetic-BlockPos.ZERO write");
            helper.assertTrue(town.structuralFlags() == StructuralFlags.NONE,
                "structuralFlags() stays NONE on the live server when the source emits"
                    + " nothing — the road_laid leg stays on the floor; the live-server"
                    + " path is the same as the bare-JVM path on the empty-source"
                    + " contract");
        } finally {
            TickScheduler.setRoadPlanSource(null);
        }

        helper.succeed();
    }

    // -----------------------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------------------

    /**
     * Lay a 1-block-thick grass floor between {@code from} and {@code to}
     * inclusive along the XZ diagonal. Mirrors the helper of the same name
     * in {@link org.lowern1ght.burg.gametest.PathLayerGameTest}: the
     * planner uses Manhattan distance, so the bounding rectangle is the
     * minimum surface to ensure every cell the planner could choose is
     * grass.
     */
    private static void plantGrassFloor(ServerLevel level, BlockPos from, BlockPos to) {
        int minX = Math.min(from.getX(), to.getX());
        int maxX = Math.max(from.getX(), to.getX());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxZ = Math.max(from.getZ(), to.getZ());
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlockAndUpdate(new BlockPos(x, from.getY(), z),
                    Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }
    }

    /**
     * Assertion pair for the pre-condition: the structural triple sits on
     * the {@link StructuralFlags#NONE} floor before the helper runs. The
     * helper's signature is {@code void assertStructuralFloorBeforeTick(Town)},
     * but Java 17 doesn't allow private-static helpers to be referenced as
     * {@code helper.assertTrue}-style assertions from the framework — the
     * framework scans for the {@code helper} parameter on the
     * {@code @GameTest} method, not for nested helpers. We use
     * {@code helper.assertTrue} below for the post-condition (so the
     * framework can locate the failing line) and a plain throw here for
     * the pre-condition (it should never fail on a fresh town).
     */
    private static void assertStructuralFloorBeforeTick(Town town) {
        if (town.getPlannedRoads().isEmpty() && town.getZoningCount().isEmpty()
                && town.structuralFlags() == StructuralFlags.NONE) {
            return;
        }
        throw new IllegalStateException(
            "pre-condition violated: fresh town must sit on the structural floor"
                + " (plannedRoads empty, zoningCount empty, structuralFlags == NONE)"
                + " — got plannedRoads.size=" + town.getPlannedRoads().size()
                + ", zoningCount.size=" + town.getZoningCount().size()
                + ", structuralFlags=" + town.structuralFlags());
    }

    private static void assertStructuralFlagsAfterTick(Town town, boolean changed,
            GameTestHelper helper) {
        helper.assertTrue(changed,
            "tickRoadPlans returns true on the live server after the planner emits a"
                + " segment — the caller's `if (tickRoadPlans(...)) LevelTowns.markDirty()`"
                + " branch takes the dirty-mark path");
        helper.assertTrue(town.getPlannedRoads().size() == 1,
            "after tickRoadPlans on the live server, getPlannedRoads has exactly one"
                + " segment — the source's single-task emit landed on the SoT via"
                + " Town.addRoadSegment (got " + town.getPlannedRoads().size() + ")");
        RoadSegment landed = town.getPlannedRoads().get(0);
        helper.assertTrue(landed != null,
            "the segment on the SoT is non-null — addRoadSegment drops null at the"
                + " edge; the planner's emit must produce a real RoadSegment");
        helper.assertTrue(landed.type() == RoadType.STREET,
            "flat-grass terrain produces a STREET type (got " + landed.type() + ")");
        helper.assertTrue(town.structuralFlags().roadLaid(),
            "structuralFlags().roadLaid() flips true on the live server after the planner"
                + " commits a segment — the structural triple's road_laid leg moves off"
                + " the floor; the act-4 gate's structural predicate sees the planning"
                + " output for the first time");
        helper.assertTrue(town.structuralFlags() != StructuralFlags.NONE,
            "structuralFlags() leaves the NONE floor — at least one leg of the structural"
                + " triple is now set; the gate's permissive form has data to consult");
    }
}
