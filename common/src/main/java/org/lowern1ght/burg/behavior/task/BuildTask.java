package org.lowern1ght.burg.behavior.task;

import org.lowern1ght.burg.behavior.executor.BuildExecutor;
import org.lowern1ght.burg.behavior.intent.BuildIntent;
import org.lowern1ght.burg.behavior.intent.TownIntent;
import org.lowern1ght.burg.entity.Npc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * A {@link CitizenTask} that drives a new building construction to completion.
 *
 * <p>This is the behaviour-engine counterpart to the existing {@code BuildGoal} in the
 * entity-AI layer. The goal is the executor for the legacy intent path; this task is the
 * executor for the new intent+task pipeline. They both end up calling the same
 * {@code Town.tryAddToConstructionQueue} seam (via the {@link BuildExecutor} interface), so
 * the underlying plan (target, origin, cost, is-instant, prepare-blocks) is shared.
 *
 * <p><b>Phase 2 wire:</b> the task is a thin wrapper over the existing
 * {@code Town} construction-queue API rather than its own per-tick block placer. On the
 * first tick the task delegates the work to {@link BuildExecutor#tryQueueNewBuild}, and on
 * subsequent ticks it reads the live {@link BuildExecutor#isPlaced} check to detect when
 * the legacy pipeline has finished placing the building. The actual MOVING / BUILDING
 * phases are still driven by {@code BuildGoal}; the engine observes and reports state.
 *
 * <p><b>Phase 7 morale:</b> each tick advances {@link #progress()} by
 * {@link #BASE_RATE} multiplied by the assignee's morale multiplier (linear 0.5x at
 * 0 morale to 1.5x at 100 morale via {@link CitizenTask#moraleMultiplier}). When
 * {@link BuildExecutor#isPlaced} returns true the progress is forced to 1.0 — the
 * real-world completion signal wins over the math.
 *
 * <p><b>State machine (Phase 7):</b>
 * <pre>
 *   PENDING --(tick 1, tryQueueNewBuild ok)--> STARTED --(tick 2)--> IN_PROGRESS
 *                                                                  |
 *                                                                  v
 *                                                                DONE  (progress >= 1.0
 *                                                                      or isPlaced)
 * </pre>
 *
 * <p>This layering is deliberately incremental. The new engine is opt-in (a town with no
 * {@link BuildIntent} enqueued has no task and no behaviour change) and the new task does
 * not duplicate the existing per-tick block placement, which would have been a near-total
 * rewrite of {@code BuildGoal}. A future phase can promote this task to a first-class
 * driver; the API surface is small enough that the change is local.
 */
public final class BuildTask implements CitizenTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildTask.class);

    /** Per-tick increment at morale=50 (multiplier 1.0). 10 ticks = full completion. */
    private static final float BASE_RATE = 0.1f;
    /** Small progress bump on the PENDING tick so diagnostics see the task move off zero. */
    private static final float INITIAL_BUMP = 0.05f;

    private final UUID id;
    private final TownIntent source;
    private final Npc assignee;
    private final BuildExecutor executor;
    private TaskState state;
    private float progress = 0f;

    public BuildTask(UUID id, TownIntent source, Npc assignee, BuildExecutor executor) {
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

        // Citizen gone (chunk unloaded past timeout, removed, etc.) -- the legacy pipeline
        // would not be able to make progress either.
        if (assignee == null || !assignee.isAlive() || assignee.isRemoved()) {
            state = TaskState.FAILED;
            return state;
        }

        if (!(source instanceof BuildIntent bi)) {
            state = TaskState.FAILED;
            return state;
        }
        if (bi.town() == null) {
            state = TaskState.FAILED;
            return state;
        }

        // PENDING -> STARTED: delegate to the BuildExecutor to enqueue the build. The legacy
        // pipeline (SimpleStateMachine + BuildGoal) takes over from there.
        if (state == TaskState.PENDING) {
            String placer = assignee.getUUID().toString();
            boolean enqueued = executor.tryQueueNewBuild(bi.town(), bi.buildingDefId(), placer);
            if (!enqueued) {
                LOGGER.debug("[BEHAVIOR] BuildTask {} could not enqueue building='{}' (executor"
                    + " rejected -- queue full, unaffordable, weight cap exceeded, etc.) -- FAILED",
                    id, bi.buildingDefId());
                state = TaskState.FAILED;
                return state;
            }
            state = TaskState.STARTED;
            progress = INITIAL_BUMP;
            LOGGER.debug("[BEHAVIOR] BuildTask {} enqueued building='{}' for citizen {} -- PENDING ->"
                + " STARTED (legacy pipeline takes over)",
                id, bi.buildingDefId(), assignee.getUUID());
        }

        // STARTED -> IN_PROGRESS: next tick after the enqueue. The task is now monitoring
        // the legacy pipeline; progress advances each tick by BASE_RATE * moraleMultiplier.
        if (state == TaskState.STARTED) {
            state = TaskState.IN_PROGRESS;
        }

        if (state == TaskState.IN_PROGRESS) {
            float mult = moraleMultiplier(ctx.morale(), assignee);
            progress = Math.min(1.0f, progress + BASE_RATE * mult);

            // Force-complete if the building has been placed -- the real-world completion
            // signal wins over the math-driven progress.
            if (executor.isPlaced(bi.town(), bi.buildingDefId())) {
                progress = 1.0f;
                LOGGER.debug("[BEHAVIOR] BuildTask {} -- building='{}' now placed, forcing"
                    + " progress=1.0", id, bi.buildingDefId());
            }

            if (progress >= 1.0f) {
                state = TaskState.DONE;
                LOGGER.debug("[BEHAVIOR] BuildTask {} -- progress=1.0, DONE (placed={})",
                    id, executor.isPlaced(bi.town(), bi.buildingDefId()));
            }
        }
        return state;
    }
}
