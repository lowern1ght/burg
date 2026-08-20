package org.lowern1ght.burg.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mutation-style invariants for the {@link ConstructionQueue#without(int)}
 * operation added by ADR-0027 (construction queue promoted to the SoT).
 * Kills mutants like a {@code without} that drops the wrong index, a
 * non-collapse on drain-to-zero, or a missing out-of-range no-op.
 *
 * <p>Companion to {@link ConstructionQueueMutationTest}, which covers
 * the pre-existing {@code enqueue} / {@code dequeue} / {@code of} API.
 * The two test files together pin the full surface a {@code Town}
 * mutation needs: enqueue for the player add / upgrade paths, dequeue
 * for the head-removal path, and {@code without} for the indexed
 * removal that {@code removeFromConstructionQueue} and
 * {@code consumeQueueEntry} both rely on after the SoT flip.
 */
class ConstructionQueueWithoutTest {

    private static ConstructionIntent.NewBuild build(long entryId) {
        return new ConstructionIntent.NewBuild(entryId, "burg:house_" + entryId);
    }

    private static ConstructionIntent.Upgrade upgrade(long entryId, String defId, long worldPos) {
        return new ConstructionIntent.Upgrade(entryId, defId, Long.toString(worldPos), 0);
    }

    @Test
    @DisplayName("without on EMPTY: out-of-range is a no-op (returns the receiver)")
    void withoutOnEmptyIsNoOp() {
        assertAll(
            () -> assertSame(ConstructionQueue.EMPTY, ConstructionQueue.EMPTY.without(0),
                "without(0) on EMPTY is the EMPTY sentinel"),
            () -> assertSame(ConstructionQueue.EMPTY, ConstructionQueue.EMPTY.without(-1),
                "negative index is a no-op"),
            () -> assertSame(ConstructionQueue.EMPTY, ConstructionQueue.EMPTY.without(99),
                "out-of-range positive index is a no-op")
        );
    }

    @Test
    @DisplayName("without removes the entry at index and keeps order of the survivors")
    void withoutRemovesAtIndex() {
        ConstructionIntent.NewBuild a = build(1);
        ConstructionIntent.NewBuild b = build(2);
        ConstructionIntent.NewBuild c = build(3);
        ConstructionIntent.NewBuild d = build(4);

        ConstructionQueue four = ConstructionQueue.EMPTY
            .enqueue(a).enqueue(b).enqueue(c).enqueue(d);

        assertEquals(List.of(a, c, d), four.without(1).entries(),
            "removing index 1 keeps the head (a), drops b, and the rest slide up");
    }

    @Test
    @DisplayName("without at the head drops the oldest entry; the new head is the next one")
    void withoutAtHead() {
        ConstructionIntent.NewBuild a = build(1);
        ConstructionIntent.NewBuild b = build(2);
        ConstructionIntent.NewBuild c = build(3);

        ConstructionQueue three = ConstructionQueue.EMPTY
            .enqueue(a).enqueue(b).enqueue(c);

        ConstructionQueue afterHeadDrop = three.without(0);

        assertAll(
            () -> assertSame(b, afterHeadDrop.peek(),
                "the new head is b — a was the dropped head"),
            () -> assertEquals(List.of(b, c), afterHeadDrop.entries())
        );
    }

    @Test
    @DisplayName("without at the tail keeps the head and drops the most recently added entry")
    void withoutAtTail() {
        ConstructionIntent.NewBuild a = build(1);
        ConstructionIntent.NewBuild b = build(2);
        ConstructionIntent.NewBuild c = build(3);

        ConstructionQueue three = ConstructionQueue.EMPTY
            .enqueue(a).enqueue(b).enqueue(c);

        ConstructionQueue afterTailDrop = three.without(2);

        assertEquals(List.of(a, b), afterTailDrop.entries(),
            "removing the tail preserves the head and middle");
    }

    @Test
    @DisplayName("draining to zero collapses to the EMPTY sentinel (referential stability)")
    void drainToEmptyCollapses() {
        ConstructionQueue one = ConstructionQueue.EMPTY.enqueue(build(1));
        ConstructionQueue drained = one.without(0);

        assertSame(ConstructionQueue.EMPTY, drained,
            "a single-entry drain collapses to EMPTY, mirroring dequeue's behavior");
    }

    @Test
    @DisplayName("without never mutates the receiver — the original queue keeps its entries")
    void withoutIsImmutable() {
        ConstructionIntent.NewBuild a = build(1);
        ConstructionIntent.NewBuild b = build(2);
        ConstructionQueue two = ConstructionQueue.EMPTY.enqueue(a).enqueue(b);

        ConstructionQueue after = two.without(0);

        assertAll(
            () -> assertEquals(2, two.size(), "the receiver keeps its size after without"),
            () -> assertEquals(1, after.size(), "the new queue dropped the head, kept b"),
            () -> assertNotSame(two, after, "without returns a fresh queue, not the receiver"),
            () -> assertSame(b, after.peek(),
                "the head of the new queue is b — the entry that was at index 1")
        );
    }

    @Test
    @DisplayName("without preserves the mixed NewBuild + Upgrade shape")
    void withoutPreservesMixedShapes() {
        ConstructionIntent.NewBuild fresh = new ConstructionIntent.NewBuild(1, "burg:sawmill");
        ConstructionIntent.Upgrade upgrade = upgrade(2, "burg:house", 42L);
        ConstructionIntent.NewBuild tail = build(3);

        ConstructionQueue three = ConstructionQueue.EMPTY
            .enqueue(fresh).enqueue(upgrade).enqueue(tail);

        ConstructionQueue afterMidDrop = three.without(1);

        assertAll(
            () -> assertEquals(2, afterMidDrop.size()),
            () -> assertEquals(List.of(fresh, tail), afterMidDrop.entries(),
                "the upgrade is gone, NewBuild entries survive on either side"),
            () -> assertTrue(afterMidDrop.entries().stream()
                .noneMatch(intent -> intent instanceof ConstructionIntent.Upgrade),
                "no Upgrade intent survives the without")
        );
    }
}
