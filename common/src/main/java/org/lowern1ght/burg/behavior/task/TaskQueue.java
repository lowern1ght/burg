package org.lowern1ght.burg.behavior.task;

import org.lowern1ght.burg.entity.Npc;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-citizen task queue: one active task at a time, plus a waiting line for the next intent.
 *
 * <p>The engine calls {@link #assign} when a citizen is matched with an intent. If the
 * citizen already has an active task, the new one replaces it ({@link #reassign} semantics)
 * and the previous task is dropped — interruption is the assumption, since the matching
 * logic only assigns when the citizen was free. Other producers (the player's manual
 * commands, a quest hook) can layer their own semantics on top.
 *
 * <p>The queue is keyed by citizen UUID. The public API takes {@link Npc}; a UUID-keyed
 * overload exists for callers — including tests — that can't or don't want to instantiate
 * an entity.
 *
 * <p>Internal structure is plain {@link HashMap} and {@link ArrayDeque}. Nothing exotic: the
 * sizes are one per citizen that ticks, the operations are O(1) amortized.
 */
public final class TaskQueue {

    /**
     * The key the queue looks up by. NPC -> UUID conversion goes through {@link Npc#getUUID()}.
     *
     * @param npcId the citizen's UUID; never null
     * @param task the task currently assigned to that citizen; never null
     */
    public record ActiveTask(UUID npcId, CitizenTask task) {}

    private final Map<UUID, ActiveTask> active = new HashMap<>();
    private final Map<UUID, Deque<CitizenTask>> waiting = new HashMap<>();

    // --- assign ---------------------------------------------------------------------------

    public void assign(Npc npc, CitizenTask task) {
        assignToId(npc.getUUID(), task);
    }

    /**
     * Lower-level form of {@link #assign(Npc, CitizenTask)} that takes the citizen's UUID
     * directly. Useful for tests and for any code that wants to schedule work for a citizen
     * that isn't currently in the world (e.g. one persisted in NBT).
     */
    public void assignToId(UUID npcId, CitizenTask task) {
        active.put(npcId, new ActiveTask(npcId, task));
    }

    // --- current --------------------------------------------------------------------------

    public Optional<CitizenTask> currentTask(Npc npc) {
        return currentTaskForId(npc.getUUID());
    }

    public Optional<CitizenTask> currentTaskForId(UUID npcId) {
        ActiveTask at = active.get(npcId);
        return at == null ? Optional.empty() : Optional.of(at.task());
    }

    /** All (npcId, task) pairs currently active. Read-only view. */
    public Collection<ActiveTask> allActive() {
        return java.util.Collections.unmodifiableCollection(active.values());
    }

    // --- reassign -------------------------------------------------------------------------

    /**
     * Reassigns the citizen to a new task, dropping whatever was active. The previous task
     * is not notified — the engine fires {@link CitizenTask#onComplete} from the tick loop
     * before calling this.
     */
    public void reassign(Npc npc, CitizenTask task) {
        reassignToId(npc.getUUID(), task);
    }

    public void reassignToId(UUID npcId, CitizenTask task) {
        active.put(npcId, new ActiveTask(npcId, task));
    }

    // --- complete -------------------------------------------------------------------------

    /**
     * Marks the citizen's current task complete with the given final state and promotes the
     * next waiting task if any. The current task is removed from the active map before the
     * promotion so the queue can never end up holding both the just-completed and the just-
     * promoted task.
     */
    public void complete(Npc npc, TaskState finalState) {
        completeForId(npc.getUUID(), finalState);
    }

    public void completeForId(UUID npcId, TaskState finalState) {
        active.remove(npcId);
        CitizenTask promoted = pollWaiting(npcId);
        if (promoted != null) {
            active.put(npcId, new ActiveTask(npcId, promoted));
        }
        // The finalState argument is what the caller passes to the removed task's
        // onComplete. The queue itself does not call onComplete; the engine does, after
        // this method returns.
        if (finalState == null) {
            throw new IllegalArgumentException("finalState must not be null");
        }
    }

    // --- waiting (queue of next tasks) ----------------------------------------------------

    /** Appends a task to the citizen's waiting line. The line is FIFO. */
    public void enqueueWaiting(Npc npc, CitizenTask task) {
        enqueueWaitingForId(npc.getUUID(), task);
    }

    public void enqueueWaitingForId(UUID npcId, CitizenTask task) {
        waiting.computeIfAbsent(npcId, id -> new ArrayDeque<>()).add(task);
    }

    /** Returns the citizen's current waiting line, or empty. */
    public Deque<CitizenTask> waitingFor(Npc npc) {
        return waitingForId(npc.getUUID());
    }

    public Deque<CitizenTask> waitingForId(UUID npcId) {
        Deque<CitizenTask> q = waiting.get(npcId);
        return q == null ? new ArrayDeque<>() : q;
    }

    private CitizenTask pollWaiting(UUID npcId) {
        Deque<CitizenTask> q = waiting.get(npcId);
        if (q == null || q.isEmpty()) return null;
        CitizenTask next = q.poll();
        if (q.isEmpty()) waiting.remove(npcId);
        return next;
    }

    /** Returns the size of the active map. Useful for tests and metrics. */
    public int activeCount() {
        return active.size();
    }
}
