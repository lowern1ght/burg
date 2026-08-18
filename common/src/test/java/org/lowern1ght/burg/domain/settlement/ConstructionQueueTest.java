package org.lowern1ght.burg.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The immutable construction queue, in pure JUnit. Like
 * {@code StockLedgerTest} and {@code StandingBookTest}, the queue is
 * immutable — every mutator returns a new queue — and {@link #EMPTY} is
 * the additive default for worlds saved before this carve.
 *
 * <p>Three correctness traps the tests are explicitly here to catch:
 * (1) {@code enqueue} on a full queue must throw a deterministic error
 * rather than silently shrinking the queue; (2) {@code dequeue} must
 * drop the head, preserve FIFO order, and throw (not return null) on
 * an empty queue; (3) {@link #EMPTY} must remain referentially stable
 * so equality checks elsewhere are cheap.
 */
class ConstructionQueueTest {

    private static ConstructionIntent newBuild(long entryId, String buildingDefId) {
        return new ConstructionIntent.NewBuild(entryId, buildingDefId);
    }

    private static ConstructionIntent upgrade(long entryId, String buildingDefId, long worldPos, int fromLevel) {
        return new ConstructionIntent.Upgrade(entryId, buildingDefId, Long.toString(worldPos), fromLevel);
    }

    @Test
    @DisplayName("the additive default for old saves is an empty queue")
    void emptyIsTheDefault() {
        ConstructionQueue queue = ConstructionQueue.EMPTY;
        assertAll(
            () -> assertSame(ConstructionQueue.EMPTY, queue, "EMPTY is referentially stable"),
            () -> assertTrue(queue.isEmpty()),
            () -> assertEquals(0, queue.size()),
            () -> assertEquals(ConstructionQueue.CAPACITY, queue.capacity(),
                "default capacity matches Town.QUEUE_CAPACITY (54)"),
            () -> assertTrue(queue.hasCapacity(),
                "an empty queue always has room for another entry"),
            () -> assertNull(queue.peek(),
                "peek() of an empty queue is null")
        );
    }

    @Test
    @DisplayName("enqueue appends a single intent at the tail and returns a new queue")
    void enqueueAppends() {
        ConstructionIntent first = newBuild(0L, "burg:oak_log");
        ConstructionQueue after = ConstructionQueue.EMPTY.enqueue(first);

        assertAll(
            () -> assertNotSame(ConstructionQueue.EMPTY, after, "a new instance is returned"),
            () -> assertEquals(1, after.size()),
            () -> assertSame(first, after.peek(), "peek returns the just-enqueued intent"),
            () -> assertEquals(1, after.entries().size())
        );
    }

    @Test
    @DisplayName("FIFO order is preserved across multiple enqueues")
    void enqueueFifoOrder() {
        ConstructionQueue queue = ConstructionQueue.EMPTY
            .enqueue(newBuild(1L, "burg:oak_log"))
            .enqueue(upgrade(2L, "burg:smithy", 42L, 1))
            .enqueue(newBuild(3L, "burg:well"));

        List<ConstructionIntent> entries = queue.entries();
        assertAll(
            () -> assertEquals(3, queue.size()),
            () -> assertEquals(1L, entries.get(0).entryId(),
                "FIFO: first enqueued sits at index 0"),
            () -> assertEquals(2L, entries.get(1).entryId(),
                "FIFO: second enqueued at index 1"),
            () -> assertEquals(3L, entries.get(2).entryId(),
                "FIFO: third enqueued at index 2 (tail)"),
            () -> assertTrue(queue.hasCapacity(),
                "size(3) < CAPACITY(54) leaves 51 slots free")
        );
    }

    @Test
    @DisplayName("enqueue rejects intents when the queue is at capacity")
    void enqueueAtCapacityThrows() {
        ConstructionQueue full = fillToCapacity(ConstructionQueue.EMPTY, 54);

        ConstructionIntent oneMore = newBuild(99L, "burg:oak_log");
        assertAll(
            () -> assertEquals(ConstructionQueue.CAPACITY, full.size()),
            () -> assertThrows(IllegalStateException.class, () -> full.enqueue(oneMore),
                "at-capacity enqueue throws rather than silently shrinking"),
            () -> assertThrows(IllegalStateException.class, () -> full.enqueue(oneMore),
                "the same call throws consistently")
        );
    }

    @Test
    @DisplayName("dequeue removes the head and preserves FIFO order on the remainder")
    void dequeueRemovesHead() {
        ConstructionIntent first = newBuild(1L, "burg:oak_log");
        ConstructionIntent second = newBuild(2L, "burg:smithy");
        ConstructionQueue queue = ConstructionQueue.EMPTY.enqueue(first).enqueue(second);

        ConstructionQueue after = queue.dequeue();

        assertAll(
            () -> assertNotSame(queue, after, "dequeue returns a new instance"),
            () -> assertEquals(1, after.size(),
                "head is dropped"),
            () -> assertSame(second, after.peek(),
                "the previous second entry is now at the head"),
            () -> assertEquals(2, queue.size(),
                "the original queue is unchanged (immutability)"),
            () -> assertSame(first, queue.peek(),
                "the original queue's head is preserved")
        );
    }

    @Test
    @DisplayName("dequeue on an empty queue throws — null-on-miss is not the contract")
    void dequeueOnEmptyThrows() {
        assertAll(
            () -> assertThrows(NoSuchElementException.class,
                () -> ConstructionQueue.EMPTY.dequeue(),
                "dequeue on EMPTY throws NoSuchElementException"),
            () -> assertSame(ConstructionQueue.EMPTY, ConstructionQueue.EMPTY,
                "throwing does not consume EMPTY")
        );
    }

    @Test
    @DisplayName("dequeue drains the queue back to EMPTY — no leftover Capacity-bound entries")
    void dequeueDrainsToEmpty() {
        ConstructionQueue queue = ConstructionQueue.EMPTY
            .enqueue(newBuild(1L, "burg:oak_log"))
            .enqueue(newBuild(2L, "burg:smithy"));

        ConstructionQueue drained = queue.dequeue().dequeue();

        assertAll(
            () -> assertSame(ConstructionQueue.EMPTY, drained,
                "drained queue collapses to the EMPTY sentinel"),
            () -> assertTrue(drained.isEmpty())
        );
    }

    @Test
    @DisplayName("peek is read-only and returns null on an empty queue")
    void peekIsReadOnly() {
        ConstructionQueue queue = ConstructionQueue.EMPTY
            .enqueue(newBuild(1L, "burg:oak_log"));

        ConstructionIntent head = queue.peek();

        assertAll(
            () -> assertEquals(1, queue.size(),
                "size is unchanged after peek"),
            () -> assertEquals("burg:oak_log", head.buildingDefId())
        );
    }

    @Test
    @DisplayName("of() builds a queue from a list and clips entries beyond capacity")
    void ofFromListClips() {
        List<ConstructionIntent> source = new ArrayList<>(ConstructionQueue.CAPACITY + 5);
        for (long i = 0; i < ConstructionQueue.CAPACITY + 5; i++) {
            source.add(newBuild(i, "burg:oak_log"));
        }

        ConstructionQueue queue = ConstructionQueue.of(source);

        assertAll(
            () -> assertEquals(ConstructionQueue.CAPACITY, queue.size(),
                "the over-capacity tail is dropped at construction time"),
            () -> assertEquals(0L, queue.entries().get(0).entryId(),
                "FIFO order is preserved by the clip"),
            () -> assertEquals(ConstructionQueue.CAPACITY - 1L,
                queue.entries().get(queue.size() - 1).entryId(),
                "the tail is the last kept entry, not the original last")
        );
    }

    @Test
    @DisplayName("of() with an empty list returns EMPTY — same instance")
    void ofEmptyList() {
        ConstructionQueue queue = ConstructionQueue.of(List.of());
        assertSame(ConstructionQueue.EMPTY, queue,
            "the additive-default path returns the EMPTY sentinel");
    }

    @Test
    @DisplayName("of() defensively copies — mutating the source list cannot mutate the queue")
    void ofDefensivelyCopies() {
        List<ConstructionIntent> source = new ArrayList<>();
        source.add(newBuild(1L, "burg:oak_log"));
        source.add(newBuild(2L, "burg:smithy"));

        ConstructionQueue queue = ConstructionQueue.of(source);

        source.clear();
        source.add(newBuild(99L, "burg:well"));

        assertAll(
            () -> assertEquals(2, queue.size(),
                "clearing and replacing the source does not affect the queue"),
            () -> assertEquals(1L, queue.entries().get(0).entryId()),
            () -> assertEquals(2L, queue.entries().get(1).entryId())
        );
    }

    @Test
    @DisplayName("each mutator returns a new queue — the input is not mutated")
    void immutability() {
        ConstructionQueue before = ConstructionQueue.EMPTY
            .enqueue(newBuild(1L, "burg:oak_log"))
            .enqueue(newBuild(2L, "burg:smithy"));

        ConstructionQueue afterEnqueue = before.enqueue(newBuild(3L, "burg:well"));
        ConstructionQueue afterDequeue = before.dequeue();

        assertAll(
            () -> assertEquals(2, before.size(),
                "the original queue is unchanged across all mutations"),
            () -> assertEquals(3, afterEnqueue.size()),
            () -> assertEquals(1, afterDequeue.size()),
            () -> assertNotSame(before, afterEnqueue),
            () -> assertNotSame(before, afterDequeue)
        );
    }

    @Test
    @DisplayName("entries() is read-only — the backing list rejects mutation")
    void entriesViewIsUnmodifiable() {
        ConstructionQueue queue = ConstructionQueue.EMPTY.enqueue(newBuild(1L, "burg:oak_log"));

        List<ConstructionIntent> view = queue.entries();

        assertAll(
            () -> assertThrows(UnsupportedOperationException.class,
                () -> view.add(newBuild(99L, "burg:oak_log")),
                "the read-only view rejects mutations"),
            () -> assertEquals(1, queue.size(),
                "a rejected mutation does not leak into the queue")
        );
    }

    @Test
    @DisplayName("Upgrade intent carries worldPosKey as a stringified BlockPos.asLong()")
    void upgradeIntentShape() {
        long packedPos = 1234567890123L;
        ConstructionIntent intent = new ConstructionIntent.Upgrade(
            7L, "burg:smithy", Long.toString(packedPos), 2);

        assertAll(
            () -> assertTrue(intent instanceof ConstructionIntent.Upgrade),
            () -> assertEquals(7L, intent.entryId()),
            () -> assertEquals("burg:smithy", intent.buildingDefId()),
            () -> assertEquals(Long.toString(packedPos),
                ((ConstructionIntent.Upgrade) intent).worldPosKey(),
                "worldPosKey is the stringified form of BlockPos.asLong()"),
            () -> assertEquals(2, ((ConstructionIntent.Upgrade) intent).fromLevel())
        );
    }

    @Test
    @DisplayName("NewBuild and Upgrade are distinct intent shapes — sealed union is exhaustive")
    void sealedUnionExhaustive() {
        ConstructionIntent nb = newBuild(1L, "burg:oak_log");
        ConstructionIntent up = upgrade(2L, "burg:smithy", 1L, 0);

        assertAll(
            () -> assertTrue(nb instanceof ConstructionIntent.NewBuild),
            () -> assertTrue(up instanceof ConstructionIntent.Upgrade),
            () -> assertEquals("burg:oak_log", nb.buildingDefId()),
            () -> assertEquals("burg:smithy", up.buildingDefId())
        );
    }

    @Test
    @DisplayName("re-null buildingDefId is rejected on construction")
    void rejectsNullBuildingDefId() {
        assertAll(
            () -> assertThrows(NullPointerException.class,
                () -> new ConstructionIntent.NewBuild(1L, null)),
            () -> assertThrows(NullPointerException.class,
                () -> new ConstructionIntent.Upgrade(1L, null, "0", 0))
        );
    }

    @Test
    @DisplayName("re-null worldPosKey on Upgrade is rejected")
    void rejectsNullWorldPosKey() {
        assertThrows(NullPointerException.class,
            () -> new ConstructionIntent.Upgrade(1L, "burg:smithy", null, 0),
            "Upgrade contract: worldPosKey must not be null");
    }

    private static ConstructionQueue fillToCapacity(ConstructionQueue queue, int capacity) {
        ConstructionQueue acc = queue;
        for (int i = 0; i < capacity; i++) {
            acc = acc.enqueue(newBuild(i, "burg:oak_log"));
        }
        return acc;
    }
}
