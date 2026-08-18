package org.lowern1ght.burg.domain.settlement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * The town-level roll of queued {@link ConstructionIntent}s, in the
 * Settlement bounded context. Immutable ordered list:
 * {@link #enqueue(ConstructionIntent)} appends at the tail and returns a
 * new queue; {@link #dequeue()} removes the head and returns a new queue;
 * {@link #size()} / {@link #capacity()} / {@link #hasCapacity()} are
 * inspection. Mutations follow the same recipe
 * {@code StandingBook} and {@code StockLedger} established
 * (ADR-0009 / ADR-0010): the queue is rebuilt wholesale on every change,
 * so the {@code Town} facade can hand out a thread-safe snapshot without
 * copy-on-write wrappers.
 *
 * <p>Capacity ({@value #CAPACITY}, matching {@code Town.QUEUE_CAPACITY})
 * is enforced at enqueue time — exceeding it throws
 * {@link IllegalStateException}, the same fast-fail discipline
 * {@link StockLedger#take} uses. {@link #of(List)} defensively clips a
 * source list down to capacity at construction time so the persisted
 * form stays bounded the same way the persisted stock form stays
 * sparse. {@link #EMPTY} is a referentially-stable sentinel — the
 * additive default for worlds saved before this carve landed (matches
 * {@code StockLedger.EMPTY} and {@code StandingBook.EMPTY}).
 *
 * <p>No Minecraft imports. The {@link ConstructionIntent} sealed type is
 * the only entry shape (ADR-0008 §"Minecraft types leave the domain").
 * The {@code Town} aggregate root continues to own the
 * {@code List<QueueEntry> constructionQueue} field for NBT
 * round-tripping; {@code Town.constructionQueueView()} is the
 * read-only strangler-side view rebuilt from that list at the edge —
 * the same asymmetric pattern ADR-0010 §"What this does NOT do
 * (today)" set up for the stock ledger.
 */
public final class ConstructionQueue {

    /**
     * Default queue capacity. Matches {@code Town.QUEUE_CAPACITY} (a
     * 6×9 grid of slots, matching {@code TownHubMenu.CHEST_SIZE}); the
     * two are not wired together at compile time — the carve is
     * additive, the legacy constant stays where it is.
     */
    public static final int CAPACITY = 54;

    /** Empty queue — the additive default for worlds saved before this carve. */
    public static final ConstructionQueue EMPTY = new ConstructionQueue(List.of(), CAPACITY);

    private final List<ConstructionIntent> entries;
    private final int capacity;

    private ConstructionQueue(List<ConstructionIntent> entries, int capacity) {
        this.entries = entries;
        this.capacity = capacity;
    }

    /**
     * Returns a new queue with {@code intent} appended at the tail.
     * {@link IllegalStateException} when the queue is at capacity.
     */
    public ConstructionQueue enqueue(ConstructionIntent intent) {
        Objects.requireNonNull(intent, "intent");
        if (!hasCapacity()) {
            throw new IllegalStateException(
                "ConstructionQueue.enqueue: at capacity (" + capacity + ")");
        }
        List<ConstructionIntent> next = new ArrayList<>(entries.size() + 1);
        next.addAll(entries);
        next.add(intent);
        return new ConstructionQueue(Collections.unmodifiableList(next), capacity);
    }

    /**
     * Returns a new queue with the head removed.
     * {@link NoSuchElementException} when the queue is empty — symmetric
     * with {@link java.util.ArrayDeque#remove()} and the same
     * fast-fail discipline {@link StockLedger#take} uses elsewhere.
     * A dequeue that drains the queue to zero entries collapses to
     * {@link #EMPTY} so referential stability is preserved across drains
     * (mirrors the {@code StockLedger.take} → EMPTY sentinel pattern).
     */
    public ConstructionQueue dequeue() {
        if (entries.isEmpty()) {
            throw new NoSuchElementException("ConstructionQueue.dequeue: empty");
        }
        List<ConstructionIntent> tail = entries.subList(1, entries.size());
        if (tail.isEmpty()) return EMPTY;
        return new ConstructionQueue(List.copyOf(tail), capacity);
    }

    /**
     * Returns the head {@link ConstructionIntent} without removing it,
     * or {@code null} when the queue is empty. The same null-on-miss
     * convention {@code Collection.peek}-style queues use; callers that
     * prefer a typed miss should check {@link #isEmpty()} first.
     */
    public ConstructionIntent peek() {
        return entries.isEmpty() ? null : entries.get(0);
    }

    /** Current entry count. */
    public int size() {
        return entries.size();
    }

    /** Maximum number of entries this queue accepts ({@link #CAPACITY} for the default). */
    public int capacity() {
        return capacity;
    }

    /** True iff there is room for at least one more entry. */
    public boolean hasCapacity() {
        return entries.size() < capacity;
    }

    /** True iff the queue holds no entries. */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Read-only, FIFO-ordered view of every entry in insertion order.
     * The backing list is unmodifiable; callers cannot mutate the queue
     * through it. Index 0 is the head, index {@code size() - 1} is the
     * tail.
     */
    public List<ConstructionIntent> entries() {
        return entries;
    }

    /**
     * Builds a queue from a list, clipping entries beyond
     * {@link #CAPACITY} at construction time. Empty input yields
     * {@link #EMPTY}; defensive copy + unmodifiable wrap on the result
     * so the caller cannot mutate the queue through the input.
     */
    public static ConstructionQueue of(List<ConstructionIntent> source) {
        Objects.requireNonNull(source, "source");
        if (source.isEmpty()) return EMPTY;
        List<ConstructionIntent> clipped = source.size() > CAPACITY
            ? List.copyOf(source.subList(0, CAPACITY))
            : List.copyOf(source);
        if (clipped.isEmpty()) return EMPTY;
        return new ConstructionQueue(clipped, CAPACITY);
    }
}
