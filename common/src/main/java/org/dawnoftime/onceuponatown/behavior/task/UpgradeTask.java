package org.dawnoftime.onceuponatown.behavior.task;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.dawnoftime.onceuponatown.behavior.intent.TownIntent;
import org.dawnoftime.onceuponatown.behavior.intent.UpgradeIntent;
import org.dawnoftime.onceuponatown.building.schematic.SchematicBlock;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.entity.ai.BuildAction;
import org.dawnoftime.onceuponatown.town.PlacedBuilding;
import org.dawnoftime.onceuponatown.town.Town;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * A {@link CitizenTask} that applies a level-up to a placed building.
 *
 * <p>Mirrors {@link BuildTask}: on the first tick the task delegates the upgrade to the
 * existing public API ({@link Town#tryQueueUpgrade(BlockPos)}), and on subsequent ticks
 * it watches the live {@link Town} state for the upgrade to land. The actual visual
 * diff + entity spawn is still driven by {@code UpgradeAction} via the existing
 * {@code SimpleStateMachine} pipeline; the engine observes and reports state.
 */
public final class UpgradeTask implements CitizenTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpgradeTask.class);

    private final UUID id;
    private final TownIntent source;
    private final Npc assignee;
    private final BuildAction action;
    private TaskState state;
    // Snapshot of the building's upgrade level at enqueue time. The task is DONE when
    // the level strictly exceeds this baseline. (UpgradeIntent doesn't carry fromLevel;
    // we read it once when the upgrade is accepted.)
    private int baselineLevel = -1;

    public UpgradeTask(UUID id, TownIntent source, Npc assignee, BuildAction action) {
        this.id = id;
        this.source = source;
        this.assignee = assignee;
        this.action = action;
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

        if (state == TaskState.PENDING) {
            return onFirstTick();
        }

        return onProgressTick();
    }

    private TaskState onFirstTick() {
        if (!(source instanceof UpgradeIntent ui)) {
            state = TaskState.FAILED;
            return state;
        }
        Town town = ui.town();
        if (town == null) {
            state = TaskState.FAILED;
            return state;
        }

        // Snapshot the building's current upgrade level BEFORE the legacy pipeline runs,
        // so the done-check has a stable baseline even if the queue is processed out of order.
        PlacedBuilding building = findBuilding(town, ui.buildingPos());
        if (building == null) {
            LOGGER.debug("[BEHAVIOR] UpgradeTask {} -- building at {} no longer exists, FAILED",
                id, ui.buildingPos());
            state = TaskState.FAILED;
            return state;
        }
        baselineLevel = building.getUpgradeLevel();

        boolean enqueued = town.tryQueueUpgrade(ui.buildingPos());
        if (!enqueued) {
            LOGGER.debug("[BEHAVIOR] UpgradeTask {} could not enqueue upgrade for building at {}"
                + " (missing, max-level, or already queued) -- FAILED",
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

    private TaskState onProgressTick() {
        if (!(source instanceof UpgradeIntent ui)) {
            state = TaskState.FAILED;
            return state;
        }
        Town town = ui.town();
        if (town == null) {
            state = TaskState.FAILED;
            return state;
        }
        PlacedBuilding building = findBuilding(town, ui.buildingPos());
        if (building == null) {
            state = TaskState.FAILED;
            return state;
        }
        if (building.getUpgradeLevel() > baselineLevel) {
            state = TaskState.DONE;
            LOGGER.debug("[BEHAVIOR] UpgradeTask {} -- building at {} upgraded {} -> {}, DONE",
                id, ui.buildingPos(), baselineLevel, building.getUpgradeLevel());
            return state;
        }
        if (action != null && action.isFailed()) {
            state = TaskState.FAILED;
            return state;
        }
        return state;
    }

    private static PlacedBuilding findBuilding(Town town, BlockPos pos) {
        for (PlacedBuilding b : town.getBuildings()) {
            if (pos.equals(b.worldPos)) return b;
        }
        return null;
    }

    // No-op BuildAction stub. See BuildTask.MONITOR for the rationale.
    public static final BuildAction MONITOR = new BuildAction() {
        @Override public BlockPos getTargetPos() { return BlockPos.ZERO; }
        @Override public BlockPos getOrigin() { return BlockPos.ZERO; }
        @Override public boolean isInstant() { return false; }
        @Override public boolean executeInstant(ServerLevel level, Npc npc) { return false; }
        @Override public List<SchematicBlock> prepareBlocks(ServerLevel level, Npc npc) { return List.of(); }
        @Override public void onComplete(ServerLevel level, Npc npc) {}
        @Override public boolean isFailed() { return false; }
        @Override public void saveTo(CompoundTag tag) {}
    };
}
