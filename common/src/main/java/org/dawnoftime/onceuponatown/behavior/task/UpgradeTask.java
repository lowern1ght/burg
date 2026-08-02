package org.dawnoftime.onceuponatown.behavior.task;

import org.dawnoftime.onceuponatown.behavior.executor.BuildExecutor;
import org.dawnoftime.onceuponatown.behavior.intent.TownIntent;
import org.dawnoftime.onceuponatown.behavior.intent.UpgradeIntent;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.town.PlacedBuilding;
import org.dawnoftime.onceuponatown.town.Town;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * A {@link CitizenTask} that applies a level-up to a placed building.
 *
 * <p>Mirrors {@link BuildTask}: on the first tick the task delegates the upgrade through
 * {@link BuildExecutor#tryQueueUpgrade}, and on subsequent ticks it watches the live town
 * state for the upgrade to land. The actual visual diff + entity spawn is still driven by
 * {@code UpgradeAction} via the existing {@code SimpleStateMachine} pipeline; the engine
 * observes and reports state.
 *
 * <p><b>Phase 7 morale:</b> progress grows by {@link #BASE_RATE} per tick scaled by the
 * assignee's morale multiplier. When the building's upgrade level strictly exceeds
 * {@link #baselineLevel} the task force-completes -- the real-world completion signal wins
 * over the math, the same way {@link BuildTask} treats {@code isPlaced}.
 *
 * <p><b>State machine (Phase 7):</b>
 * <pre>
 *   PENDING --(tick 1, tryQueueUpgrade ok)--> STARTED --(tick 2)--> IN_PROGRESS
 *                                                                   |
 *                                                                   v
 *                                                                 DONE  (progress >= 1.0
 *                                                                       or level > baseline)
 * </pre>
 */
public final class UpgradeTask implements CitizenTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpgradeTask.class);

    /** Per-tick increment at morale=50 (multiplier 1.0). 10 ticks = full completion. */
    private static final float BASE_RATE = 0.1f;
    /** Small progress bump on the PENDING tick so diagnostics see the task move off zero. */
    private static final float INITIAL_BUMP = 0.05f;

    private final UUID id;
    private final TownIntent source;
    private final Npc assignee;
    private final BuildExecutor executor;
    private TaskState state;
    // Snapshot of the building's upgrade level at enqueue time. The task is DONE when
    // the level strictly exceeds this baseline. (UpgradeIntent doesn't carry fromLevel;
    // we read it once when the upgrade is accepted.)
    private int baselineLevel = -1;
    private float progress = 0f;

    public UpgradeTask(UUID id, TownIntent source, Npc assignee, BuildExecutor executor) {
        this.id = id;
        this.source = source;
        this.assignee = assignee;
        this.executor = executor;
        this.state = TaskState.PENDING;
    }

    @Override public UUID id() { return id; }
    @Override public TownIntent source() { return source; }
    @Override public Npc assignee() { return assignee; }
    @Override public TaskState state() { return state; }
    @Override public float progress() { return progress; }
    @Override public boolean isInterruptible() { return state == TaskState.STARTED; }

    @Override
    public TaskState tick(TaskContext ctx) {
        if (state.isTerminal()) return state;

        if (assignee == null || !assignee.isAlive() || assignee.isRemoved()) {
            state = TaskState.FAILED;
            return state;
        }

        if (!(source instanceof UpgradeIntent ui)) {
            state = TaskState.FAILED;
            return state;
        }
        Town town = ui.town();
        if (town == null) {
            state = TaskState.FAILED;
            return state;
        }

        // Find the building at the upgrade target position. Inlined because private helpers
        // in production code are forbidden by the project's code-shape rule.
        PlacedBuilding building = null;
        for (PlacedBuilding b : town.getBuildings()) {
            if (ui.buildingPos().equals(b.worldPos)) { building = b; break; }
        }
        if (building == null) {
            LOGGER.debug("[BEHAVIOR] UpgradeTask {} -- building at {} no longer exists, FAILED",
                id, ui.buildingPos());
            state = TaskState.FAILED;
            return state;
        }

        // PENDING -> STARTED: snapshot the building's current upgrade level BEFORE the legacy
        // pipeline runs, so the done-check has a stable baseline even if the queue is
        // processed out of order. Then delegate the upgrade to the BuildExecutor.
        if (state == TaskState.PENDING) {
            baselineLevel = building.getUpgradeLevel();

            String placer = assignee.getUUID().toString();
            boolean enqueued = executor.tryQueueUpgrade(town, ui.buildingPos(), placer);
            if (!enqueued) {
                LOGGER.debug("[BEHAVIOR] UpgradeTask {} could not enqueue upgrade for building at {}"
                    + " (executor rejected -- missing, max-level, or already queued) -- FAILED",
                    id, ui.buildingPos());
                state = TaskState.FAILED;
                return state;
            }
            state = TaskState.STARTED;
            progress = INITIAL_BUMP;
            LOGGER.debug("[BEHAVIOR] UpgradeTask {} enqueued upgrade for building at {} baseline={}"
                + " -- PENDING -> STARTED (legacy pipeline takes over)",
                id, ui.buildingPos(), baselineLevel);
        }

        // STARTED -> IN_PROGRESS: subsequent ticks advance progress.
        if (state == TaskState.STARTED) {
            state = TaskState.IN_PROGRESS;
        }

        if (state == TaskState.IN_PROGRESS) {
            float mult = moraleMultiplier(ctx.morale(), assignee);
            progress = Math.min(1.0f, progress + BASE_RATE * mult);

            // Force-complete when the building's upgrade level strictly exceeds the
            // baseline -- the real-world completion signal wins over the math.
            if (building.getUpgradeLevel() > baselineLevel) {
                progress = 1.0f;
                LOGGER.debug("[BEHAVIOR] UpgradeTask {} -- building at {} upgraded past baseline"
                    + " {}, forcing progress=1.0",
                    id, ui.buildingPos(), baselineLevel);
            }

            if (progress >= 1.0f) {
                state = TaskState.DONE;
                LOGGER.debug("[BEHAVIOR] UpgradeTask {} -- progress=1.0, DONE (level={})",
                    id, ui.buildingPos(), building.getUpgradeLevel());
            }
        }
        return state;
    }
}
