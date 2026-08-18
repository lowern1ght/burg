package org.lowern1ght.burg.behavior.task;

/**
 * The lifecycle state of a {@link CitizenTask}.
 *
 * <p>A task moves through these in roughly the order they appear below; the only transitions
 * the engine actually checks are "is this terminal?" (DONE, FAILED, INTERRUPTED) and
 * "should I keep ticking?" (everything else).
 */
public enum TaskState {
    /** Queued but not yet started. The NPC has not picked it up. */
    PENDING,
    /** The NPC has accepted the task and is moving to the start position. */
    STARTED,
    /** The task is mid-flight. The engine keeps ticking it. */
    IN_PROGRESS,
    /** The task finished successfully. Terminal. */
    DONE,
    /** The task failed. The engine logs the reason and switches the NPC to an idle task. */
    FAILED,
    /** The task was interrupted by a higher-priority intent. Terminal. */
    INTERRUPTED;

    public boolean isTerminal() {
        return this == DONE || this == FAILED || this == INTERRUPTED;
    }
}
