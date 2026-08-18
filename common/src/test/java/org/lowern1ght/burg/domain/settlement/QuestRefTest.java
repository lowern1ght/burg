package org.lowern1ght.burg.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The quest reference record, in pure JUnit. {@link QuestRef} is the
 * Minecraft-free projection of {@code town.Quest} — the {@code QuestLog}
 * tests already exercise the record at the boundary (constructor null
 * guards, {@code hasStatus}/{@code isTask}/{@code isNote}). This file
 * pins the standalone contract: the two factory methods, equality on the
 * three components, and the type/status sentinels.
 */
class QuestRefTest {

    @Test
    @DisplayName("the canonical sentinels are exactly TASK / NOTE and ACTIVE / COMPLETED")
    void sentinels() {
        assertAll(
            () -> assertEquals("TASK", QuestRef.TYPE_TASK),
            () -> assertEquals("NOTE", QuestRef.TYPE_NOTE),
            () -> assertEquals("ACTIVE", QuestRef.STATUS_ACTIVE),
            () -> assertEquals("COMPLETED", QuestRef.STATUS_COMPLETED)
        );
    }

    @Test
    @DisplayName("of() builds a TASK ref carrying a status")
    void ofBuildsTask() {
        QuestRef ref = QuestRef.of("gather_wood", QuestRef.TYPE_TASK, QuestRef.STATUS_ACTIVE);

        assertAll(
            () -> assertEquals("gather_wood", ref.defId()),
            () -> assertEquals(QuestRef.TYPE_TASK, ref.type()),
            () -> assertEquals(QuestRef.STATUS_ACTIVE, ref.status()),
            () -> assertTrue(ref.hasStatus()),
            () -> assertTrue(ref.isTask()),
            () -> assertFalse(ref.isNote())
        );
    }

    @Test
    @DisplayName("ofUnstatused() builds a NOTE-shaped ref with a null status")
    void ofUnstatusedBuildsNote() {
        QuestRef ref = QuestRef.ofUnstatused("lore_intro", QuestRef.TYPE_NOTE);

        assertAll(
            () -> assertEquals("lore_intro", ref.defId()),
            () -> assertEquals(QuestRef.TYPE_NOTE, ref.type()),
            () -> assertNull(ref.status(),
                "a NOTE-shaped ref carries no status"),
            () -> assertFalse(ref.hasStatus()),
            () -> assertTrue(ref.isNote()),
            () -> assertFalse(ref.isTask())
        );
    }

    @Test
    @DisplayName("of() can also build a NOTE-shaped ref by passing an explicit null status")
    void ofWithExplicitNullStatus() {
        QuestRef ref = QuestRef.of("lore_intro", QuestRef.TYPE_NOTE, null);

        assertAll(
            () -> assertNull(ref.status()),
            () -> assertFalse(ref.hasStatus(),
                "an explicit null status reads as 'no status'"),
            () -> assertTrue(ref.isNote())
        );
    }

    @Test
    @DisplayName("the constructor rejects null defId or null type")
    void constructorRejectsNulls() {
        assertAll(
            () -> assertThrows(NullPointerException.class,
                () -> new QuestRef(null, QuestRef.TYPE_TASK, QuestRef.STATUS_ACTIVE),
                "defId is required"),
            () -> assertThrows(NullPointerException.class,
                () -> new QuestRef("gather_wood", null, QuestRef.STATUS_ACTIVE),
                "type is required")
        );
    }

    @Test
    @DisplayName("two refs with the same three components are equal; a status or type flip is not")
    void equality() {
        QuestRef a = QuestRef.of("gather_wood", QuestRef.TYPE_TASK, QuestRef.STATUS_ACTIVE);
        QuestRef b = QuestRef.of("gather_wood", QuestRef.TYPE_TASK, QuestRef.STATUS_ACTIVE);

        assertAll(
            () -> assertEquals(a, b, "same defId / type / status are equal"),
            () -> assertNotEquals(a,
                QuestRef.of("gather_wood", QuestRef.TYPE_TASK, QuestRef.STATUS_COMPLETED),
                "different status — different ref"),
            () -> assertNotEquals(a,
                QuestRef.of("gather_wood", QuestRef.TYPE_NOTE, QuestRef.STATUS_ACTIVE),
                "different type — different ref"),
            () -> assertNotEquals(a,
                QuestRef.of("mine_stone", QuestRef.TYPE_TASK, QuestRef.STATUS_ACTIVE),
                "different defId — different ref")
        );
    }
}
