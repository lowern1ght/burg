package org.lowern1ght.burg.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mutation-style invariants for {@link ConstructionQueue}: strict FIFO,
 * capacity enforced at the boundary, and the EMPTY sentinel preserved
 * across drains. Kills mutants like a dequeue that drops the tail
 * instead of the head, an off-by-one capacity check, or an of() that
 * appends instead of clips.
 */
class ConstructionQueueMutationTest {

    private static ConstructionIntent.NewBuild build(long entryId) {
        return new ConstructionIntent.NewBuild(entryId, "burg:house_" + entryId);
    }

    @Test
    @DisplayName("the empty queue: size 0, no head, no capacity use, empty view")
    void emptyQueueDefaults() {
        assertAll(
            () -> assertSame(ConstructionQueue.EMPTY, ConstructionQueue.of(List.of()),
                "of(emptyList) is the EMPTY sentinel"),
            () -> assertEquals(0, ConstructionQueue.EMPTY.size()),
            () -> assertTrue(ConstructionQueue.EMPTY.isEmpty()),
            () -> assertNull(ConstructionQueue.EMPTY.peek(),
                "peek on an empty queue is null, not an exception"),
            () -> assertTrue(ConstructionQueue.EMPTY.entries().isEmpty()),
            () -> assertEquals(ConstructionQueue.CAPACITY, ConstructionQueue.EMPTY.capacity()),
            () -> assertTrue(ConstructionQueue.EMPTY.hasCapacity())
        );
    }

    @Test
    @DisplayName("dequeue on an empty queue fails fast")
    void dequeueOnEmptyThrows() {
        assertThrows(NoSuchElementException.class, ConstructionQueue.EMPTY::dequeue);
    }

    @Test
    @DisplayName("enqueue appends at the tail; dequeue removes the head — strict FIFO")
    void fifoOrderPreserved() {
        ConstructionIntent.NewBuild a = build(1);
        ConstructionIntent.NewBuild b = build(2);
        ConstructionIntent.NewBuild c = build(3);

        ConstructionQueue queue = ConstructionQueue.EMPTY
            .enqueue(a)
            .enqueue(b)
            .enqueue(c);

        assertAll(
            () -> assertEquals(List.of(a, b, c), queue.entries(),
                "insertion order is the iteration order"),
            () -> assertSame(a, queue.peek(), "the head is the oldest entry")
        );

        ConstructionQueue afterOne = queue.dequeue();
        ConstructionQueue afterTwo = afterOne.enqueue(build(4)).dequeue().dequeue();

        assertAll(
            () -> assertEquals(List.of(b, c), afterOne.entries(),
                "dequeue removed a, not c (kills a tail-drop mutant)"),
            () -> assertEquals(List.of(build(4)), afterTwo.entries(),
                "draining b then c leaves the post-drain enqueue at the head")
        );
    }

    @Test
    @DisplayName("a single entry drained to zero collapses to the EMPTY sentinel")
    void drainToEmptyCollapses() {
        ConstructionQueue drained = ConstructionQueue.EMPTY.enqueue(build(1)).dequeue();

        assertSame(ConstructionQueue.EMPTY, drained,
            "referential stability across drains — the sentinel comes back");
    }

    @Test
    @DisplayName("enqueue never mutates the receiver — the original queue keeps its entry")
    void enqueueIsImmutable() {
        ConstructionQueue one = ConstructionQueue.EMPTY.enqueue(build(1));
        ConstructionQueue two = one.enqueue(build(2));

        assertAll(
            () -> assertEquals(1, one.size(), "the receiver is unchanged"),
            () -> assertEquals(2, two.size()),
            () -> assertEquals(build(1), one.peek())
        );
    }

    @Test
    @DisplayName("capacity is enforced at enqueue: 54 fit, the 55th throws")
    void capacityBoundary() {
        ConstructionQueue queue = ConstructionQueue.EMPTY;
        for (long i = 0; i < ConstructionQueue.CAPACITY; i++) {
            queue = queue.enqueue(build(i));
        }

        ConstructionQueue full = queue;
        assertEquals(ConstructionQueue.CAPACITY, full.size());
        assertFalse(full.hasCapacity(), "a full queue reports no capacity");
        assertThrows(IllegalStateException.class, () -> full.enqueue(build(99)),
            "the 55th enqueue fails fast");

        ConstructionQueue afterDequeue = full.dequeue();
        assertTrue(afterDequeue.hasCapacity(),
            "one dequeue reopens exactly one slot");
        assertEquals(ConstructionQueue.CAPACITY, afterDequeue.enqueue(build(99)).size());
    }

    @Test
    @DisplayName("of() clips an oversized list to CAPACITY, keeping the head")
    void ofClipsOversizedList() {
        List<ConstructionIntent> oversized = new ArrayList<>();
        for (long i = 0; i < ConstructionQueue.CAPACITY + 6; i++) {
            oversized.add(build(i));
        }

        ConstructionQueue clipped = ConstructionQueue.of(oversized);

        assertAll(
            () -> assertEquals(ConstructionQueue.CAPACITY, clipped.size()),
            () -> assertEquals(0L, clipped.entries().get(0).entryId(),
                "the head of the source survives the clip"),
            () -> assertEquals((long) ConstructionQueue.CAPACITY - 1,
                clipped.entries().get(ConstructionQueue.CAPACITY - 1).entryId(),
                "the clip keeps the first CAPACITY entries, dropping the tail")
        );
    }

    @Test
    @DisplayName("of() preserves order for an in-capacity list and copies defensively")
    void ofPreservesOrderAndCopies() {
        ConstructionIntent.NewBuild a = build(1);
        ConstructionIntent.NewBuild b = build(2);
        List<ConstructionIntent> source = new ArrayList<>(List.of(a, b));

        ConstructionQueue queue = ConstructionQueue.of(source);
        source.add(build(3));

        assertAll(
            () -> assertEquals(List.of(a, b), queue.entries(),
                "post-construction mutation of the source does not leak in"),
            () -> assertEquals(2, queue.size())
        );
    }

    @Test
    @DisplayName("entries() is unmodifiable — the queue cannot be mutated through its view")
    void entriesAreUnmodifiable() {
        ConstructionQueue queue = ConstructionQueue.of(List.of(build(1)));

        assertThrows(UnsupportedOperationException.class,
            () -> queue.entries().add(build(2)));
    }

    @Test
    @DisplayName("enqueue rejects a null intent")
    void enqueueRejectsNull() {
        assertThrows(NullPointerException.class,
            () -> ConstructionQueue.EMPTY.enqueue(null));
    }

    @Test
    @DisplayName("NewBuild and Upgrade intents coexist in one queue and keep their shapes")
    void mixedIntentShapes() {
        ConstructionIntent.NewBuild fresh = new ConstructionIntent.NewBuild(1, "burg:sawmill");
        ConstructionIntent.Upgrade upgrade = new ConstructionIntent.Upgrade(
            2, "burg:house", "1234567890", 1);

        ConstructionQueue queue = ConstructionQueue.EMPTY.enqueue(fresh).enqueue(upgrade);

        ConstructionIntent head = queue.peek();
        assertNotNull(head);
        assertAll(
            () -> assertSame(fresh, head),
            () -> assertEquals(2, queue.size()),
            () -> assertEquals(upgrade, queue.entries().get(1))
        );
    }
}
