package org.lowern1ght.burg.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mutation-style invariants for {@link QuestLog}: removals of unknown
 * defs are no-ops, additions replace by defId (the roll is keyed, not a
 * multi-map), completion stamps overwrite, and the of() factory is a
 * fixpoint for a log's own views. Kills mutants like a withAdded that
 * appends duplicates or a withRemoved that drops every match.
 */
class QuestLogMutationTest {

    private static QuestRef task(String defId, String status) {
        return QuestRef.of(defId, QuestRef.TYPE_TASK, status);
    }

    @Test
    @DisplayName("EMPTY log: no entries, no completions, unknown defs read zero / null")
    void emptyLogDefaults() {
        assertAll(
            () -> assertTrue(QuestLog.EMPTY.isEmpty()),
            () -> assertEquals(0, QuestLog.EMPTY.size()),
            () -> assertEquals(0L, QuestLog.EMPTY.lastCompletedFor("burg:unknown")),
            () -> assertNull(QuestLog.EMPTY.findById("burg:unknown")),
            () -> assertTrue(QuestLog.EMPTY.entries().isEmpty()),
            () -> assertTrue(QuestLog.EMPTY.lastCompleted().isEmpty())
        );
    }

    @Test
    @DisplayName("withAdded on EMPTY grows the roll; findById finds it")
    void withAddedGrows() {
        QuestRef ref = task("burg:fetch", QuestRef.STATUS_ACTIVE);
        QuestLog log = QuestLog.EMPTY.withAdded(ref);

        assertAll(
            () -> assertEquals(1, log.size()),
            () -> assertSame(ref, log.findById("burg:fetch"))
        );
    }

    @Test
    @DisplayName("withAdded with a known defId replaces in place — the roll stays keyed by defId")
    void withAddedReplacesByDefId() {
        QuestRef first = task("burg:fetch", QuestRef.STATUS_ACTIVE);
        QuestRef respawned = task("burg:fetch", QuestRef.STATUS_COMPLETED);
        QuestRef other = task("burg:haul", QuestRef.STATUS_ACTIVE);

        QuestLog log = QuestLog.EMPTY
            .withAdded(first)
            .withAdded(other)
            .withAdded(respawned);

        assertAll(
            () -> assertEquals(2, log.size(),
                "re-adding a defId must not append a duplicate (kills an append mutant)"),
            () -> assertSame(respawned, log.findById("burg:fetch"),
                "the re-spawned ref supersedes the old one"),
            () -> assertSame(other, log.findById("burg:haul")),
            () -> assertEquals(List.of(respawned, other), log.entries(),
                "replacement happens in place — the re-added ref keeps the old position")
        );
    }

    @Test
    @DisplayName("withRemoved of an unknown defId is a no-op — same instance")
    void withRemovedUnknownIsNoOp() {
        QuestLog log = QuestLog.EMPTY.withAdded(task("burg:fetch", QuestRef.STATUS_ACTIVE));

        assertSame(log, log.withRemoved("burg:unknown"),
            "removing a defId that is not on the roll returns the receiver");
        assertSame(QuestLog.EMPTY, QuestLog.EMPTY.withRemoved("burg:unknown"),
            "and on the EMPTY log the sentinel comes back");
    }

    @Test
    @DisplayName("withRemoved drops exactly the first matching ref")
    void withRemovedDropsFirstMatch() {
        // of() does not dedupe a hand-built list, so two refs can share a
        // defId on a reconstructed log. withRemoved must drop only the first.
        QuestRef dup1 = task("burg:fetch", QuestRef.STATUS_ACTIVE);
        QuestRef dup2 = task("burg:fetch", QuestRef.STATUS_COMPLETED);
        QuestRef other = task("burg:haul", QuestRef.STATUS_ACTIVE);

        QuestLog log = QuestLog.of(List.of(dup1, dup2, other), Map.of()).withRemoved("burg:fetch");

        assertEquals(List.of(dup2, other), log.entries(),
            "only the first match is removed (kills a drop-all mutant)");
    }

    @Test
    @DisplayName("removing the last entry with no completions collapses to EMPTY")
    void removeLastCollapsesToEmpty() {
        QuestLog drained = QuestLog.EMPTY
            .withAdded(task("burg:fetch", QuestRef.STATUS_ACTIVE))
            .withRemoved("burg:fetch");

        assertSame(QuestLog.EMPTY, drained,
            "a log with no refs and no completions is the sentinel");
    }

    @Test
    @DisplayName("removing the last entry keeps recorded completions — the log is not empty")
    void completionsKeepLogAlive() {
        QuestLog log = QuestLog.EMPTY
            .withAdded(task("burg:fetch", QuestRef.STATUS_ACTIVE))
            .withCompleted("burg:fetch", 100L)
            .withRemoved("burg:fetch");

        assertAll(
            () -> assertEquals(0, log.size()),
            () -> assertEquals(100L, log.lastCompletedFor("burg:fetch"),
                "the completion stamp outlives the ref"),
            () -> assertTrue(!log.isEmpty(),
                "isEmpty requires BOTH the roll and the completion map to be empty")
        );
    }

