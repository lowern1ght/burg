package org.dawnoftime.onceuponatown.behavior;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.dawnoftime.onceuponatown.behavior.executor.BuildExecutor;
import org.dawnoftime.onceuponatown.behavior.intent.BuildIntent;
import org.dawnoftime.onceuponatown.behavior.intent.IntentScheduler;
import org.dawnoftime.onceuponatown.behavior.intent.NpcSupplier;
import org.dawnoftime.onceuponatown.behavior.intent.TownIntent;
import org.dawnoftime.onceuponatown.behavior.intent.UpgradeIntent;
import org.dawnoftime.onceuponatown.behavior.role.RoleAssigner;
import org.dawnoftime.onceuponatown.behavior.role.RoleAssignerConfig;
import org.dawnoftime.onceuponatown.behavior.task.BuildTask;
import org.dawnoftime.onceuponatown.behavior.task.CitizenTask;
import org.dawnoftime.onceuponatown.behavior.task.IdleTask;
import org.dawnoftime.onceuponatown.behavior.task.TaskContext;
import org.dawnoftime.onceuponatown.behavior.task.TaskQueue;
import org.dawnoftime.onceuponatown.behavior.task.TaskState;
import org.dawnoftime.onceuponatown.behavior.task.UpgradeTask;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.Town;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
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
 *   <li>For each pairing, build a default {@link CitizenTask} (a {@link BuildTask} for a
 *       {@link BuildIntent}, an {@link UpgradeTask} for an {@link UpgradeIntent}, an
 *       {@link IdleTask} for the catch-all) and hand it to the queue.</li>
 *   <li>Tick every active task. If a task lands in a terminal state, fire its
 *       {@link CitizenTask#onComplete} and remove it from the queue.</li>
 * </ol>
 *
 * <p>The engine is a singleton ({@link #INSTANCE}). The single Minecraft main thread
 * drives it from {@code TickScheduler.tick}. The world-aware {@link NpcSupplier}
 * captures the active {@code ServerLevel} via a per-tick field set by
 * {@link #onServerTick}; reading it outside a tick returns an empty list / nothing,
 * which is the safe default.
 *
 * <p>Thread safety: not required. Server ticks are single-threaded on the main thread,
 * and the engine is only invoked from there. Documented for future maintainers.
 */
public final class BehaviorEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(BehaviorEngine.class);

    /**
     * The singleton. Initialised at class load. Tests may construct their own
     * {@code BehaviorEngine} for isolation; production code uses this instance.
     */
    public static final BehaviorEngine INSTANCE = createInstance();

    private static BehaviorEngine createInstance() {
        // One WorldNpcSupplier shared between the scheduler and the engine. Without
        // sharing, the scheduler's supplier never gets bound to the engine and its
        // freeCitizens() returns empty (no level), defeating pairings.
        WorldNpcSupplier supplier = new WorldNpcSupplier();
        return new BehaviorEngine(
            new IntentScheduler(supplier),
            new TaskQueue(),
            supplier
        );
    }

    // --- static test seam (Phase 2) --------------------------------------------------
    //
    // The engine's BuildExecutor and NpcSupplier are registered once per server lifetime
    // (TickScheduler wires them at startup). Tests call register() with a FakeBuildExecutor
    // and any NpcSupplier they like. The volatile fields give the reads a happens-before
    // relationship with the writes -- single-threaded on the main thread, but the
    // explicit volatile documents that.
    //
    // Last writer wins: a second register() overwrites the first. Tests rely on this to
    // swap the seam between cases (engineRegistersExecutorOnce tests the semantics).

    private static volatile BuildExecutor staticExecutor;
    private static volatile NpcSupplier staticNpcSupplier;

    /**
     * Register the BuildExecutor and NpcSupplier the engine uses when assigning tasks.
     * Last writer wins.
     */
    public static void register(BuildExecutor executor, NpcSupplier npcSupplier) {
        staticExecutor = executor;
        staticNpcSupplier = npcSupplier;
    }

    /** The executor currently registered, or null if register() has not been called. */
    public static BuildExecutor getExecutor() { return staticExecutor; }

    /** The NPC supplier currently registered, or null if register() has not been called. */
    public static NpcSupplier getNpcSupplier() { return staticNpcSupplier; }

    private final IntentScheduler scheduler;
    private final TaskQueue tasks;
    private final NpcSupplier npcSupplier;
    /**
     * Per-engine role assigner. Updated every {@link #ROLE_UPDATE_INTERVAL} ticks for every
     * town in the active level. The engine reads roles from here in the next phase (intent
     * routing); for now the assigner runs but nothing consults it.
     */
    private final RoleAssigner roleAssigner = new RoleAssigner();
    /** Tick counter used to throttle the role-assigner update. */
    private int ticksSinceLastRoleUpdate = 0;
    /** Role-assigner runs every {@value} ticks, i.e. once every 5 seconds at 20 tps. */
    private static final int ROLE_UPDATE_INTERVAL = 100;

    /**
     * The level the engine is currently ticking. Set by {@link #onServerTick} before any
     * other work; read by {@link WorldNpcSupplier} to resolve UUIDs and builder lists.
     * Single-threaded (main thread) -- no volatile or synchronization needed.
     */
    private ServerLevel currentLevel;

    public BehaviorEngine(IntentScheduler scheduler, TaskQueue tasks, NpcSupplier npcSupplier) {
        this.scheduler = scheduler;
        this.tasks = tasks;
        this.npcSupplier = npcSupplier;
        if (npcSupplier instanceof WorldNpcSupplier w) {
            w.bind(this);
        }
    }

    public IntentScheduler scheduler() { return scheduler; }
    public TaskQueue tasks() { return tasks; }

    /** The role assigner this engine maintains. Tests and the next phase consult this directly. */
    public RoleAssigner roleAssigner() { return roleAssigner; }

    /**
     * Runs one engine tick. The engine is the only thing that ticks tasks; the scheduler
     * runs alongside.
     */
    public void onServerTick(ServerLevel level, long gameTick) {
        ServerLevel previous = this.currentLevel;
        this.currentLevel = level;
        try {
            TaskContext ctx = new TaskContext(level, gameTick, npcSupplier);

            // 0) Role assigner. Runs at ROLE_UPDATE_INTERVAL (5s at 20 tps) to keep the
            //    bookkeeping cheap. Iterates every town in this level and feeds it the
            //    currently-loaded citizens. Idempotent: a citizen that has already been
            //    assigned keeps its role, so repeated ticks are safe.
            ticksSinceLastRoleUpdate++;
            if (ticksSinceLastRoleUpdate >= ROLE_UPDATE_INTERVAL) {
                ticksSinceLastRoleUpdate = 0;
                for (Town town : LevelTowns.get(level).getAllTowns()) {
                    List<Npc> citizens = npcSupplier.freeCitizens(town);
                    roleAssigner.update(town, citizens, RoleAssignerConfig.defaults());
                }
            }

            // 1) Scheduler: prune, sort, pair.
            scheduler.onTick(new IntentScheduler.TickContext() {
                @Override public long gameTick() { return gameTick; }
            });

            // 2) Promote pairings to tasks. The scheduler only knows "intent X goes to
            //    citizen Y" -- it is the engine's job to construct the right CitizenTask
            //    for that pairing and hand it to the per-citizen queue.
            Map<ResourceLocation, UUID> pairings = scheduler.peekPairings();
            for (Map.Entry<ResourceLocation, UUID> e : pairings.entrySet()) {
                ResourceLocation intentId = e.getKey();
                UUID citizenId = e.getValue();
                TownIntent intent = scheduler.find(intentId).orElse(null);
                if (intent == null) {
                    LOGGER.debug("[BEHAVIOR] pairing for intent {} has no intent in scheduler --"
                        + " skipping", intentId);
                    continue;
                }
                Npc citizen = npcSupplier.findByUuid(citizenId).orElse(null);
                if (citizen == null) {
                    LOGGER.debug("[BEHAVIOR] pairing for intent {} -> {} has no live citizen --"
                        + " skipping", intentId, citizenId);
                    continue;
                }
                CitizenTask task = buildTask(intent, citizen);
                if (task == null) {
                    LOGGER.debug("[BEHAVIOR] no task factory for intent kind {} -- skipping",
                        kindFor(intent));
                    continue;
                }
                tasks.assign(citizen, task);
            }

            // 3) Tick active tasks. If a task terminalises, fire onComplete and let the queue
            //    promote the next waiting task (if any).
            Map<UUID, TaskState> completedThisTick = new HashMap<>();
            for (TaskQueue.ActiveTask at : tasks.allActive()) {
                CitizenTask task = at.task();
                if (task.state().isTerminal()) continue;
                TaskState next = task.tick(ctx);
                if (next.isTerminal()) {
                    task.onComplete(ctx, next);
                    completedThisTick.put(at.npcId(), next);
                }
            }
            for (Map.Entry<UUID, TaskState> e : completedThisTick.entrySet()) {
                tasks.completeForId(e.getKey(), e.getValue());
            }
        } finally {
            this.currentLevel = previous;
        }
    }

    /**
     * Factory: choose the task kind for a given intent. Returns null if the engine has no
     * executor for this intent kind yet (defensive -- the engine logs and skips), or if
     * no BuildExecutor has been registered (the engine cannot enqueue without one).
     */
    private static CitizenTask buildTask(TownIntent intent, Npc citizen) {
        BuildExecutor exec = staticExecutor;
        if (exec == null) {
            LOGGER.debug("[BEHAVIOR] no BuildExecutor registered -- cannot assign task for"
                + " intent kind {}", kindFor(intent));
            return null;
        }
        if (intent instanceof BuildIntent) {
            return new BuildTask(UUID.randomUUID(), intent, citizen, exec);
        }
        if (intent instanceof UpgradeIntent) {
            return new UpgradeTask(UUID.randomUUID(), intent, citizen, exec);
        }
        return null;
    }

    // --- kind classification ---------------------------------------------------------

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

    /**
     * World-aware NPC supplier. Reads the level from the bound engine's
     * {@code currentLevel} field, which is set by {@link #onServerTick} on every
     * server tick. Returns empty results outside a tick -- callers that hold a
     * supplier across ticks must tolerate this.
     */
    static final class WorldNpcSupplier implements NpcSupplier {
        private BehaviorEngine engine;

        void bind(BehaviorEngine engine) {
            this.engine = engine;
        }

        @Override
        public java.util.List<Npc> freeCitizens(Town town) {
            ServerLevel level = currentLevel();
            if (level == null || town == null) return java.util.List.of();
            java.util.List<Npc> result = new java.util.ArrayList<>();
            for (UUID id : town.getBuilderNpcIds()) {
                if (id == null) continue;
                Entity e = level.getEntity(id);
                if (e instanceof Npc npc) {
                    result.add(npc);
                }
            }
            return result;
        }

        @Override
        public Optional<Npc> findByUuid(UUID id) {
            ServerLevel level = currentLevel();
            if (level == null) return Optional.empty();
            Entity e = level.getEntity(id);
            return e instanceof Npc npc ? Optional.of(npc) : Optional.empty();
        }

        private ServerLevel currentLevel() {
            return engine == null ? null : engine.currentLevel;
        }
    }
}
