package org.dawnoftime.onceuponatown.behavior.task;

import org.dawnoftime.onceuponatown.behavior.intent.TownIntent;
import org.dawnoftime.onceuponatown.entity.Npc;

import java.util.UUID;

/**
 * A {@link CitizenTask} for a single line of dialogue — the settler greets, complains, or
 * relays news. Implemented in a later phase; until then the task immediately reports
 * {@link TaskState#FAILED}.
 */
public final class SpeakTask implements CitizenTask {

    private final UUID id;
    private final TownIntent source;
    private final Npc assignee;
    private TaskState state;

    public SpeakTask(UUID id, TownIntent source, Npc assignee) {
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