    @Test
    @DisplayName("withCompleted overwrites: stamping the same tick twice equals stamping once")
    void withCompletedIdempotentValueWise() {
        QuestLog once = QuestLog.EMPTY.withCompleted("burg:fetch", 50L);
        QuestLog twice = once.withCompleted("burg:fetch", 50L);

        assertAll(
            () -> assertEquals(50L, twice.lastCompletedFor("burg:fetch")),
            () -> assertEquals(once.lastCompleted().size(), twice.lastCompleted().size(),
                "re-stamping does not append a second map entry")
        );
    }

    @Test
    @DisplayName("withCompleted updates an existing stamp and preserves other defs")
    void withCompletedOverwritesAndPreserves() {
        QuestLog log = QuestLog.EMPTY
            .withCompleted("burg:fetch", 50L)
            .withCompleted("burg:haul", 70L)
            .withCompleted("burg:fetch", 120L);

        assertAll(
            () -> assertEquals(120L, log.lastCompletedFor("burg:fetch"),
                "the latest stamp wins"),
            () -> assertEquals(70L, log.lastCompletedFor("burg:haul"),
                "other defs are untouched")
        );
    }

    @Test
    @DisplayName("withCompleted rejects a negative tick and keeps zero as valid")
    void withCompletedRejectsNegativeTick() {
        assertAll(
            () -> assertThrows(IllegalArgumentException.class,
                () -> QuestLog.EMPTY.withCompleted("burg:fetch", -1L)),
            () -> assertEquals(0L, QuestLog.EMPTY.withCompleted("burg:fetch", 0L)
                .lastCompletedFor("burg:fetch"),
                "tick 0 is game-time origin, not negative")
        );
    }

    @Test
    @DisplayName("lastCompletedFor for an unstamped def reads zero — sparse, not absent")
    void lastCompletedUnknownReadsZero() {
        QuestLog log = QuestLog.EMPTY.withCompleted("burg:fetch", 50L);

        assertEquals(0L, log.lastCompletedFor("burg:other"));
    }

    @Test
    @DisplayName("of(null, null) is the EMPTY sentinel; so is of(empty, empty)")
    void ofNullsIsEmpty() {
        assertAll(
            () -> assertSame(QuestLog.EMPTY, QuestLog.of(null, null)),
            () -> assertSame(QuestLog.EMPTY, QuestLog.of(List.of(), Map.of()))
        );
    }

    @Test
    @DisplayName("of() drops negative and null ticks from the completion map")
    void ofDropsBadTicks() {
        Map<String, Long> dirty = new LinkedHashMap<>();
        dirty.put("burg:fetch", 50L);
        dirty.put("burg:minus", -10L);
        dirty.put("burg:nullish", null);

        QuestLog log = QuestLog.of(List.of(), dirty);

        assertAll(
            () -> assertEquals(1, log.lastCompleted().size()),
            () -> assertEquals(50L, log.lastCompletedFor("burg:fetch")),
            () -> assertEquals(0L, log.lastCompletedFor("burg:minus"),
                "a negative tick is dropped at construction"),
            () -> assertEquals(0L, log.lastCompletedFor("burg:nullish"),
                "a null tick is dropped at construction")
        );
    }

    @Test
    @DisplayName("of(entries(), lastCompleted()) is a fixpoint for any built log")
    void ofIsAFixpoint() {
        QuestRef fetch = task("burg:fetch", QuestRef.STATUS_ACTIVE);
        QuestRef note = QuestRef.ofUnstatused("burg:lore", QuestRef.TYPE_NOTE);
        QuestLog log = QuestLog.EMPTY
            .withAdded(fetch)
            .withAdded(note)
            .withCompleted("burg:old", 999L);

        QuestLog rebuilt = QuestLog.of(log.entries(), log.lastCompleted());

        assertAll(
            () -> assertEquals(log.size(), rebuilt.size()),
            () -> assertEquals(log.entries(), rebuilt.entries()),
            () -> assertEquals(log.lastCompleted(), rebuilt.lastCompleted()),
            () -> assertNotNull(rebuilt.findById("burg:fetch")),
            () -> assertTrue(rebuilt.findById("burg:lore").isNote(),
                "the NOTE type survives the rebuild")
        );
    }

    @Test
    @DisplayName("entries() and lastCompleted() are unmodifiable views")
    void viewsAreUnmodifiable() {
        QuestLog log = QuestLog.EMPTY
            .withAdded(task("burg:fetch", QuestRef.STATUS_ACTIVE))
            .withCompleted("burg:fetch", 10L);

        assertAll(
            () -> assertThrows(UnsupportedOperationException.class,
                () -> log.entries().add(task("burg:x", QuestRef.STATUS_ACTIVE))),
            () -> assertThrows(UnsupportedOperationException.class,
                () -> log.lastCompleted().put("burg:x", 1L))
        );
    }

    @Test
    @DisplayName("withRemoved preserves the completion map for unrelated defs")
    void withRemovedPreservesCompletions() {
        QuestLog log = QuestLog.EMPTY
            .withAdded(task("burg:fetch", QuestRef.STATUS_ACTIVE))
            .withCompleted("burg:old_task", 300L)
            .withRemoved("burg:fetch");

        assertEquals(300L, log.lastCompletedFor("burg:old_task"),
            "removing a ref does not touch completion stamps");
    }
}
