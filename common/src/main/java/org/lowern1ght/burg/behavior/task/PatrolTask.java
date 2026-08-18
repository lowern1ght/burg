package org.lowern1ght.burg.behavior.task;

import org.lowern1ght.burg.behavior.intent.TownIntent;
import org.lowern1ght.burg.entity.Npc;

import java.util.UUID;

/**
 * A {@link CitizenTask} for walking a perimeter — wall patrol, herd escort, perimeter
 * check. Implemented in Phase BEHAVIOR-5 alongside defence. Until then the task
 * immediately reports {@link TaskState#FAILED}.
 */
public final class PatrolTask implements CitizenTask {

    private final UUID id;
    private final TownIntent source;
    private final Npc assignee;
    private TaskState state;

    public PatrolTask(UUID id, TownIntent source, Npc assignee) {
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
