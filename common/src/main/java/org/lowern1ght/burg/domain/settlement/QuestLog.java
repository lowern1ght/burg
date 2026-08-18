package org.lowern1ght.burg.domain.settlement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A town's quest log — the third value object in the Settlement bounded
 * context (after {@code StandingBook} and {@code StockLedger}) and the
 * strangler-side analogue of {@code Town.activeQuests} plus
 * {@code Town.questDefLastCompleted}. The log is a Minecraft-free shape
 * the domain layer can reason about without a {@code net.minecraft}
 * import on the classpath.
 *
 * <p>The log carries two pieces of state the engine tracks today:
 * <ul>
 *   <li>an ordered list of {@link QuestRef} entries — the quest
 *       definitions currently visible to the town, drawn from
 *       {@code Town.activeQuests} (and, for completed-but-still-tracked
 *       TASK defs, from {@code Town.questDefLastCompleted}).</li>
 *   <li>a {@code defId → tick} map of last-completion timestamps for
 *       the TASK defs this town has ever finished — the engine uses
 *       this to space refreshes for repeatable tasks.</li>
 * </ul>
 *
 * <p>Both pieces are immutable. Every mutator ({@link #withAdded},
 * {@link #withRemoved}, {@link #withCompleted}) returns a new log. The
 * empty log is the referentially-stable {@link #EMPTY} sentinel that
 * older worlds and a fresh {@code Town()} both fall back to (additive
 * NBT default — old saves load with an empty {@code ActiveQuests} list
 * and an absent {@code QuestDefLastCompleted} compound, both of which
 * collapse to the {@link #EMPTY} sentinel in the rebuild).
 *
 * <p>Mutators are intentionally narrow. {@link #withAdded} appends a
 * ref to the entry list (the engine's "spawn this quest" path).
 * {@link #withRemoved} drops a ref by {@code defId} (the engine's
 * "complete this quest" path — the underlying Task tick removes the
 * quest from {@code Town.activeQuests}). {@link #withCompleted} sets
 * or updates the {@code lastCompleted} tick for a TASK def (the
 * engine's "stamp the completion time" path). The mutators do not
 * cross-reference each other — the engine is the source of truth and
 * calls them in the order its state machine demands.
 *
 * <p>No Minecraft imports. The {@link QuestRef} is the value-object
 * wrapper; the {@code Town} facade builds the log from
 * {@code activeQuests} + {@code questDefLastCompleted} on every call
 * to {@code Town.questLog()}.
 */
public final class QuestLog {

    /**
     * Empty log — the additive default for worlds saved before this
     * carve and for a freshly constructed {@code Town()} that has not
     * accepted any quests yet. Referentially stable so equality checks
     * elsewhere are cheap.
     */
    public static final QuestLog EMPTY = new QuestLog(List.of(), Map.of());

    private final List<QuestRef> entries;
    private final Map<String, Long> lastCompleted;

    private QuestLog(List<QuestRef> entries, Map<String, Long> lastCompleted) {
        this.entries = entries;
        this.lastCompleted = lastCompleted;
    }

    /**
     * Returns the last completion tick for {@code defId}, or {@code 0}
     * when the def has never been completed by this town. The log is
     * sparse: a defId never seen reads as zero, never as "absent".
     */
    public long lastCompletedFor(String defId) {
        Objects.requireNonNull(defId, "defId");
        Long t = lastCompleted.get(defId);
        return t != null ? t : 0L;
    }

    /**
     * Returns the first ref whose {@code defId} equals {@code defId}, or
     * {@code null} when no such ref is on the log. Order is the
     * roll's stored order; the first match is the engine's intended
     * target.
     */
    public QuestRef findById(String defId) {
        Objects.requireNonNull(defId, "defId");
        for (QuestRef ref : entries) {
            if (defId.equals(ref.defId())) return ref;
        }
        return null;
    }

    /**
     * Returns a new log with {@code ref} appended to the entry list. If
     * the roll already holds a ref with the same {@code defId}, the
     * existing entry is replaced (the engine treats defId as the
     * primary key — re-spawning a quest supersedes the old one).
     */
    public QuestLog withAdded(QuestRef ref) {
        Objects.requireNonNull(ref, "ref");
        if (entries.isEmpty()) return new QuestLog(List.of(ref), lastCompleted);
        List<QuestRef> next = new ArrayList<>(entries.size() + 1);
        boolean replaced = false;
        for (QuestRef existing : entries) {
            if (existing.defId().equals(ref.defId())) {
                next.add(ref);
                replaced = true;
            } else {
                next.add(existing);
            }
        }
        if (!replaced) next.add(ref);
        return new QuestLog(Collections.unmodifiableList(next), lastCompleted);
    }

    /**
     * Returns a new log with the ref whose {@code defId} equals
     * {@code defId} removed. If no such ref exists, the returned log is
     * unchanged.
     */
    public QuestLog withRemoved(String defId) {
        Objects.requireNonNull(defId, "defId");
        if (entries.isEmpty()) return this;
        List<QuestRef> next = new ArrayList<>(entries.size());
        boolean removed = false;
        for (QuestRef ref : entries) {
            if (!removed && defId.equals(ref.defId())) {
                removed = true;
                continue;
            }
            next.add(ref);
        }
        if (!removed) return this;
        return next.isEmpty() && lastCompleted.isEmpty() ? EMPTY : new QuestLog(Collections.unmodifiableList(next), lastCompleted);
    }

    /**
     * Returns a new log with the {@code lastCompleted} entry for
     * {@code defId} set to {@code tick}. Existing entries for other
     * defs are untouched. A negative tick is rejected: completion
     * timestamps are game time and never go back.
     */
    public QuestLog withCompleted(String defId, long tick) {
        Objects.requireNonNull(defId, "defId");
        if (tick < 0L) {
            throw new IllegalArgumentException(
                "QuestLog.withCompleted requires a non-negative tick (got " + tick + ")");
        }
        if (lastCompleted.isEmpty()) {
            Map<String, Long> next = Map.of(defId, tick);
            return new QuestLog(entries, next);
        }
        Map<String, Long> next = new LinkedHashMap<>(lastCompleted);
        next.put(defId, tick);
        return new QuestLog(entries, Collections.unmodifiableMap(next));
    }

    /** True iff no ref is on the roll and no completion has been recorded. */
    public boolean isEmpty() {
        return entries.isEmpty() && lastCompleted.isEmpty();
    }

    /** Number of refs on the entry list. */
    public int size() {
        return entries.size();
    }

    /** Read-only view of the entries, in roll order. */
    public List<QuestRef> entries() {
        return entries;
    }

    /** Read-only view of the {@code defId → tick} completion map. */
    public Map<String, Long> lastCompleted() {
        return lastCompleted;
    }

    /**
     * Builds a log from an entry list and a completion map. Both
     * arguments are defensively copied; a {@code null} argument is
     * treated as empty. The constructor is the last line of defence
     * against a malformed source — a {@code null} ref in the list or
     * a {@code null} defId / negative tick in the map is rejected.
     */
    public static QuestLog of(List<QuestRef> sourceEntries, Map<String, Long> sourceLastCompleted) {
        List<QuestRef> entriesCopy = sourceEntries == null || sourceEntries.isEmpty()
            ? List.of()
            : List.copyOf(sourceEntries);
        Map<String, Long> next;
        if (sourceLastCompleted == null || sourceLastCompleted.isEmpty()) {
            next = Map.of();
        } else {
            Map<String, Long> copy = new LinkedHashMap<>();
            for (Map.Entry<String, Long> e : sourceLastCompleted.entrySet()) {
                Objects.requireNonNull(e.getKey(), "lastCompleted key");
                Long v = e.getValue();
                if (v == null || v < 0L) continue;
                copy.put(e.getKey(), v);
            }
            next = copy.isEmpty() ? Map.of() : Collections.unmodifiableMap(copy);
        }
        if (entriesCopy.isEmpty() && next.isEmpty()) return EMPTY;
        return new QuestLog(Collections.unmodifiableList(entriesCopy), next);
    }
}
