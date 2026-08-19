package org.lowern1ght.burg.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tester edge cases for {@link ConstructionQueue}: the capacity boundary
 * from both sides (enqueue past full, of() clipping), the peek/dequeue
 * consistency contract, rotation order after interleaved ops, entryId
 * extremes, and the NewBuild-vs-Upgrade equality traps.
 */
class ConstructionQueueEdgeCasesTest {

    private static ConstructionIntent.NewBuild build(long entryId, String defId) {
        return new ConstructionIntent.NewBuild(entryId, defId);
    }

    private static ConstructionIntent.Upgrade upgrade(long entryId, String defId, String pos, int level) {
        return new ConstructionIntent.Upgrade(entryId, defId, pos, level);
    }

    @Test
    @DisplayName("enqueue(null) is rejected — a blank intent never enters the queue")
    void enqueueNullRejected() {
        assertThrows(NullPointerException.class, () -> ConstructionQueue.EMPTY.enqueue(null));
    }

    @Test
    @DisplayName("the capacity boundary: CAPACITY-1 accepts, CAPACITY is full, and the (CAPACITY+1)-th enqueue throws")
    void capacityBoundary() {
        ConstructionQueue queue = ConstructionQueue.EMPTY;
        for (long i = 0; i < ConstructionQueue.CAPACITY - 1; i++) {
            queue = queue.enqueue(build(i, "burg:x"));
        }

        ConstructionQueue last = queue.enqueue(build(53, "burg:last"));
        assertAll(
            () -> assertEquals(ConstructionQueue.CAPACITY, last.size()),
            () -> assertTrue(!last.hasCapacity(), "the 54th slot fills the queue"),
            () -> assertThrows(IllegalStateException.class,
                () -> last.enqueue(build(54, "burg:overflow")),
                "the 55th enqueue throws"),
            () -> assertEquals(ConstructionQueue.CAPACITY, last.size(),
                "the rejected enqueue did not shrink or grow the queue"),
            () -> assertEquals(53L, last.entries().get(last.size() - 1).entryId(),
                "the tail is still the last accepted entry")
        );
    }

    @Test
    @DisplayName("a dequeue from a full queue frees exactly one slot")
    void dequeueFreesCapacity() {
        ConstructionQueue full = ConstructionQueue.EMPTY;
        for (long i = 0; i < ConstructionQueue.CAPACITY; i++) {
            full = full.enqueue(build(i, "burg:x"));
        }

        ConstructionQueue freed = full.dequeue();

        assertAll(
            () -> assertTrue(freed.hasCapacity(),
                "one dequeue re-opens one slot"),
            () -> assertEquals(ConstructionQueue.CAPACITY - 1, freed.size()),
            () -> assertEquals(1L, freed.peek().entryId(),
                "entry 0 was the head that got dequeued")
        );
    }

    @Test
    @DisplayName("of() with exactly CAPACITY entries yields a full queue; one fewer leaves room")
    void ofExactCapacityBoundary() {
        List<ConstructionIntent> exactly = new ArrayList<>();
        for (long i = 0; i < ConstructionQueue.CAPACITY; i++) {
            exactly.add(build(i, "burg:x"));
        }
        ConstructionQueue full = ConstructionQueue.of(exactly);
        assertTrue(!full.hasCapacity(), "a source of exactly CAPACITY is full");

        List<ConstructionIntent> oneShort = exactly.subList(0, ConstructionQueue.CAPACITY - 1);
        ConstructionQueue almost = ConstructionQueue.of(oneShort);
        assertAll(
            () -> assertTrue(almost.hasCapacity()),
            () -> assertEquals(ConstructionQueue.CAPACITY, almost.capacity(),
                "a clipped or exact build always carries the default capacity")
        );
    }

    @Test
    @DisplayName("of() keeps the HEAD when clipping — the oldest intent survives, not the newest")
    void ofClipKeepsHead() {
        List<ConstructionIntent> source = new ArrayList<>();
        for (long i = 0; i < ConstructionQueue.CAPACITY + 2; i++) {
            source.add(build(i, "burg:x"));
        }

        ConstructionQueue clipped = ConstructionQueue.of(source);

        assertAll(
            () -> assertEquals(0L, clipped.peek().entryId(),
                "the head is the first-ever intent"),
            () -> assertEquals(ConstructionQueue.CAPACITY - 1L,
                clipped.entries().get(clipped.size() - 1).entryId(),
                "the clip drops the newest overflow, not the oldest work")
        );
    }

    @Test
    @DisplayName("of(null element) fails loudly at construction")
    void ofRejectsNullElements() {
        List<ConstructionIntent> source = new ArrayList<>();
        source.add(build(1L, "burg:x"));
        source.add(null);

        assertThrows(NullPointerException.class, () -> ConstructionQueue.of(source),
            "a null intent in the source list is a hard failure");
    }

    @Test
    @DisplayName("peek and dequeue agree — what you peek is exactly what dequeue removes")
    void peekDequeueConsistency() {
        ConstructionQueue queue = ConstructionQueue.EMPTY
            .enqueue(build(1L, "burg:first"))
            .enqueue(build(2L, "burg:second"))
            .enqueue(build(3L, "burg:third"));

        ConstructionIntent peeked = queue.peek();
        ConstructionQueue afterDequeue = queue.dequeue();

        assertAll(
            () -> assertEquals(false, afterDequeue.entries().contains(peeked),
                "dequeue removed exactly the peeked entry"),
            () -> assertNotEquals(peeked, afterDequeue.peek(),
                "after dequeue the peek moves to the next entry"),
            () -> assertEquals(2L, afterDequeue.peek().entryId())
        );
    }

