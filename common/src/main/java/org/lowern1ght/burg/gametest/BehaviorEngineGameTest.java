package org.lowern1ght.burg.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.lowern1ght.burg.Constants;
import org.lowern1ght.burg.behavior.BehaviorEngine;
import org.lowern1ght.burg.behavior.intent.BuildIntent;
import org.lowern1ght.burg.behavior.intent.IntentCost;
import org.lowern1ght.burg.behavior.intent.IntentScheduler;
import org.lowern1ght.burg.behavior.intent.NpcSupplier;
import org.lowern1ght.burg.behavior.intent.TownIntent;
import org.lowern1ght.burg.behavior.task.BuildTask;
import org.lowern1ght.burg.behavior.task.CitizenTask;
import org.lowern1ght.burg.behavior.task.IdleTask;
import org.lowern1ght.burg.behavior.task.RoadTask;
import org.lowern1ght.burg.behavior.task.SpeakTask;
import org.lowern1ght.burg.behavior.task.TaskQueue;
import org.lowern1ght.burg.behavior.task.TaskState;
import org.lowern1ght.burg.entity.Npc;
import org.lowern1ght.burg.registry.EntityRegistry;
import org.lowern1ght.burg.town.Town;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * GameTest coverage for the Phase 1 behaviour engine skeleton.
 *
 * <p>The {@code common} test source set has no Minecraft on its classpath (see
 * {@code common/build.gradle} — "Plain JVM tests, no Minecraft"). Every behavior class
 * here imports an {@code net.minecraft.*} type, so the JUnit tests for the scheduler, the
 * queue and the cost were promoted to {@code @GameTest}. The game-test JVM has the mod
 * classpath, so the tests can construct a real {@link Town}, a real {@link Npc}, and
 * exercise the engine's wiring end-to-end.
 *
 * <p>What the test does NOT cover (left for the wiring commit and the next phase):
 * <ul>
 *   <li>The engine's onServerTick call from a real {@code ServerTickEvent.Post}.</li>
 *   <li>Task kinds beyond BuildTask getting assigned and ticked.</li>
 *   <li>Per-tick block placement (that's the BuildTask implementation deferred to the
 *       next phase).</li>
 * </ul>
 */
@GameTestHolder(Constants.MOD_ID)
@PrefixGameTestTemplate(false)
public class BehaviorEngineGameTest {

    private static final ResourceLocation CARPENTER =
        ResourceLocation.fromNamespaceAndPath("burg", "carpenter");
    private static final ResourceLocation MASON =
        ResourceLocation.fromNamespaceAndPath("burg", "mason");
    // settlement has weight=0 and no construction_cost, so tryAddToConstructionQueue
    // succeeds in the test environment (no stock, weight cap=0).
    private static final ResourceLocation SETTLEMENT =
        ResourceLocation.fromNamespaceAndPath("burg", "settlement");

    // -----------------------------------------------------------------------------------
    // engine smoke
    // -----------------------------------------------------------------------------------

