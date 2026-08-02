package org.dawnoftime.onceuponatown.behavior.task;

import org.dawnoftime.onceuponatown.behavior.executor.BuildExecutor;
import org.dawnoftime.onceuponatown.behavior.intent.BuildIntent;
import org.dawnoftime.onceuponatown.behavior.intent.TownIntent;
import org.dawnoftime.onceuponatown.entity.Npc;
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
 * <p><b>Phase 2 wire (this revision):</b> the task is a thin wrapper over the existing
 * {@code Town} construction-queue API rather than its own per-tick block placer. On the
 * first tick the task delegates the work to {@link BuildExecutor#tryQueueNewBuild}, and on
 * subsequent ticks it reads the live {@link BuildExecutor#isPlaced} check to detect when
 * the legacy pipeline has finished placing the building. The actual MOVING / BUILDING
 * phases are still driven by {@code BuildGoal}; the engine observes and reports state.
 *
 * <p>This layering is deliberately incremental. The new engine is opt-in (a town with no
 * {@link BuildIntent} enqueued has no task and no behaviour change) and the new task does
 * not duplicate the existing per-tick block placement, which would have been a near-total
 * rewrite of {@code BuildGoal}. A future phase can promote this task to a first-class
 * driver; the API surface is small enough that the change is local.
 */
public final class BuildTask implements CitizenTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildTask.class);

    private final UUID id;
    private final TownIntent source;
    private final Npc assignee;
    private final BuildExecutor executor;
    private TaskState state;

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

        if (state == TaskState.PENDING) {
            // First tick: delegate to the BuildExecutor to enqueue the build. The legacy
            // pipeline (SimpleStateMachine + BuildGoal) takes over from there.
            String placer = assignee != null ? assignee.getUUID().toString() : null;
            boolean enqueued = executor.tryQueueNewBuild(bi.town(), bi.buildingDefId(), placer);
            if (!enqueued) {
                LOGGER.debug("[BEHAVIOR] BuildTask {} could not enqueue building='{}' (executor"
                    + " rejected -- queue full, unaffordable, weight cap exceeded, etc.) -- FAILED",
                    id, bi.buildingDefId());
                state = TaskState.FAILED;
                return state;
            }
            state = TaskState.IN_PROGRESS;
            LOGGER.debug("[BEHAVIOR] BuildTask {} enqueued building='{}' for citizen {} -- PENDING ->"
                + " IN_PROGRESS (legacy pipeline takes over)", id, bi.buildingDefId(), assignee.getUUID());
            return state;
        }

        // IN_PROGRESS: monitor the legacy pipeline.
        if (executor.isPlaced(bi.town(), bi.buildingDefId())) {
            state = TaskState.DONE;
            LOGGER.debug("[BEHAVIOR] BuildTask {} -- building='{}' now placed, DONE",
                id, bi.buildingDefId());
            return state;
        }
        return state;
    }
}
