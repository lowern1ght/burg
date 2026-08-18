package org.lowern1ght.burg.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The quest log, in pure JUnit. Like {@code StandingBookTest} and
 * {@code StockLedgerTest}, the log is immutable — every mutator returns a
 * new log — and the default-empty path is the additive NBT default for
 * old saves.
 *
 * <p>Five correctness traps the unit tests are explicitly here to catch:
 * (1) the {@code EMPTY} sentinel stays referentially stable so equality
 * checks elsewhere are cheap; (2) a {@code withRemoved} for a defId that
 * is not on the log is a no-op (preserves the same instance, the same
 * shape as {@code StockLedger.merge} of an empty ledger); (3)
 * {@code withAdded} on a defId that already exists replaces the entry
 * rather than duplicating it (the engine treats defId as the primary key
 * — re-spawning a quest supersedes the old one); (4) negative ticks on
 * {@code withCompleted} are rejected with a deterministic failure rather
 * than silently degrading; and (5) the {@code lastCompleted} map is
 * sparse — a defId never completed reads as zero, never as "absent".
 */
class QuestLogTest {

    private static QuestRef note(String defId) {
        return QuestRef.ofUnstatused(defId, QuestRef.TYPE_NOTE);
    }

    private static QuestRef activeTask(String defId) {
        return QuestRef.of(defId, QuestRef.TYPE_TASK, QuestRef.STATUS_ACTIVE);
    }

    private static QuestRef completedTask(String defId) {
        return QuestRef.of(defId, QuestRef.TYPE_TASK, QuestRef.STATUS_COMPLETED);
    }

    @Test
    @DisplayName("the additive default for old saves is an empty log")
    void emptyIsTheDefault() {
        QuestLog log = QuestLog.EMPTY;
        assertAll(
            () -> assertSame(QuestLog.EMPTY, log, "EMPTY is referentially stable"),
            () -> assertTrue(log.isEmpty()),
            () -> assertEquals(0, log.size()),
            () -> assertEquals(0L, log.lastCompletedFor("anything"),
                "a defId never completed reads as zero"),
            () -> assertNull(log.findById("anything"),
                "a defId not on the roll has no ref"),
            () -> assertEquals(List.of(), log.entries()),
            () -> assertEquals(Map.of(), log.lastCompleted())
        );
    }

    @Test
    @DisplayName("of() builds a log from two sources and drops malformed entries")
    void ofFromTwoSources() {
        List<QuestRef> entries = List.of(activeTask("gather_wood"), note("lore_intro"));
        Map<String, Long> completed = new LinkedHashMap<>();
        completed.put("gather_wood", 1234L);
        completed.put("mine_stone", 5678L);
        QuestLog log = QuestLog.of(entries, completed);

        assertAll(
            () -> assertEquals(2, log.size()),
            () -> assertEquals(1234L, log.lastCompletedFor("gather_wood")),
            () -> assertEquals(5678L, log.lastCompletedFor("mine_stone")),
            () -> assertEquals(0L, log.lastCompletedFor("never_completed"),
                "a defId not in the completion map reads as zero"),
            () -> assertEquals("gather_wood", log.findById("gather_wood").defId()),
            () -> assertEquals("lore_intro", log.findById("lore_intro").defId())
        );
    }

    @Test
    @DisplayName("of() with a null or empty source returns the EMPTY sentinel")
    void ofNullOrEmpty() {
        assertAll(
            () -> assertSame(QuestLog.EMPTY, QuestLog.of(null, null),
                "both sources null ⇒ EMPTY"),
            () -> assertSame(QuestLog.EMPTY, QuestLog.of(List.of(), null),
                "empty entries + null completed ⇒ EMPTY"),
            () -> assertSame(QuestLog.EMPTY, QuestLog.of(null, Map.of()),
                "null entries + empty completed ⇒ EMPTY")
        );
    }

    @Test
    @DisplayName("of() drops negative and null completion ticks defensively")
    void ofDropsNegativeTicks() {
        Map<String, Long> completed = new LinkedHashMap<>();
        completed.put("good", 100L);
        completed.put("bad_negative", -1L);
        completed.put("bad_null", null);
        QuestLog log = QuestLog.of(List.of(), completed);

        assertAll(
            () -> assertEquals(100L, log.lastCompletedFor("good"),
                "the valid tick survives"),
            () -> assertEquals(0L, log.lastCompletedFor("bad_negative"),
                "negative ticks are dropped at construction time"),
            () -> assertEquals(0L, log.lastCompletedFor("bad_null"),
                "null ticks are dropped at construction time"),
            () -> assertTrue(log.lastCompleted().containsKey("good"),
                "the surviving entry is still on the roll"),
            () -> assertFalse(log.lastCompleted().containsKey("bad_negative"),
                "the dropped entry is not on the roll"),
            () -> assertFalse(log.lastCompleted().containsKey("bad_null"),
                "the dropped entry is not on the roll")
        );
    }