    @Test
    @DisplayName("rotation order: enqueue a, enqueue b, dequeue, enqueue c → [b, c]")
    void rotationOrder() {
        ConstructionQueue queue = ConstructionQueue.EMPTY
            .enqueue(build(1L, "burg:a"))
            .enqueue(build(2L, "burg:b"))
            .dequeue()
            .enqueue(build(3L, "burg:c"));

        List<Long> ids = queue.entries().stream().map(ConstructionIntent::entryId).toList();
        assertEquals(List.of(2L, 3L), ids,
            "a dequeue-then-enqueue rotates: b is the head, c is the tail");
    }

    @Test
    @DisplayName("entryId extremes are plain longs — MIN, -1, 0, MAX all queue and compare by value")
    void entryIdExtremes() {
        ConstructionQueue queue = ConstructionQueue.EMPTY
            .enqueue(build(Long.MIN_VALUE, "burg:min"))
            .enqueue(build(-1L, "burg:neg"))
            .enqueue(build(0L, "burg:zero"))
            .enqueue(build(Long.MAX_VALUE, "burg:max"));

        List<Long> ids = queue.entries().stream().map(ConstructionIntent::entryId).toList();
        assertEquals(
            List.of(Long.MIN_VALUE, -1L, 0L, Long.MAX_VALUE),
            ids,
            "ids are stored verbatim in FIFO order — no normalisation, no reordering");
    }

    @Test
    @DisplayName("the same intent can be enqueued twice — the queue is a list, not a set")
    void duplicateIntentsAllowed() {
        ConstructionIntent twice = build(1L, "burg:same");
        ConstructionQueue queue = ConstructionQueue.EMPTY
            .enqueue(twice)
            .enqueue(twice);

        assertAll(
            () -> assertEquals(2, queue.size(),
                "an identical intent appears twice — dedup is the caller's job"),
            () -> assertSame(twice, queue.entries().get(0)),
            () -> assertSame(twice, queue.entries().get(1))
        );
    }

    @Test
    @DisplayName("NewBuild and Upgrade with the same entryId + defId are NOT equal — the shape is part of identity")
    void newBuildAndUpgradeAreDistinct() {
        ConstructionIntent.NewBuild newBuild = build(7L, "burg:smithy");
        ConstructionIntent.Upgrade upgrade = upgrade(7L, "burg:smithy", "42", 0);

        assertAll(
            () -> assertNotEquals(newBuild, upgrade,
                "same id and defId, different shape ⇒ unequal"),
            () -> assertEquals(newBuild, build(7L, "burg:smithy")),
            () -> assertEquals(upgrade, upgrade(7L, "burg:smithy", "42", 0)),
            () -> assertEquals(upgrade.hashCode(), upgrade(7L, "burg:smithy", "42", 0).hashCode())
        );
    }

    @Test
    @DisplayName("mixed-up Upgrade fields are caught: worldPosKey and fromLevel are positional")
    void upgradeFieldOrderMatters() {
        ConstructionIntent.Upgrade left = upgrade(1L, "burg:x", "100", 2);
        ConstructionIntent.Upgrade swapped = upgrade(1L, "burg:x", "2", 100);

        assertAll(
            () -> assertNotEquals(left, swapped,
                "swapping worldPosKey and fromLevel yields a different intent"),
            () -> assertNotEquals(upgrade(1L, "burg:x", "100", 2), upgrade(2L, "burg:x", "100", 2),
                "the entryId is part of equality")
        );
    }

    @Test
    @DisplayName("dequeue on a single-entry queue built by of() collapses to EMPTY")
    void dequeueSingleToEmpty() {
        ConstructionQueue single = ConstructionQueue.of(List.of(build(1L, "burg:x")));

        ConstructionQueue drained = single.dequeue();

        assertAll(
            () -> assertSame(ConstructionQueue.EMPTY, drained),
            () -> assertThrows(NoSuchElementException.class, drained::dequeue,
                "and a second dequeue on EMPTY throws")
        );
    }

    @Test
    @DisplayName("peek on EMPTY stays null and never throws — asymmetric with dequeue on purpose")
    void peekEmptyIsNull() {
        assertNull(ConstructionQueue.EMPTY.peek());
    }

    @Test
    @DisplayName("repeating the same enqueue 100 times from one source yields identical results")
    void repeatedEnqueueIsStable() {
        ConstructionQueue source = ConstructionQueue.EMPTY.enqueue(build(1L, "burg:x"));
        ConstructionQueue first = null;

        for (int i = 0; i < 100; i++) {
            ConstructionQueue result = source.enqueue(build(2L, "burg:y"));
            if (first == null) {
                first = result;
            } else {
                assertEquals(first.size(), result.size(), "iteration " + i);
                assertEquals(
                    first.entries().stream().map(ConstructionIntent::entryId).toList(),
                    result.entries().stream().map(ConstructionIntent::entryId).toList(),
                    "iteration " + i);
            }
        }

        assertEquals(1, source.size(),
            "the source is unchanged after 100 mutations of it");
    }

    @Test
    @DisplayName("an empty-string buildingDefId is accepted — validation of def ids is the caller's job")
    void emptyDefIdIsAccepted() {
        // Characterisation: the record guards null but not blank. A blank
        // defId queues fine and will fail later at resolution time. If the
        // domain ever tightens this, this test flips.
        ConstructionIntent blank = build(1L, "");

        assertEquals("", blank.buildingDefId());
    }
}
