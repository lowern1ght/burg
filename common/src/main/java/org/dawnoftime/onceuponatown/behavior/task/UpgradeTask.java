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
 */
public final class UpgradeTask implements CitizenTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpgradeTask.class);

    private final UUID id;
    private final TownIntent source;
    private final Npc assignee;
    private final BuildExecutor executor;
    private TaskState state;
    // Snapshot of the building's upgrade level at enqueue time. The task is DONE when
    // the level strictly exceeds this baseline. (UpgradeIntent doesn't carry fromLevel;
    // we read it once when the upgrade is accepted.)
    private int baselineLevel = -1;

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

        if (state == TaskState.PENDING) {
            // Snapshot the building's current upgrade level BEFORE the legacy pipeline runs,
            // so the done-check has a stable baseline even if the queue is processed out of order.
            baselineLevel = building.getUpgradeLevel();

            String placer = assignee != null ? assignee.getUUID().toString() : null;
            boolean enqueued = executor.tryQueueUpgrade(town, ui.buildingPos(), placer);
            if (!enqueued) {
                LOGGER.debug("[BEHAVIOR] UpgradeTask {} could not enqueue upgrade for building at {}"
                    + " (executor rejected -- missing, max-level, or already queued) -- FAILED",
                    id, ui.buildingPos());
                state = TaskState.FAILED;
                return state;
            }
            state = TaskState.IN_PROGRESS;
            LOGGER.debug("[BEHAVIOR] UpgradeTask {} enqueued upgrade for building at {} baseline={}"
                + " -- PENDING -> IN_PROGRESS (legacy pipeline takes over)",
                id, ui.buildingPos(), baselineLevel);
            return state;
        }

        // IN_PROGRESS: monitor the legacy pipeline.
        if (building.getUpgradeLevel() > baselineLevel) {
            state = TaskState.DONE;
            LOGGER.debug("[BEHAVIOR] UpgradeTask {} -- building at {} upgraded {} -> {}, DONE",
                id, ui.buildingPos(), baselineLevel, building.getUpgradeLevel());
            return state;
        }
        return state;
    }
}