    @Test
    @DisplayName("withAdded appends a ref and grows the roll")
    void withAddedAppends() {
        QuestLog log = QuestLog.EMPTY
            .withAdded(activeTask("gather_wood"))
            .withAdded(note("lore_intro"));

        assertAll(
            () -> assertEquals(2, log.size()),
            () -> assertEquals("gather_wood", log.entries().get(0).defId()),
            () -> assertEquals("lore_intro", log.entries().get(1).defId()),
            () -> assertTrue(QuestLog.EMPTY.isEmpty(),
                "the original EMPTY log is unchanged (immutability)")
        );
    }

    @Test
    @DisplayName("withAdded replaces a ref when the defId already exists")
    void withAddedReplaces() {
        QuestLog original = QuestLog.EMPTY
            .withAdded(activeTask("gather_wood"))
            .withAdded(note("gather_wood")); // type flipped from TASK to NOTE
        assertEquals(1, original.size(),
            "a ref added with a matching defId replaces the existing one");

        QuestRef head = original.findById("gather_wood");
        assertEquals(QuestRef.TYPE_NOTE, head.type(),
            "the surviving ref is the latest write");
    }

    @Test
    @DisplayName("withRemoved drops the ref whose defId matches and preserves the rest")
    void withRemoved() {
        QuestLog log = QuestLog.EMPTY
            .withAdded(activeTask("gather_wood"))
            .withAdded(note("lore_intro"))
            .withAdded(activeTask("mine_stone"));

        QuestLog after = log.withRemoved("lore_intro");

        assertAll(
            () -> assertEquals(3, log.size(),
                "the original log is unchanged (immutability)"),
            () -> assertEquals(2, after.size()),
            () -> assertNull(after.findById("lore_intro"),
                "the removed ref is gone"),
            () -> assertEquals("gather_wood", after.findById("gather_wood").defId(),
                "the surviving refs are untouched"),
            () -> assertEquals("mine_stone", after.findById("mine_stone").defId())
        );
    }

    @Test
    @DisplayName("withRemoved for a defId that is not on the log is a no-op")
    void withRemovedMissingIsNoOp() {
        QuestLog log = QuestLog.EMPTY.withAdded(activeTask("gather_wood"));
        QuestLog after = log.withRemoved("never_existed");

        assertSame(log, after,
            "withRemoved of a missing defId returns the same instance");
    }

    @Test
    @DisplayName("withRemoved that empties the roll collapses to EMPTY")
    void withRemovedLastRefCollapses() {
        QuestLog log = QuestLog.EMPTY
            .withAdded(activeTask("gather_wood"))
            .withCompleted("gather_wood", 42L);

        QuestLog after = log.withRemoved("gather_wood");

        // The completion map survives the removal — only the roll collapses.
        // EMPTY is reachability-defined, not "both fields empty".
        assertAll(
            () -> assertEquals(0, after.size(),
                "the ref is gone"),
            () -> assertEquals(42L, after.lastCompletedFor("gather_wood"),
                "the completion tick survives — the log still has data"),
            () -> assertFalse(after.isEmpty(),
                "the log is not EMPTY because the completion map is non-empty"),
            () -> assertSame(QuestLog.EMPTY, QuestLog.EMPTY.withRemoved("anything"),
                "EMPTY.withRemoved is still EMPTY")
        );
    }

    @Test
    @DisplayName("withCompleted stamps a tick and merges with existing entries")
    void withCompleted() {
        QuestLog log = QuestLog.EMPTY
            .withCompleted("gather_wood", 100L)
            .withCompleted("mine_stone", 200L)
            .withCompleted("gather_wood", 300L);

        assertAll(
            () -> assertEquals(300L, log.lastCompletedFor("gather_wood"),
                "the latest tick wins"),
            () -> assertEquals(200L, log.lastCompletedFor("mine_stone"),
                "untouched defs are preserved"),
            () -> assertEquals(2, log.lastCompleted().size())
        );
    }

    @Test
    @DisplayName("withCompleted rejects negative ticks")
    void withCompletedRejectsNegative() {
        assertAll(
            () -> assertThrows(IllegalArgumentException.class,
                () -> QuestLog.EMPTY.withCompleted("gather_wood", -1L),
                "negative ticks are game-time and rejected up front"),
            () -> assertThrows(IllegalArgumentException.class,
                () -> QuestLog.EMPTY.withCompleted("gather_wood", Long.MIN_VALUE),
                "the boundary case is also rejected")
        );
    }

