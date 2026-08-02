package org.dawnoftime.onceuponatown.behavior.task;

import net.minecraft.server.level.ServerLevel;
import org.dawnoftime.onceuponatown.behavior.intent.TownIntent;
import org.dawnoftime.onceuponatown.building.schematic.SchematicBlock;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.entity.ai.BuildAction;

import java.util.List;
import java.util.UUID;

/**
 * A {@link CitizenTask} that drives a {@link BuildAction} to completion.
 *
 * <p>This is the behaviour-engine counterpart to the existing {@code BuildGoal} in the
 * entity-AI layer. The goal is the executor for the legacy intent path; this task is the
 * executor for the new intent+task pipeline. They both call the same {@link BuildAction}
 * surface, so the underlying plan (target, origin, cost, is-instant, prepare-blocks) is
 * shared.
 *
 * <p>Phase 1 is a skeleton: the task walks the action's lifecycle — MOVING, BUILDING,
 * DONE — but does not yet place blocks tick-by-tick. That layering is the next step. Until
 * then the task reports {@link TaskState#IN_PROGRESS} through the BUILDING phase and only
 * flips to DONE after the action's {@code prepareBlocks} returns an empty list.
 */
public final class BuildTask implements CitizenTask {

    private final UUID id;
    private final TownIntent source;
    private final Npc assignee;
    private final BuildAction action;
    private TaskState state;
    private List<SchematicBlock> blocks;

    public BuildTask(UUID id, TownIntent source, Npc assignee, BuildAction action) {
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

        // Phase 1 skeleton: we don't yet place blocks per tick. We just walk the action's
        // lifecycle and report status. The block-by-block placement lands in the next phase.
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

        // Real placement is deferred to the entity-AI layer (BuildGoal). Until that hook is
        // wired in, the task spins in IN_PROGRESS so the engine knows something is happening.
        return state;
    }
}
