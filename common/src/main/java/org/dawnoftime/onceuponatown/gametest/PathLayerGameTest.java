package org.dawnoftime.onceuponatown.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.dawnoftime.onceuponatown.Constants;
import org.dawnoftime.onceuponatown.behavior.intent.ExpandIntent;
import org.dawnoftime.onceuponatown.behavior.intent.IntentCost;
import org.dawnoftime.onceuponatown.behavior.path.RoadBuilder;
import org.dawnoftime.onceuponatown.behavior.path.RoadGraph;
import org.dawnoftime.onceuponatown.behavior.path.RoadLayer;
import org.dawnoftime.onceuponatown.behavior.path.RoadLayerFromStructures;
import org.dawnoftime.onceuponatown.behavior.path.RoadPlanner;
import org.dawnoftime.onceuponatown.behavior.path.RoadSegment;
import org.dawnoftime.onceuponatown.behavior.path.RoadType;
import org.dawnoftime.onceuponatown.behavior.path.TerrainCost;
import org.dawnoftime.onceuponatown.behavior.task.PathTask;
import org.dawnoftime.onceuponatown.behavior.task.TaskState;
import org.dawnoftime.onceuponatown.town.Town;

import java.util.List;
import java.util.Optional;

/**
 * GameTest coverage for the Phase BEHAVIOR-3 path layer.
 *
 * <p>Exercises the planner, the cost table, the layer, the builder, and the
 * resulting {@link PathTask} lifecycle. Lives in {@code gametest/} (not
 * {@code common/src/test/}) because {@link TerrainCost} and {@link
 * RoadPlanner} import {@code net.minecraft.*} types — the plain-JVM test
 * source set has no Minecraft on its classpath.
 *
 * <p>Tests use the {@code empty5x5} template and operate entirely inside
 * its 5x5 footprint. The planner's chunk-loaded assumption is satisfied
 * because the template is a single chunk; positions outside the template
 * footprint resolve to {@code air} via {@link ServerLevel#getBlockState}.
 * Tests that need a known-block surface — like the {@code
 * roadPlanner_avoidsForest} case — set blocks explicitly inside the
 * template.
 */
@GameTestHolder(Constants.MOD_ID)
@PrefixGameTestTemplate(false)
public class PathLayerGameTest {

    private static final ResourceLocation EXPECTED_STREET =
        ResourceLocation.fromNamespaceAndPath("onceuponatown", "streets/street_step");
    private static final ResourceLocation EXPECTED_BRIDGE =
        ResourceLocation.fromNamespaceAndPath("onceuponatown", "streets/street_bridge_3");
    private static final ResourceLocation EXPECTED_CULVERT =
        ResourceLocation.fromNamespaceAndPath("onceuponatown", "streets/street_culvert");

    // -----------------------------------------------------------------------------------
    // TerrainCost
    // -----------------------------------------------------------------------------------