    @Test
    @DisplayName("withCompleted on EMPTY yields a log whose lastCompleted map has the new entry")
    void withCompletedFromEmpty() {
        QuestLog log = QuestLog.EMPTY.withCompleted("gather_wood", 42L);

        assertAll(
            () -> assertEquals(0, log.size(),
                "no refs on the roll — withCompleted only touches the completion map"),
            () -> assertEquals(42L, log.lastCompletedFor("gather_wood")),
            () -> assertEquals(1, log.lastCompleted().size()),
            () -> assertFalse(log.isEmpty(),
                "the log is not EMPTY because the completion map is non-empty")
        );
    }

    @Test
    @DisplayName("QuestRef.status is null for NOTE and isTask / isNote distinguish the two kinds")
    void questRefShape() {
        QuestRef note = note("lore_intro");
        QuestRef task = activeTask("gather_wood");

        assertAll(
            () -> assertNull(note.status(),
                "a NOTE has no status"),
            () -> assertFalse(note.hasStatus(),
                "hasStatus mirrors the null/non-null check"),
            () -> assertTrue(note.isNote()),
            () -> assertFalse(note.isTask()),
            () -> assertTrue(task.isTask()),
            () -> assertFalse(task.isNote()),
            () -> assertTrue(task.hasStatus(),
                "an active task carries the ACTIVE status"),
            () -> assertEquals(QuestRef.STATUS_ACTIVE, task.status())
        );
    }

    @Test
    @DisplayName("each mutator returns a new log — the input is not mutated")
    void immutability() {
        QuestLog before = QuestLog.EMPTY
            .withAdded(activeTask("gather_wood"))
            .withCompleted("gather_wood", 100L);

        QuestLog afterAdd = before.withAdded(note("lore_intro"));
        QuestLog afterRemove = before.withRemoved("gather_wood");
        QuestLog afterComplete = before.withCompleted("gather_wood", 200L);

        assertAll(
            () -> assertEquals(1, before.size(),
                "the original log is unchanged across all mutations"),
            () -> assertEquals(1, before.lastCompleted().size()),
            () -> assertEquals(100L, before.lastCompletedFor("gather_wood")),
            () -> assertEquals(2, afterAdd.size()),
            () -> assertEquals(0, afterRemove.size(),
                "removal drops the ref but the completion map survives"),
            () -> assertEquals(200L, afterComplete.lastCompletedFor("gather_wood")),
            () -> assertNotSame(before, afterAdd, "withAdded returns a new instance"),
            () -> assertNotSame(before, afterRemove, "withRemoved returns a new instance"),
            () -> assertNotSame(before, afterComplete, "withCompleted returns a new instance")
        );
    }

    @Test
    @DisplayName("QuestRef rejects null defId or type at the constructor")
    void questRefConstructorGuards() {
        assertAll(
            () -> assertThrows(NullPointerException.class,
                () -> new QuestRef(null, "TASK", null),
                "defId is required"),
            () -> assertThrows(NullPointerException.class,
                () -> new QuestRef("gather_wood", null, null),
                "type is required")
        );
    }

    @Test
    @DisplayName("QuestLog rejects null defId / ref on mutators")
    void mutatorGuards() {
        assertAll(
            () -> assertThrows(NullPointerException.class,
                () -> QuestLog.EMPTY.withAdded(null),
                "withAdded rejects null ref"),
            () -> assertThrows(NullPointerException.class,
                () -> QuestLog.EMPTY.withRemoved(null),
                "withRemoved rejects null defId"),
            () -> assertThrows(NullPointerException.class,
                () -> QuestLog.EMPTY.withCompleted(null, 0L),
                "withCompleted rejects null defId"),
            () -> assertThrows(NullPointerException.class,
                () -> QuestLog.EMPTY.lastCompletedFor(null),
                "lastCompletedFor rejects null defId"),
            () -> assertThrows(NullPointerException.class,
                () -> QuestLog.EMPTY.findById(null),
                "findById rejects null defId")
        );
    }

    @Test
    @DisplayName("of() rejects null map keys defensively")
    void ofRejectsNullKeys() {
        Map<String, Long> completed = new HashMap<>();
        // Build via a LinkedHashMap so we can put a null key (HashMap allows
        // one null key; LinkedHashMap does the same).
        completed.put(null, 100L);
        assertThrows(NullPointerException.class,
            () -> QuestLog.of(List.of(), completed),
            "a null key in the source map is rejected at construction time");
    }
}
