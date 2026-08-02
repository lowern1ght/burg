package org.dawnoftime.onceuponatown.behavior.task;

import org.dawnoftime.onceuponatown.behavior.intent.TownIntent;
import org.dawnoftime.onceuponatown.behavior.morale.MoraleState;
import org.dawnoftime.onceuponatown.entity.Npc;

import java.util.UUID;

/**
 * A unit of work that a citizen is performing.
 *
 * <p>Tasks are the <i>how</i> of the behaviour engine. An intent declares what a town wants;
 * a task is the concrete plan for a specific citizen to carry it out. Tasks own the per-tick
 * machinery — movement, block placement, dialogue, whatever the kind demands — and report
 * their state back to the engine.
 *
 * <p>The {@code permits} list is the full set of task kinds in the engine. Adding a new
 * task kind is a two-step: declare the class, then extend the permits list. The engine
 * never type-checks at runtime; the compiler enforces exhaustiveness.
 */
public sealed interface CitizenTask
        permits BuildTask, UpgradeTask, PathTask, TradeTask,
                SpeakTask, PatrolTask, IdleTask {

    /** Stable id for this task instance. Used by the queue and the log. */
    UUID id();

    /**
     * The intent that caused this task to exist, or null for an {@link IdleTask} (which has
     * no origin intent — it is the default state for a citizen with nothing to do).
     */
    TownIntent source();

    /** The citizen doing this work. */
    Npc assignee();

    /** Current lifecycle state. Updated by {@link #tick}. */
    TaskState state();

    /** Advances the task one step. Returns the new state. */
    TaskState tick(TaskContext ctx);

    /**
     * Progress multiplier based on the assignee's morale. Default: linear 0.5x at
     * 0 morale to 1.5x at 100 morale. Concrete tasks can override for
     * different curves or to disable morale effects.
     */
    default float moraleMultiplier(MoraleState morale, Npc assignee) {
        if (assignee == null || morale == null) return 1.0f;
        int value = morale.valueFor(assignee.getUUID());
        return 0.5f + (value / 100.0f);
    }

    /**
     * True if a higher-priority intent can preempt this task. Walking across the map is
     * interruptible; placing a block is not (the half-placed building would be invalid).
     */
    boolean isInterruptible();

    /** Hook fired when the task reaches a terminal state. Default no-op. */
    default void onComplete(TaskContext ctx, TaskState finalState) {}
}