    /**
     * The default cost table matches the calibration in the task spec:
     * grass and dirt are cheap, wood is moderate, water is expensive, stone
     * is very expensive.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "path")
    public static void terrainCost_returnsConfiguredCosts(GameTestHelper helper) {
        TerrainCost cost = new TerrainCost();

        helper.assertTrue(cost.costFor(Blocks.OAK_LOG) == 5,
            "oak_log is 5 (was " + cost.costFor(Blocks.OAK_LOG) + ")");
        helper.assertTrue(cost.costFor(Blocks.GRASS_BLOCK) == 1,
            "grass_block is 1 (was " + cost.costFor(Blocks.GRASS_BLOCK) + ")");
        helper.assertTrue(cost.costFor(Blocks.WATER) == 15,
            "water is 15 (was " + cost.costFor(Blocks.WATER) + ")");
        helper.assertTrue(cost.costFor(Blocks.STONE) == 20,
            "stone is 20 (was " + cost.costFor(Blocks.STONE) + ")");
        helper.assertTrue(cost.costFor(Blocks.LAVA) == 100,
            "lava is 100 (was " + cost.costFor(Blocks.LAVA) + ")");

        // Unknown blocks fall back to a moderate cost (10).
        helper.assertTrue(cost.costFor(Blocks.PURPLE_WOOL) == 10,
            "unknown blocks default to 10 (was " + cost.costFor(Blocks.PURPLE_WOOL) + ")");

        // registerCost overrides the default.
        cost.registerCost(Blocks.PURPLE_WOOL, 2);
        helper.assertTrue(cost.costFor(Blocks.PURPLE_WOOL) == 2,
            "registerCost overrides the default (was " + cost.costFor(Blocks.PURPLE_WOOL) + ")");

        helper.succeed();
    }

    // -----------------------------------------------------------------------------------
    // RoadPlanner
    // -----------------------------------------------------------------------------------

    /**
     * A flat (all-grass) test world, a short distance — the planner must
     * deliver a SEGMENT whose waypoints all sit on the surface and whose
     * segments are all STREET.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 80, batch = "path")
    public static void roadPlanner_flatTerrain_shortPath(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockPos end = helper.absolutePos(new BlockPos(0, 1, 4));
        plantGrassFloor(level, start, end);

        RoadPlanner planner = new RoadPlanner(new TerrainCost());
        RoadSegment segment = planner.plan(start, end, level);

        helper.assertTrue(segment.start().equals(start),
            "segment.start is the from endpoint (was " + segment.start() + ")");
        helper.assertTrue(segment.end().equals(end),
            "segment.end is the to endpoint (was " + segment.end() + ")");
        helper.assertTrue(segment.type() == RoadType.STREET,
            "flat terrain produces a STREET type (was " + segment.type() + ")");
        helper.assertTrue(segment.length() >= 5,
            "waypoint count covers the distance (was " + segment.length() + ")");
        helper.assertTrue(segment.waypoints().get(0).equals(start),
            "first waypoint is the from endpoint (was " + segment.waypoints().get(0) + ")");
        helper.assertTrue(segment.waypoints().get(segment.length() - 1).equals(end),
            "last waypoint is the to endpoint (was "
                + segment.waypoints().get(segment.length() - 1) + ")");

        helper.succeed();
    }

    /**
     * A 3x3 patch of {@code oak_log} in the middle of a flat grass world.
     * Cost of the straight-line path through the forest: 1+5+5+5+1 = 17.
     * Cost of routing around the patch: ~9 (5 cells in, 4 cells out, plus
     * 2 cells to leave the row). The planner must pick the cheaper route —
     * if it walks straight through, the total cost is too high.
     *
     * <p>Why the cost assertion (and not a "no log cell" check): the
     * planner is free to walk through a log cell if no cheaper route exists.
     * The cost threshold captures the Planner's intent ("avoid the forest
     * when a cheaper route exists") without coupling the test to the exact
     * path geometry.
     *
     * <p>5x5 template constraint: the planner must be able to read cells
     * outside the 5x5 footprint to find the detour. Sandbox chunks return
     * AIR for unloaded cells, so this works.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 80, batch = "path")
    public static void roadPlanner_avoidsForest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockPos end = helper.absolutePos(new BlockPos(4, 1, 0));

        // Build a 5x3 grass corridor at z = -1, 0, +1 (the planner can
        // route through any of these rows).
        for (int x = 0; x < 5; x++) {
            for (int z = -1; z <= 1; z++) {
                level.setBlockAndUpdate(
                    helper.absolutePos(new BlockPos(x, 1, z)),
                    Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }

        // 3x3 patch of oak_log placed at z = -1, 0, +1, x = 1, 2, 3 (in
        // absolute coords). This sits in the middle of the route's
        // straight-line path and forces the planner to detour around it.
        BlockPos patchOrigin = helper.absolutePos(new BlockPos(1, 1, -1));
        for (int dx = 0; dx < 3; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                BlockPos leaf = patchOrigin.offset(dx, 0, dz);
                level.setBlockAndUpdate(leaf, Blocks.OAK_LOG.defaultBlockState());
            }
        }

        RoadPlanner planner = new RoadPlanner(new TerrainCost());
        RoadSegment segment = planner.plan(start, end, level);

        // The planner must pick a path with a cost strictly less than the
        // straight-through cost (17). The detour adds at most 2 cells at
        // 1 cost each (round trip out of the row), so the threshold of 13
        // captures the planner's intent without depending on the exact
        // detour geometry.
        TerrainCost costs = new TerrainCost();
        int totalCost = 0;
        for (BlockPos wp : segment.waypoints()) {
            totalCost += costs.costFor(level.getBlockState(wp).getBlock());
        }
        helper.assertTrue(totalCost < 13,
            "planner routes around the forest (cost was " + totalCost
                + ", threshold < 13 means the planner avoided the patch)");

        helper.assertTrue(segment.length() >= 5,
            "waypoint count covers the distance (was " + segment.length() + ")");
        helper.assertTrue(segment.type() == RoadType.STREET,
            "no water → STREET type (was " + segment.type() + ")");

        helper.succeed();
    }

    /**
     * Trivial case: from == to. The planner must return a single-cell
     * segment classified by the start cell.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "path")
    public static void roadPlanner_zeroDistance_returnsSingleCell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState());

        RoadPlanner planner = new RoadPlanner(new TerrainCost());
        RoadSegment segment = planner.plan(pos, pos, level);

        helper.assertTrue(segment.length() == 1,
            "single-cell waypoints (was " + segment.length() + ")");
        helper.assertTrue(segment.waypoints().get(0).equals(pos),
            "the single waypoint is the start/end cell (was " + segment.waypoints().get(0) + ")");
        helper.assertTrue(segment.type() == RoadType.STREET,
            "grass cell classifies as STREET (was " + segment.type() + ")");

        helper.succeed();
    }

    /**
     * A WATER cell along the route reclassifies the whole segment as
     * BRIDGE. The planner still prefers a grass detour in the first
     * instance, but if the only viable path crosses water, the segment's
     * surface type is BRIDGE.
     *
     * <p>Setup: a 3-cell grass corridor with water in the middle, surrounded
     * by stone barriers on every side. The planner must cross the water
     * because the stone (cost 20) makes any detour far more expensive than
     * the single water cell (cost 15).
     */
    @GameTest(template = "empty5x5", timeoutTicks = 80, batch = "path")
    public static void roadPlanner_pathOverWater_classifiesAsBridge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockPos end = helper.absolutePos(new BlockPos(2, 1, 0));