    /**
     * Empty town, empty scheduler, empty task queue — the engine's onServerTick must run
     * cleanly when nothing is wired. This is the smoke test that catches any NPE
     * introduced when the skeleton is extended.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void engine_emptyWorld_runsCleanly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BehaviorEngine engine = new BehaviorEngine(
            new IntentScheduler(emptyNpcSupplier()),
            new TaskQueue(),
            emptyNpcSupplier()
        );

        engine.onServerTick(level, level.getGameTime());

        helper.assertTrue(engine.tasks().allActive().isEmpty(),
            "an empty world does not assign any tasks");
        helper.assertTrue(engine.scheduler().drainPendingAssignments().isEmpty(),
            "an empty world has no pending pairings");
        helper.succeed();
    }

    // -----------------------------------------------------------------------------------
    // scheduler
    // -----------------------------------------------------------------------------------

    /**
     * Spawn a real NPC, register it as a town builder, enqueue a cheap build, and verify
     * the scheduler pairs the intent with the NPC. The engine itself is also ticked, so
     * the TaskQueue ends up recording... well, nothing yet, because the engine's tick
     * only promotes a pairing to a task when the intent-to-task factory lands. What the
     * scheduler reports is the observable pairing.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 80, batch = "behavior")
    public static void scheduler_freeCitizen_pairedWithIntent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));

        Npc builder = spawnBuilder(level, anchor);
        Town town = townWith(builder.getUUID());
        NpcSupplier supplier = new SingleNpcSupplier(town, builder);

        IntentScheduler scheduler = new IntentScheduler(supplier);
        BehaviorEngine engine = new BehaviorEngine(scheduler, new TaskQueue(), supplier);

        TownIntent intent = new BuildIntent(CARPENTER, town, 5, IntentCost.empty(), Town.Zone.CORE);
        scheduler.enqueue(intent);

        engine.onServerTick(level, level.getGameTime());

        var pairings = scheduler.drainPendingAssignments();
        helper.assertTrue(pairings.containsKey(CARPENTER),
            "the scheduler paired the intent with the builder (pairings=" + pairings + ")");
        helper.assertTrue(pairings.get(CARPENTER).equals(builder.getUUID()),
            "the paired citizen is the builder we spawned");
        helper.succeed();
    }

    /**
     * A "super simple" case: an intent whose building is already placed must be dropped
     * by the scheduler on the next tick. This is the canResolve/isStillValid contract
     * exercised in a real Minecraft context.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void scheduler_buildingAlreadyPlaced_dropsIntent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));

        Npc builder = spawnBuilder(level, anchor);
        Town town = townWith(builder.getUUID());
        town.registerBuilding(anchor, CARPENTER.toString(), List.of(),
            new BoundingBox(0, 0, 0, 1, 1, 1), Rotation.NONE);

        NpcSupplier supplier = new SingleNpcSupplier(town, builder);
        IntentScheduler scheduler = new IntentScheduler(supplier);
        BehaviorEngine engine = new BehaviorEngine(scheduler, new TaskQueue(), supplier);

        scheduler.enqueue(new BuildIntent(CARPENTER, town, 5, IntentCost.empty(), Town.Zone.CORE));
        engine.onServerTick(level, level.getGameTime());

        helper.assertTrue(scheduler.activeIntents(town).isEmpty(),
            "the intent was dropped because the building is already in place");
        helper.assertTrue(scheduler.drainPendingAssignments().isEmpty(),
            "no pairings when the only intent is stale");
        helper.succeed();
    }

    /**
     * Enqueue three intents at competing priorities; the active list is sorted with the
     * highest priority first after the tick. With one free citizen the scheduler pairs
     * the top one.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void scheduler_activeListIsSortedByPriority(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));

        Npc builder = spawnBuilder(level, anchor);
        Town town = townWith(builder.getUUID());
        NpcSupplier supplier = new SingleNpcSupplier(town, builder);

        IntentScheduler scheduler = new IntentScheduler(supplier);
        BehaviorEngine engine = new BehaviorEngine(scheduler, new TaskQueue(), supplier);

        scheduler.enqueue(new BuildIntent(MASON, town, 1, IntentCost.empty(), Town.Zone.INDUSTRY));
        scheduler.enqueue(new BuildIntent(CARPENTER, town, 10, IntentCost.empty(), Town.Zone.CORE));
        scheduler.enqueue(new BuildIntent(
            ResourceLocation.fromNamespaceAndPath("burg", "mid"), town, 5, IntentCost.empty(),
            Town.Zone.CORE));

        engine.onServerTick(level, level.getGameTime());

        List<TownIntent> active = scheduler.activeIntents(town);
        helper.assertTrue(active.size() == 3, "three intents remain active");
        helper.assertTrue(active.get(0).basePriority() == 10, "highest priority is first");
        helper.assertTrue(active.get(1).basePriority() == 5, "middle priority is second");
        helper.assertTrue(active.get(2).basePriority() == 1, "lowest priority is last");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void scheduler_cancelledIntent_isRemoved(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));

        Npc builder = spawnBuilder(level, anchor);
        Town town = townWith(builder.getUUID());
        NpcSupplier supplier = new SingleNpcSupplier(town, builder);

        IntentScheduler scheduler = new IntentScheduler(supplier);
        BehaviorEngine engine = new BehaviorEngine(scheduler, new TaskQueue(), supplier);

        scheduler.enqueue(new BuildIntent(CARPENTER, town, 5, IntentCost.empty(), Town.Zone.CORE));
        scheduler.cancel(CARPENTER);
        engine.onServerTick(level, level.getGameTime());

        helper.assertTrue(scheduler.activeIntents(town).isEmpty(),
            "a cancelled intent is gone from the active list");
        helper.assertTrue(scheduler.drainPendingAssignments().isEmpty(),
            "no pairings for a cancelled intent");
        helper.succeed();
    }

    // -----------------------------------------------------------------------------------
    // intent cost
    // -----------------------------------------------------------------------------------

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void intentCost_emptyAndAccumulates(GameTestHelper helper) {
        IntentCost empty = IntentCost.empty();
        helper.assertTrue(empty.isEmpty(), "empty cost has no entries");
        helper.assertTrue(empty.totalAmount() == 0, "empty cost sums to zero");

        ResourceLocation log = ResourceLocation.withDefaultNamespace("oak_log");
        ResourceLocation stone = ResourceLocation.withDefaultNamespace("cobblestone");
        IntentCost cost = new IntentCost(List.of(
            new IntentCost.Entry(log, 12),
            new IntentCost.Entry(stone, 5),
            new IntentCost.Entry(log, 3)
        ));
        helper.assertTrue(cost.totalAmount() == 20, "12 + 5 + 3 sums to 20");
        helper.assertTrue(cost.entries().size() == 3, "three entries listed individually");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void intentCost_emptyIsSingleton(GameTestHelper helper) {
        helper.assertTrue(IntentCost.empty() == IntentCost.empty(),
            "empty() returns the same instance every call");
        helper.succeed();
    }

    // -----------------------------------------------------------------------------------
    // task queue
    // -----------------------------------------------------------------------------------

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void queue_assign_replacesAndPromotes(GameTestHelper helper) {
        TaskQueue queue = new TaskQueue();
        UUID npcOne = UUID.randomUUID();

        CitizenTask first = new RoadTask(UUID.randomUUID(), null, null);
        CitizenTask second = new SpeakTask(UUID.randomUUID(), null, null);
        CitizenTask waiting = new IdleTask(null);

        queue.assignToId(npcOne, first);
        queue.enqueueWaitingForId(npcOne, waiting);
        queue.assignToId(npcOne, second);

        helper.assertTrue(queue.activeCount() == 1, "one active task");
        helper.assertTrue(queue.currentTaskForId(npcOne).orElse(null) == second,
            "the latest assign wins");

        queue.completeForId(npcOne, TaskState.DONE);
        helper.assertTrue(queue.currentTaskForId(npcOne).orElse(null) == waiting,
            "the waiting task is promoted after completion");
        helper.assertTrue(queue.waitingForId(npcOne).isEmpty(),
            "the waiting list drains when the only task is promoted");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void queue_complete_noWaiting_citizenGoesIdle(GameTestHelper helper) {
        TaskQueue queue = new TaskQueue();
        UUID npcOne = UUID.randomUUID();
        CitizenTask only = new RoadTask(UUID.randomUUID(), null, null);

        queue.assignToId(npcOne, only);
        queue.completeForId(npcOne, TaskState.DONE);

        helper.assertTrue(queue.activeCount() == 0, "no active task");
        helper.assertTrue(queue.currentTaskForId(npcOne).isEmpty(),
            "currentTask is empty until a new task is assigned");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void queue_waitingIsFifo(GameTestHelper helper) {
        TaskQueue queue = new TaskQueue();
        UUID npcOne = UUID.randomUUID();
        CitizenTask current = new RoadTask(UUID.randomUUID(), null, null);
        CitizenTask firstWaiting = new SpeakTask(UUID.randomUUID(), null, null);
        CitizenTask secondWaiting = new RoadTask(UUID.randomUUID(), null, null);

        queue.assignToId(npcOne, current);
        queue.enqueueWaitingForId(npcOne, firstWaiting);
        queue.enqueueWaitingForId(npcOne, secondWaiting);

        queue.completeForId(npcOne, TaskState.DONE);

        helper.assertTrue(queue.currentTaskForId(npcOne).orElse(null) == firstWaiting,
            "the first waiting task is promoted");
        Deque<CitizenTask> waiting = new ArrayDeque<>(queue.waitingForId(npcOne));
        helper.assertTrue(waiting.size() == 1, "the second waiting task is still in the queue");
        helper.assertTrue(waiting.peek() == secondWaiting, "FIFO order preserved");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void queue_allActive_returnsEveryAssignedTask(GameTestHelper helper) {
        TaskQueue queue = new TaskQueue();
        UUID npcOne = UUID.randomUUID();
        UUID npcTwo = UUID.randomUUID();
        CitizenTask t1 = new RoadTask(UUID.randomUUID(), null, null);
        CitizenTask t2 = new SpeakTask(UUID.randomUUID(), null, null);

        queue.assignToId(npcOne, t1);
        queue.assignToId(npcTwo, t2);

        var all = queue.allActive();
        helper.assertTrue(all.size() == 2, "two active tasks");
        helper.assertTrue(all.stream().anyMatch(at -> at.npcId().equals(npcOne) && at.task() == t1),
            "the first pairing is in allActive");
        helper.assertTrue(all.stream().anyMatch(at -> at.npcId().equals(npcTwo) && at.task() == t2),
            "the second pairing is in allActive");
        helper.succeed();
    }

    // -----------------------------------------------------------------------------------
    // wire: engine on ServerTick
    // -----------------------------------------------------------------------------------

    /**
     * A {@link BuildIntent} is enqueued for a town with a free builder. Running the
     * engine's {@code onServerTick} once must promote the pairing to a {@link BuildTask}
     * in the per-citizen queue, and the same tick transitions the task PENDING ->
     * IN_PROGRESS (because the legacy {@code Town.tryAddToConstructionQueue} call
     * succeeds in the test setup and the engine ticks the just-assigned task in the
     * same pass).
     *
     * <p><b>Phase 7 update:</b> the task now also tracks {@link BuildTask#progress()}
     * each tick (10% per tick at neutral morale). The town has no actual placement
     * (the legacy {@code BuildGoal} pipeline isn't ticked), so {@code isPlaced}
     * returns false -- but the morale-driven math still drives the task to DONE
     * after 10 neutral-morale ticks. The engine removes the DONE task from the
     * queue and we assert that here.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 120, batch = "behavior")
    public static void engineRunsOnServerTick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));

        Npc builder = spawnBuilder(level, anchor);
        Town town = townWith(builder.getUUID());
        NpcSupplier supplier = new SingleNpcSupplier(town, builder);

        IntentScheduler scheduler = new IntentScheduler(supplier);
        BehaviorEngine engine = new BehaviorEngine(scheduler, new TaskQueue(), supplier);
        // Phase 2: the engine now requires a registered BuildExecutor. Town implements it
        // directly, so we register the test town. Real production wires the same way from
        // TickScheduler.
        BehaviorEngine.register(town, supplier);

        try {
        // Use SETTLEMENT (weight=0, no construction_cost) so tryAddToConstructionQueue
        // succeeds in the test environment where the town has no stock and no era weight cap.
        TownIntent intent = new BuildIntent(SETTLEMENT, town, 5, IntentCost.empty(), Town.Zone.CORE);
        scheduler.enqueue(intent);

        // Tick 1: scheduler pairs the intent, engine assigns a BuildTask to the builder,
        // then ticks the just-assigned task (which transitions PENDING -> IN_PROGRESS by
        // delegating to Town.tryAddToConstructionQueue). Phase 7: the tick also advances
        // progress by the INITIAL_BUMP + BASE_RATE.
        engine.onServerTick(level, level.getGameTime());

        var pairings = scheduler.drainPendingAssignments();
        helper.assertTrue(pairings.containsKey(SETTLEMENT),
            "the scheduler paired the intent (pairings=" + pairings + ")");
        helper.assertTrue(engine.tasks().currentTaskForId(builder.getUUID()).isPresent(),
            "the engine assigned a task to the paired builder");

        CitizenTask afterTick1 = engine.tasks().currentTaskForId(builder.getUUID()).orElseThrow();
        helper.assertTrue(afterTick1 instanceof BuildTask,
            "the assigned task is a BuildTask (was " + afterTick1.getClass().getSimpleName() + ")");
        helper.assertTrue(afterTick1.state() == TaskState.IN_PROGRESS,
            "after the first tick the task has transitioned to IN_PROGRESS via"
                + " Town.tryAddToConstructionQueue (was " + afterTick1.state() + ")");
        helper.assertTrue(!town.constructionQueueView().isEmpty(),
            "BuildTask delegated to Town.tryAddToConstructionQueue, queue now has"
                + " " + town.constructionQueueView().size() + " entry/ies");

        // Ticks 2..N: progress advances each tick. At neutral morale (50 -> multiplier
        // 1.0) with INITIAL_BUMP=0.05 and BASE_RATE=0.1, the task reaches DONE on the
        // 10th tick (progress reaches 1.0). The engine removes DONE tasks from the
        // queue, so currentTaskForId returns empty once the task is terminalised.
        for (int i = 0; i < 15; i++) {
            engine.onServerTick(level, level.getGameTime() + 1L + i);
        }
        helper.assertTrue(engine.tasks().currentTaskForId(builder.getUUID()).isEmpty(),
            "after enough ticks the BuildTask reaches DONE and is removed (currentTask present)");

        helper.succeed();
        } finally {
            BehaviorEngine.register(null, null);
        }
    }

    /**
     * A town with NO intents must produce no tasks. This is the
     * "existing behaviour unchanged" property the wire is meant to preserve: the engine
     * is opt-in by intent enqueueing, and a town with nothing to do is invisible to it.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 80, batch = "behavior")
    public static void engineNoIntent_noop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));

        Npc builder = spawnBuilder(level, anchor);
        Town town = townWith(builder.getUUID());
        NpcSupplier supplier = new SingleNpcSupplier(town, builder);

        IntentScheduler scheduler = new IntentScheduler(supplier);
        BehaviorEngine engine = new BehaviorEngine(scheduler, new TaskQueue(), supplier);

        // No intents enqueued.
        for (int i = 0; i < 10; i++) {
            engine.onServerTick(level, level.getGameTime() + i);
        }

        helper.assertTrue(scheduler.activeIntents(town).isEmpty(),
            "no intents for a town that did not enqueue any");
        helper.assertTrue(scheduler.drainPendingAssignments().isEmpty(),
            "no pairings when the scheduler has no intents");
        helper.assertTrue(engine.tasks().allActive().isEmpty(),
            "no tasks assigned when no intents were enqueued");
        helper.assertTrue(town.constructionQueueView().isEmpty(),
            "the construction queue is untouched (was "
                + town.constructionQueueView().size() + ")");
        helper.succeed();
    }

    // -----------------------------------------------------------------------------------
    // BuildExecutor seam (Phase 2)
    // -----------------------------------------------------------------------------------

    /**
     * A registered {@link FakeBuildExecutor} records the {@code tryQueueNewBuild} call
     * when the engine promotes a {@link BuildIntent} pairing to a {@link BuildTask}. The
     * fake's town and defId arguments must match what the task was constructed with.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 80, batch = "behavior")
    public static void engineUsesBuildExecutor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));

        Npc builder = spawnBuilder(level, anchor);
        Town town = townWith(builder.getUUID());
        NpcSupplier supplier = new SingleNpcSupplier(town, builder);

        // Register a fake executor before running the engine. After this point any
        // BuildTask the engine creates will route through the fake.
        FakeBuildExecutor fake = new FakeBuildExecutor();
        IntentScheduler scheduler = new IntentScheduler(supplier);
        BehaviorEngine engine = new BehaviorEngine(scheduler, new TaskQueue(), supplier);
        BehaviorEngine.register(fake, supplier);

        try {
            TownIntent intent = new BuildIntent(SETTLEMENT, town, 5, IntentCost.empty(), Town.Zone.CORE);
            scheduler.enqueue(intent);
            engine.onServerTick(level, level.getGameTime());

            var newBuildCalls = fake.getNewBuildCalls();
            helper.assertTrue(!newBuildCalls.isEmpty(),
                "BuildExecutor.tryQueueNewBuild was called at least once (calls="
                    + newBuildCalls.size() + ")");
            FakeBuildExecutor.NewBuildCall call = newBuildCalls.get(0);
            helper.assertTrue(call.town() == town,
                "the executor received the same town instance the intent was bound to");
            helper.assertTrue(SETTLEMENT.equals(call.defId()),
                "the executor received the intent's defId (was " + call.defId() + ")");
            helper.assertTrue(builder.getUUID().toString().equals(call.placerUuid()),
                "the executor received the builder's UUID as placer (was " + call.placerUuid() + ")");
        } finally {
            // Leave the static state clean for the next test (last writer wins, but a stale
            // fake would surprise a later test).
            BehaviorEngine.register(null, null);
        }
        helper.succeed();
    }

    /**
     * When the executor returns false (queue full, unaffordable, weight cap exceeded,
     * etc.) the engine marks the {@link BuildTask} as FAILED on the same tick rather than
     * leaving it spinning in IN_PROGRESS. Verified end-to-end via
     * {@link TaskState#FAILED}.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 80, batch = "behavior")
    public static void fakeExecutorRejectsNoop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));

        Npc builder = spawnBuilder(level, anchor);
        Town town = townWith(builder.getUUID());
        NpcSupplier supplier = new SingleNpcSupplier(town, builder);

        FakeBuildExecutor fake = new FakeBuildExecutor();
        fake.newBuildResult = false;   // reject every enqueue
        IntentScheduler scheduler = new IntentScheduler(supplier);
        BehaviorEngine engine = new BehaviorEngine(scheduler, new TaskQueue(), supplier);
        BehaviorEngine.register(fake, supplier);

        try {
            TownIntent intent = new BuildIntent(SETTLEMENT, town, 5, IntentCost.empty(), Town.Zone.CORE);
            scheduler.enqueue(intent);
            engine.onServerTick(level, level.getGameTime());

            // The engine's tick completed and removed the BuildTask from the queue because
            // it transitioned to FAILED on the same tick (the executor rejected the
            // enqueue). Verify via the recorded call list and via the town's empty queue --
            // the task is no longer present to read directly.
            helper.assertTrue(fake.getNewBuildCalls().size() == 1,
                "the executor was called exactly once before the task failed (calls="
                    + fake.getNewBuildCalls().size() + ")");
            helper.assertTrue(town.constructionQueueView().isEmpty(),
                "no construction queue entry was added (executor rejected) (queue size="
                    + town.constructionQueueView().size() + ")");
            helper.assertTrue(engine.tasks().allActive().isEmpty(),
                "the BuildTask is no longer active (terminalized and removed) (active="
                    + engine.tasks().allActive().size() + ")");
        } finally {
            BehaviorEngine.register(null, null);
        }
        helper.succeed();
    }

    /**
     * Last writer wins. A second {@link BehaviorEngine#register} overwrites the first.
     * Asserts by swapping a recording executor for a no-call executor and observing that
     * the second register's executor is the one the engine picks up.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 80, batch = "behavior")
    public static void engineRegistersExecutorOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));

        Npc builder = spawnBuilder(level, anchor);
        Town town = townWith(builder.getUUID());
        NpcSupplier supplier = new SingleNpcSupplier(town, builder);

        FakeBuildExecutor first = new FakeBuildExecutor();
        FakeBuildExecutor second = new FakeBuildExecutor();
        IntentScheduler scheduler = new IntentScheduler(supplier);
        BehaviorEngine engine = new BehaviorEngine(scheduler, new TaskQueue(), supplier);

        BehaviorEngine.register(first, supplier);
        BehaviorEngine.register(second, supplier);
        helper.assertTrue(BehaviorEngine.getExecutor() == second,
            "the second register() overwrites the first (got " + BehaviorEngine.getExecutor()
                + ")");

        // Sanity check that the engine actually routes through second on this tick.
        try {
            scheduler.enqueue(new BuildIntent(SETTLEMENT, town, 5, IntentCost.empty(), Town.Zone.CORE));
            engine.onServerTick(level, level.getGameTime());

            helper.assertTrue(first.getNewBuildCalls().isEmpty(),
                "the first (overwritten) executor recorded no calls (calls="
                    + first.getNewBuildCalls().size() + ")");
            helper.assertTrue(second.getNewBuildCalls().size() == 1,
                "the second (current) executor recorded the engine's call (calls="
                    + second.getNewBuildCalls().size() + ")");
        } finally {
            BehaviorEngine.register(null, null);
        }
        helper.succeed();
    }

    // -----------------------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------------------

    private static Npc spawnBuilder(ServerLevel level, BlockPos anchor) {
        Npc builder = EntityRegistry.NPC.create(level);
        builder.setPersistenceRequired();
        builder.moveTo(anchor.getX() + 0.5, anchor.getY() + 1.0, anchor.getZ() + 0.5);
        level.addFreshEntity(builder);
        return builder;
    }

    private static Town townWith(UUID builderId) {
        Town town = new Town();
        town.setBuilderNpcIdAtSlot(0, builderId);
        town.setName("TestTown");
        return town;
    }

    private static NpcSupplier emptyNpcSupplier() {
        return new NpcSupplier() {
            @Override public List<Npc> freeCitizens(Town town) { return List.of(); }
            @Override public Optional<Npc> findByUuid(UUID id) { return Optional.empty(); }
        };
    }

    /** Returns the given NPC when the town matches, else empty. */
    private record SingleNpcSupplier(Town target, Npc npc) implements NpcSupplier {
        @Override public List<Npc> freeCitizens(Town town) {
            return town == target ? List.of(npc) : List.of();
        }
        @Override public Optional<Npc> findByUuid(UUID id) {
            return npc.getUUID().equals(id) ? Optional.of(npc) : Optional.empty();
        }
    }

    /**
     * Compile-time guard: if {@link TaskState} ever drifts out of the {@code terminated}
     * set the engine relies on, the engine's promote-on-complete logic breaks. The check
     * is here so a future test that calls these states directly cannot accidentally
     * add a new terminal state without noticing.
     */
    @SuppressWarnings("unused")
    private static void assertTerminalStates(TaskState s) {
        // Three terminals as advertised in TaskState.java.
        if (s == TaskState.DONE || s == TaskState.FAILED || s == TaskState.INTERRUPTED) return;
        throw new AssertionError("TaskState " + s + " is not terminal");
    }
}
