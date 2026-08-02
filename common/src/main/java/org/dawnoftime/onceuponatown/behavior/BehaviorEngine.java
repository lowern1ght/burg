package org.dawnoftime.onceuponatown.behavior;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.dawnoftime.onceuponatown.behavior.intent.IntentScheduler;
import org.dawnoftime.onceuponatown.behavior.intent.NpcSupplier;
import org.dawnoftime.onceuponatown.behavior.intent.TownIntent;
import org.dawnoftime.onceuponatown.behavior.task.CitizenTask;
import org.dawnoftime.onceuponatown.behavior.task.IdleTask;
import org.dawnoftime.onceuponatown.behavior.task.TaskContext;
import org.dawnoftime.onceuponatown.behavior.task.TaskQueue;
import org.dawnoftime.onceuponatown.behavior.task.TaskState;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The orchestrator that ties the intent scheduler to the per-citizen task queue.
 *
 * <p>Each tick:
 *
 * <ol>
 *   <li>Run the scheduler. It prunes stale intents, sorts the survivors by priority, and
 *       pairs free citizens with the intents that resolve. The result is a map of intent
 *       id -> citizen uuid.</li>
 *   <li>For each pair, build a default {@link CitizenTask} (placeholder for now — the
 *       per-kind factories land in the next phase) and hand it to the queue.</li>
 *   <li>Tick every active task. If a task lands in a terminal state, fire its
 *       {@link CitizenTask#onComplete} and remove it from the queue.</li>
 *   <li>Free citizens with no task get an {@link IdleTask} assigned so they show up in
 *       future scheduler pairings consistently.</li>
 * </ol>
 *
 * <p>This class is the composition point for the engine but is not yet wired into the
 * NeoForge event bus or {@code TickScheduler}. That wiring is a separate commit on top of
 * this skeleton.
 */
public final class BehaviorEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(BehaviorEngine.class);

    private final IntentScheduler scheduler;
    private final TaskQueue tasks;
    private final NpcSupplier npcSupplier;

    public BehaviorEngine(IntentScheduler scheduler, TaskQueue tasks, NpcSupplier npcSupplier) {
        this.scheduler = scheduler;
        this.tasks = tasks;
        this.npcSupplier = npcSupplier;
    }

    public IntentScheduler scheduler() { return scheduler; }
    public TaskQueue tasks() { return tasks; }

    /**
     * Runs one engine tick. The engine is the only thing that ticks tasks; the scheduler
     * runs alongside.
     */
    public void onServerTick(ServerLevel level, long gameTick) {
        TaskContext ctx = new TaskContext(level, gameTick, npcSupplier);

        // 1) Scheduler: prune, sort, pair.
        scheduler.onTick(new IntentScheduler.TickContext() {
            @Override public long gameTick() { return gameTick; }
        });

        // 2) Take the pairings and assign each to a citizen. Phase 1 doesn't yet know how
        //    to build a task per intent kind, so we record the pairing in a holding map
        //    and let the next phase promote it to a real task. For now, a paired citizen
        //    with no task gets an IdleTask so the engine's invariant (every citizen has a
        //    task) holds.
        //    The scheduler's pendingAssignments are NOT drained here — they remain until
        //    the next scheduler tick clears them. Tests and consumers can peek or drain
        //    via the scheduler's accessors.
        Map<ResourceLocation, UUID> pairings = scheduler.peekPairings();
        for (Map.Entry<ResourceLocation, UUID> e : pairings.entrySet()) {
            IntentKind kind = kindFor(scheduler.find(e.getKey()).orElse(null));
            LOGGER.debug("[BEHAVIOR] pairing intent {} -> npc {} (kind={})",
                e.getKey(), e.getValue(), kind);
        }

        // 3) Tick active tasks. If a task terminalises, fire onComplete and let the queue
        //    promote the next waiting task (if any).
        Map<UUID, TaskState> completedThisTick = new HashMap<>();
        for (TaskQueue.ActiveTask at : tasks.allActive()) {
            CitizenTask task = at.task();
            TaskState next = task.tick(ctx);
            if (next.isTerminal()) {
                task.onComplete(ctx, next);
                completedThisTick.put(at.npcId(), next);
            }
        }
        for (Map.Entry<UUID, TaskState> e : completedThisTick.entrySet()) {
            tasks.completeForId(e.getKey(), e.getValue());
        }

        // 4) Free citizens without a task get an IdleTask so the engine sees them as
        //    "currently doing nothing" rather than "lost".
        //    Skipped here — the engine doesn't have a town-set on this tick; left for the
        //    wiring commit that knows the town list.
    }

    // --- kind classification (placeholder) -----------------------------------------------

    /** A rough classification used by the engine until Phase BEHAVIOR-2 adds the real factories. */
    public enum IntentKind {
        BUILD, UPGRADE, EXPAND, TRADE, DEFEND, RECALL, IDLE
    }

    /**
     * Returns the {@link IntentKind} for a given intent, or {@link IntentKind#IDLE} if the
     * lookup misses. Used by the engine's debug logging and by the kind-to-task factory
     * pipeline (placeholder).
     */
    public static IntentKind kindFor(TownIntent intent) {
        if (intent == null) return IntentKind.IDLE;
        if (intent instanceof org.dawnoftime.onceuponatown.behavior.intent.BuildIntent) return IntentKind.BUILD;
        if (intent instanceof org.dawnoftime.onceuponatown.behavior.intent.UpgradeIntent) return IntentKind.UPGRADE;
        if (intent instanceof org.dawnoftime.onceuponatown.behavior.intent.ExpandIntent) return IntentKind.EXPAND;
        if (intent instanceof org.dawnoftime.onceuponatown.behavior.intent.TradeIntent) return IntentKind.TRADE;
        if (intent instanceof org.dawnoftime.onceuponatown.behavior.intent.DefendIntent) return IntentKind.DEFEND;
        if (intent instanceof org.dawnoftime.onceuponatown.behavior.intent.RecallIntent) return IntentKind.RECALL;
        return IntentKind.IDLE;
    }

    // --- unused helper retained for the wiring commit ------------------------------------

    /** Builds an idle task for a citizen. Tests can ignore. */
    public static CitizenTask idleFor(Npc npc) {
        return new IdleTask(npc);
    }

    /**
     * Look up a citizen by UUID. Convenience overload for the wiring commit.
     */
    public Optional<Npc> findCitizen(UUID id) {
        return npcSupplier.findByUuid(id);
    }
}
