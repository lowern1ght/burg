package org.dawnoftime.onceuponatown.behavior.task;

import org.dawnoftime.onceuponatown.behavior.intent.TownIntent;
import org.dawnoftime.onceuponatown.entity.Npc;

import java.util.UUID;

/**
 * The default {@link CitizenTask} for a citizen with nothing to do.
 *
 * <p>An idle task has no source intent and no work to do. {@link #tick} flips it to
 * {@link TaskState#DONE} on the first call so the engine doesn't keep ticking it. The
 * engine treats "no task" and "idle task done" the same way — the citizen is free for the
 * next intent.
 */
public final class IdleTask implements CitizenTask {

    private final UUID id;
    private final Npc assignee;
    private TaskState state;

    public IdleTask(Npc assignee) {
        this.id = UUID.randomUUID();
        this.assignee = assignee;
        this.state = TaskState.PENDING;
    }

    @Override public UUID id() { return id; }
    @Override public TownIntent source() { return null; }
    @Override public Npc assignee() { return assignee; }
    @Override public TaskState state() { return state; }
    @Override public boolean isInterruptible() { return true; }

    @Override
    public TaskState tick(TaskContext ctx) {
        state = TaskState.DONE;
        return state;
    }
}
