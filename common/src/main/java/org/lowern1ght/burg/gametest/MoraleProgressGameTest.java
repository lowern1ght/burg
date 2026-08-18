package org.lowern1ght.burg.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.lowern1ght.burg.Constants;
import org.lowern1ght.burg.behavior.intent.BuildIntent;
import org.lowern1ght.burg.behavior.intent.IntentCost;
import org.lowern1ght.burg.behavior.morale.MoraleState;
import org.lowern1ght.burg.behavior.road.RoadSegment;
import org.lowern1ght.burg.behavior.road.RoadType;
import org.lowern1ght.burg.behavior.task.BuildTask;
import org.lowern1ght.burg.behavior.task.CitizenTask;
import org.lowern1ght.burg.behavior.task.RoadTask;
import org.lowern1ght.burg.behavior.task.TaskContext;
import org.lowern1ght.burg.behavior.task.TaskState;
import org.lowern1ght.burg.entity.Npc;
import org.lowern1ght.burg.registry.EntityRegistry;
import org.lowern1ght.burg.town.Town;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * GameTest coverage for Phase BEHAVIOR-7: morale multiplier actually drives
 * task progress.
 *
 * <p>Phase 6 wired the morale multiplier into the engine's log line; Phase 7
 * makes it move {@link CitizenTask#progress()} per tick. The cases here
 * exercise the multiplier directly via {@link BuildTask#tick} and
 * {@link RoadTask#tick}, side-stepping the engine so the math is the only
 * thing under test.
 *
 * <p>The morale-driven math is linear (0.5x at 0 morale to 1.5x at 100 morale);
 * {@link BuildTask} adds a 0.05 {@code INITIAL_BUMP} on the PENDING tick.
 * Tests use a real {@link Town} (as the {@code source} for {@link BuildIntent}
 * and the BuildExecutor target) and a {@link FakeBuildExecutor} for the
 * seam so the engine's static executor doesn't leak across tests.
 */
@GameTestHolder(Constants.MOD_ID)
@PrefixGameTestTemplate(false)
public class MoraleProgressGameTest {

    private static final ResourceLocation SETTLEMENT =
        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "settlement");

    // -----------------------------------------------------------------------------------
    // BuildTask morale rate
    // -----------------------------------------------------------------------------------

    /**
     * Set the assignee's morale to 90 (multiplier 1.4). Run 5 ticks. Progress
     * must exceed 0.5 -- a high-morale citizen completes meaningfully faster
     * than the neutral 0.5 mark would suggest.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 80, batch = "behavior")
    public static void buildTask_highMorale_completesFaster(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(1, 1, 1));

        Npc builder = spawnCitizen(level, anchor);
        MoraleState morale = new MoraleState();
        morale.set(builder.getUUID(), 90);

        FakeBuildExecutor fake = new FakeBuildExecutor();
        BuildTask task = newBuildTask(builder, fake);

        for (int i = 0; i < 5; i++) {
            task.tick(contextFor(level, fake, morale));
        }
        helper.assertTrue(task.progress() > 0.5f,
            "high morale (90, mult 1.4) advances progress > 0.5 in 5 ticks (was "
                + task.progress() + ")");

        helper.succeed();
    }

    /**
     * Set the assignee's morale to 10 (multiplier 0.6). Run 5 ticks. Progress
     * must stay under 0.5 -- a low-morale citizen is meaningfully slower than
     * the neutral 0.5 mark.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 80, batch = "behavior")
    public static void buildTask_lowMorale_completesSlower(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(1, 1, 1));

        Npc builder = spawnCitizen(level, anchor);
        MoraleState morale = new MoraleState();
        morale.set(builder.getUUID(), 10);

        FakeBuildExecutor fake = new FakeBuildExecutor();
        BuildTask task = newBuildTask(builder, fake);

        for (int i = 0; i < 5; i++) {
            task.tick(contextFor(level, fake, morale));
        }
        helper.assertTrue(task.progress() < 0.5f,
            "low morale (10, mult 0.6) keeps progress < 0.5 in 5 ticks (was "
                + task.progress() + ")");

        helper.succeed();
    }

    /**
     * Set the assignee's morale to 50 (multiplier exactly 1.0). Run 5 ticks.
     * Progress should land near 0.55 (initial 0.05 bump + 5 * 0.1 * 1.0); we
     * assert a band around the spec's "approximately 0.5" expectation with
     * enough tolerance for the bump.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 80, batch = "behavior")
    public static void buildTask_neutralMorale_linearProgress(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(1, 1, 1));

        Npc builder = spawnCitizen(level, anchor);
        MoraleState morale = new MoraleState();
        morale.set(builder.getUUID(), 50);

        FakeBuildExecutor fake = new FakeBuildExecutor();
        BuildTask task = newBuildTask(builder, fake);

        for (int i = 0; i < 5; i++) {
            task.tick(contextFor(level, fake, morale));
        }
        // 0.05 (initial bump) + 5 * 0.1 * 1.0 = 0.55. Tolerance 0.1 covers the bump.
        float progress = task.progress();
        helper.assertTrue(progress > 0.4f && progress < 0.7f,
            "neutral morale (50, mult 1.0) lands near 0.5 in 5 ticks (was " + progress + ")");

        helper.succeed();
    }

    /**
     * Setup the {@link FakeBuildExecutor} to report {@code SETTLEMENT} as
     * already placed. On the first tick the task delegates to the executor
     * (PENDING -> STARTED, progress 0.05) then sees {@code isPlaced == true}
     * and force-completes to {@link TaskState#DONE}. The real-world
     * completion signal wins over the math-driven progress.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 80, batch = "behavior")
    public static void buildTask_forceComplete_whenBuildingPlaced(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(1, 1, 1));

        Npc builder = spawnCitizen(level, anchor);

        FakeBuildExecutor fake = new FakeBuildExecutor();
        fake.placed.add(SETTLEMENT);    // pre-populate the placed set

        BuildTask task = newBuildTask(builder, fake);

        task.tick(contextFor(level, fake, new MoraleState()));
        helper.assertTrue(task.state() == TaskState.DONE,
            "BuildTask force-completes when isPlaced returns true (state was "
                + task.state() + ")");
        helper.assertTrue(task.progress() >= 1.0f,
            "BuildTask progress is forced to 1.0 on placement (was " + task.progress() + ")");

        helper.succeed();
    }

    // -----------------------------------------------------------------------------------
    // RoadTask waypoint advancement
    // -----------------------------------------------------------------------------------

    /**
     * Build a 10-waypoint segment, run 5 ticks at neutral morale, and verify
     * {@link RoadTask#currentWaypoint} tracks progress linearly: at progress 0.5
     * the halfway point of the waypoint list (index 5) is the current waypoint.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 80, batch = "behavior")
    public static void pathTask_advancesThroughWaypoints(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos a = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockPos b = helper.absolutePos(new BlockPos(9, 1, 0));
        List<BlockPos> waypoints = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            waypoints.add(new BlockPos(i, 1, 0));
        }
        RoadSegment segment = new RoadSegment(a, b, waypoints, RoadType.STREET);
        RoadTask task = new RoadTask(segment, SETTLEMENT);

        MoraleState morale = new MoraleState();
        // No assignee on the production constructor -> multiplier defaults to 1.0.
        for (int i = 0; i < 5; i++) {
            task.tick(contextFor(level, new FakeBuildExecutor(), morale));
        }

        helper.assertTrue(task.progress() > 0.4f,
            "RoadTask progressed past 0.4 in 5 ticks (was " + task.progress() + ")");
        helper.assertTrue(task.currentWaypoint() == 5,
            "currentWaypoint() tracks halfway through 10 waypoints at progress 0.5"
                + " (was " + task.currentWaypoint() + ")");

        helper.succeed();
    }

    // -----------------------------------------------------------------------------------
    // CitizenTask.progress() via the interface
    // -----------------------------------------------------------------------------------

    /**
     * Read {@link CitizenTask#progress()} through the sealed interface, not the
     * concrete {@link BuildTask} class. Verifies the default-on-interface
     * addition is accessible from generic code (the engine's diagnostics path).
     */
    @GameTest(template = "empty5x5", timeoutTicks = 80, batch = "behavior")
    public static void progress_isExposed_viaCitizenTaskInterface(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(1, 1, 1));

        Npc builder = spawnCitizen(level, anchor);
        MoraleState morale = new MoraleState();
        morale.set(builder.getUUID(), 50);

        FakeBuildExecutor fake = new FakeBuildExecutor();
        BuildTask concrete = newBuildTask(builder, fake);

        // Up-cast to the interface; the engine's diagnostics path looks at
        // CitizenTask.progress(), never at the concrete subclass.
        CitizenTask task = concrete;
        helper.assertTrue(task.progress() == 0.0f,
            "fresh task reports 0.0 progress via CitizenTask.progress() (was "
                + task.progress() + ")");

        task.tick(contextFor(level, fake, morale));
        helper.assertTrue(task.progress() > 0.0f,
            "after one tick progress advances above 0 via CitizenTask.progress() (was "
                + task.progress() + ")");

        helper.succeed();
    }

    // -----------------------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------------------

    private static Npc spawnCitizen(ServerLevel level, BlockPos position) {
        Npc npc = EntityRegistry.NPC.create(level);
        npc.setPersistenceRequired();
        npc.moveTo(position.getX() + 0.5, position.getY() + 1.0, position.getZ() + 0.5);
        level.addFreshEntity(npc);
        return npc;
    }

    private static BuildTask newBuildTask(Npc builder, FakeBuildExecutor fake) {
        Town town = new Town();
        town.setName("ProgressTest");
        return new BuildTask(UUID.randomUUID(),
            new BuildIntent(SETTLEMENT, town, 5, IntentCost.empty(), Town.Zone.CORE),
            builder, fake);
    }

    /**
     * A {@link TaskContext} carrying the level, the fake executor, and the
     * caller-supplied {@link MoraleState}. The NPC supplier is a no-op stub
     * because the tasks under test do not look up citizens by uuid.
     */
    private static TaskContext contextFor(ServerLevel level, FakeBuildExecutor fake,
                                          MoraleState morale) {
        return new TaskContext(level, level.getGameTime(),
            new org.lowern1ght.burg.behavior.intent.NpcSupplier() {
                @Override
                public List<Npc> freeCitizens(Town town) {
                    return List.of();
                }
                @Override
                public Optional<Npc> findByUuid(UUID id) {
                    return Optional.empty();
                }
            },
            fake, morale);
    }
}