        // Build a 3-cell grass corridor at z=0 with water in the middle.
        for (int dx = 0; dx < 3; dx++) {
            BlockPos cell = helper.absolutePos(new BlockPos(dx, 1, 0));
            if (dx == 1) {
                level.setBlockAndUpdate(cell, Blocks.WATER.defaultBlockState());
            } else {
                level.setBlockAndUpdate(cell, Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }

        // Stone barriers north and south of the corridor: z=1 and z=-1.
        // The planner would need to detour around these (cost 20 per cell)
        // rather than walk through them, so the cheapest route is over the
        // single water cell (cost 15).
        for (int dx = -1; dx <= 3; dx++) {
            for (int dz : new int[]{-1, 1}) {
                BlockPos cell = helper.absolutePos(new BlockPos(dx, 1, dz));
                level.setBlockAndUpdate(cell, Blocks.STONE.defaultBlockState());
            }
        }

        RoadPlanner planner = new RoadPlanner(new TerrainCost());
        RoadSegment segment = planner.plan(start, end, level);

        helper.assertTrue(segment.type() == RoadType.BRIDGE,
            "path over water classifies as BRIDGE (was " + segment.type() + ")");

        helper.succeed();
    }

    // -----------------------------------------------------------------------------------
    // RoadLayer
    // -----------------------------------------------------------------------------------

    /**
     * Each {@link RoadType} maps to the right NBT structure id. The
     * canonical piece is the one named in the layer — variant selection
     * is a future concern.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "path")
    public static void roadLayer_returnsCorrectNbt(GameTestHelper helper) {
        RoadLayer layer = new RoadLayerFromStructures();

        BlockPos a = new BlockPos(0, 1, 0);
        BlockPos b = new BlockPos(1, 1, 0);

        RoadSegment street  = new RoadSegment(a, b, List.of(a, b), RoadType.STREET);
        RoadSegment bridge  = new RoadSegment(a, b, List.of(a, b), RoadType.BRIDGE);
        RoadSegment culvert = new RoadSegment(a, b, List.of(a, b), RoadType.CULVERT);

        helper.assertTrue(EXPECTED_STREET.equals(layer.pieceFor(street)),
            "STREET -> " + EXPECTED_STREET + " (was " + layer.pieceFor(street) + ")");
        helper.assertTrue(EXPECTED_BRIDGE.equals(layer.pieceFor(bridge)),
            "BRIDGE -> " + EXPECTED_BRIDGE + " (was " + layer.pieceFor(bridge) + ")");
        helper.assertTrue(EXPECTED_CULVERT.equals(layer.pieceFor(culvert)),
            "CULVERT -> " + EXPECTED_CULVERT + " (was " + layer.pieceFor(culvert) + ")");

        helper.succeed();
    }

    // -----------------------------------------------------------------------------------
    // RoadBuilder
    // -----------------------------------------------------------------------------------

    /**
     * End-to-end: an {@link ExpandIntent} is handed to {@link RoadBuilder},
     * which returns a list of {@link PathTask}s. Each task carries the
     * planner's segment and the layer's piece NBT.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 80, batch = "path")
    public static void roadBuilder_producesPathTask(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos from = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockPos to = helper.absolutePos(new BlockPos(0, 1, 4));
        plantGrassFloor(level, from, to);

        Town town = new Town();
        town.setName("PathTest");
        ExpandIntent intent = new ExpandIntent(from, to, town, 5, IntentCost.empty());

        RoadBuilder builder = new RoadBuilder(
            new RoadPlanner(new TerrainCost()),
            new RoadLayerFromStructures()
        );

        List<PathTask> tasks = builder.planTasks(intent, town, level);

        helper.assertTrue(tasks.size() == 1,
            "one task per segment (was " + tasks.size() + ")");

        PathTask task = tasks.get(0);
        helper.assertTrue(task.segment() != null,
            "the task carries the planner's segment");
        helper.assertTrue(task.segment().start().equals(from),
            "segment.start is the intent's from (was " + task.segment().start() + ")");
        helper.assertTrue(task.segment().end().equals(to),
            "segment.end is the intent's to (was " + task.segment().end() + ")");
        helper.assertTrue(EXPECTED_STREET.equals(task.pieceNbt()),
            "the task carries the layer's piece NBT (was " + task.pieceNbt() + ")");

        // Side effect: the graph records the endpoints and the segment.
        RoadGraph graph = builder.graph();
        helper.assertTrue(graph.hasNode(town, from),
            "the graph records the from endpoint");
        helper.assertTrue(graph.hasNode(town, to),
            "the graph records the to endpoint");
        helper.assertTrue(graph.plannedFor(town).size() == 1,
            "the graph records one segment (was " + graph.plannedFor(town).size() + ")");

        helper.succeed();
    }

    /**
     * The builder returns an empty task list if any input is null. Defensive
     * contract — the engine should never pass null to the planner, but the
     * builder's public surface should tolerate it.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "path")
    public static void roadBuilder_nullInput_returnsEmpty(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Town town = new Town();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));

        RoadBuilder builder = new RoadBuilder(
            new RoadPlanner(new TerrainCost()),
            new RoadLayerFromStructures()
        );

        helper.assertTrue(builder.planTasks(null, town, level).isEmpty(),
            "null intent returns empty");
        helper.assertTrue(builder.planTasks(
            new ExpandIntent(pos, pos, town, 0, IntentCost.empty()), null, level).isEmpty(),
            "null town returns empty");
        helper.assertTrue(builder.planTasks(
            new ExpandIntent(pos, pos, town, 0, IntentCost.empty()), town, null).isEmpty(),
            "null level returns empty");

        helper.succeed();
    }

    // -----------------------------------------------------------------------------------
    // PathTask
    // -----------------------------------------------------------------------------------

    /**
     * The planner-built {@link PathTask} transitions
     * {@link TaskState#PENDING} → {@link TaskState#IN_PROGRESS} →
     * {@link TaskState#DONE} across two ticks. Planning-only — no blocks
     * placed.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "path")
    public static void pathTask_completesAfterOneTick(GameTestHelper helper) {
        BlockPos a = new BlockPos(0, 1, 0);
        BlockPos b = new BlockPos(1, 1, 0);
        RoadSegment segment = new RoadSegment(a, b, List.of(a, b), RoadType.STREET);
        PathTask task = new PathTask(segment, EXPECTED_STREET);

        helper.assertTrue(task.state() == TaskState.PENDING,
            "new task is PENDING (was " + task.state() + ")");

        task.tick(stubContext(helper));
        helper.assertTrue(task.state() == TaskState.IN_PROGRESS,
            "first tick transitions to IN_PROGRESS (was " + task.state() + ")");

        task.tick(stubContext(helper));
        helper.assertTrue(task.state() == TaskState.DONE,
            "second tick transitions to DONE (was " + task.state() + ")");

        helper.succeed();
    }

    /**
     * The legacy stub form of {@link PathTask} (UUID + source + assignee)
     * preserves the pre-revision behaviour: a single FAILED state on first
     * tick. The existing queue tests rely on this; the new constructor
     * shape is additive.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "path")
    public static void pathTask_legacyStub_failsOnFirstTick(GameTestHelper helper) {
        PathTask legacy = new PathTask(java.util.UUID.randomUUID(), null, null);

        helper.assertTrue(legacy.state() == TaskState.PENDING,
            "legacy stub starts PENDING (was " + legacy.state() + ")");

        legacy.tick(stubContext(helper));
        helper.assertTrue(legacy.state() == TaskState.FAILED,
            "legacy stub fails on first tick (was " + legacy.state() + ")");

        helper.succeed();
    }

    // -----------------------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------------------

    /**
     * Lay a 1-block-thick grass floor between {@code from} and {@code to}
     * inclusive along the XZ diagonal (the planner uses Manhattan distance,
     * so this is the bounding rectangle to ensure every cell the planner
     * could choose is grass).
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
     * A minimal {@link org.dawnoftime.onceuponatown.behavior.task.TaskContext}
     * for tasks that need a level but do not touch the NPC supplier. The
     * planner-built {@link PathTask} does not look at the context in this
     * revision, so this is enough for the lifecycle tests.
     */
    private static org.dawnoftime.onceuponatown.behavior.task.TaskContext stubContext(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        return new org.dawnoftime.onceuponatown.behavior.task.TaskContext(
            level, level.getGameTime(), new org.dawnoftime.onceuponatown.behavior.intent.NpcSupplier() {
                @Override
                public List<org.dawnoftime.onceuponatown.entity.Npc> freeCitizens(Town town) {
                    return List.of();
                }
                @Override
                public Optional<org.dawnoftime.onceuponatown.entity.Npc> findByUuid(
                        java.util.UUID id) {
                    return Optional.empty();
                }
            });
    }
}
