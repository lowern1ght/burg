package org.dawnoftime.onceuponatown.behavior.task;

import net.minecraft.server.level.ServerLevel;
import org.dawnoftime.onceuponatown.behavior.intent.TownIntent;
import org.dawnoftime.onceuponatown.building.schematic.SchematicBlock;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.entity.ai.BuildAction;

import java.util.List;
import java.util.UUID;

/**
 * A {@link CitizenTask} that applies a level-up to a placed building.
 *
 * <p>Mirrors {@link BuildTask} but the underlying {@link BuildAction} is an upgrade plan
 * (apply the visual diff between two NBT levels rather than place a full template). The
 * skeleton is identical: walk the lifecycle, defer per-tick placement to the entity-AI
 * layer.
 */
public final class UpgradeTask implements CitizenTask {

    private final UUID id;
    private final TownIntent source;
    private final Npc assignee;
    private final BuildAction action;
    private TaskState state;
    private List<SchematicBlock> blocks;

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
        ServerLevel level = ctx.level();

        if (action.isInstant()) {
            if (action.executeInstant(level, assignee)) {
                action.onComplete(level, assignee);
                state = TaskState.DONE;
            }
            return state;
        }

        if (blocks == null) {
            blocks = action.prepareBlocks(level, assignee);
            state = TaskState.IN_PROGRESS;
        }

        if (blocks.isEmpty() || action.isFailed()) {
            action.onComplete(level, assignee);
            state = action.isFailed() ? TaskState.FAILED : TaskState.DONE;
            return state;
        }

        return state;
    }
}
