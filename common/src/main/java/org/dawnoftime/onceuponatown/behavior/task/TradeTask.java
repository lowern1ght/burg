package org.dawnoftime.onceuponatown.behavior.task;

import org.dawnoftime.onceuponatown.behavior.intent.TownIntent;
import org.dawnoftime.onceuponatown.entity.Npc;

import java.util.UUID;

/**
 * A {@link CitizenTask} for a settler running their trade at a workplace. Implemented in
 * Phase BEHAVIOR-2. Until then the task immediately reports {@link TaskState#FAILED}.
 */
public final class TradeTask implements CitizenTask {

    private final UUID id;
    private final TownIntent source;
    private final Npc assignee;
    private TaskState state;

    public TradeTask(UUID id, TownIntent source, Npc assignee) {
        this.id = id;
        this.source = source;
        this.assignee = assignee;
        this.state = TaskState.PENDING;
    }

    @Override public UUID id() { return id; }
    @Override public TownIntent source() { return source; }
    @Override public Npc assignee() { return assignee; }
    @Override public TaskState state() { return state; }
    @Override public boolean isInterruptible() { return true; }

    @Override
    public TaskState tick(TaskContext ctx) {
        state = TaskState.FAILED;
        return state;
    }
}
